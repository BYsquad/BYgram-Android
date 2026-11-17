package org.telegram.messenger.plugins;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.telegram.messenger.BuildConfig;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class PluginDevServer {
    private static final int PORT = 42690;
    private static final int MAX_COMMAND = ((PluginPackage.MAX_ZIP + 2) / 3) * 4 + 4096;
    private static PluginDevServer current;
    private final ServerSocket server;
    private final byte[] token;
    private volatile Socket active;

    private PluginDevServer(String token) throws IOException {
        this.token = token.getBytes(StandardCharsets.US_ASCII);
        server = new ServerSocket();
        try {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT));
        } catch (IOException error) {
            server.close();
            throw error;
        }
    }

    static synchronized void start(String token) throws IOException {
        if (!BuildConfig.DEBUG_VERSION) throw new IOException("DeveloperModeUnavailable");
        stop();
        PluginDevServer instance = new PluginDevServer(token);
        current = instance;
        Thread thread = new Thread(instance::listen, "BYgramDevServer");
        thread.setDaemon(true);
        thread.start();
    }

    static synchronized void stop() {
        if (current == null) return;
        try { current.server.close(); } catch (IOException ignored) { }
        Socket connection = current.active;
        if (connection != null) try { connection.close(); } catch (IOException ignored) { }
        current = null;
    }

    private void listen() {
        while (!server.isClosed()) {
            try (Socket connection = server.accept()) {
                active = connection;
                if (server.isClosed()) return;
                connection.setSoTimeout(15000);
                Terminal terminal = new Terminal(connection);
                while (!server.isClosed() && execute(terminal, connection)) { }
            } catch (IOException ignored) {
            } finally {
                active = null;
            }
        }
    }

    private boolean execute(Terminal terminal, Socket connection) throws IOException {
        JsonObject response = new JsonObject();
        boolean debugging = false;
        boolean elyx = false;
        try {
            String line = terminal.readObject(MAX_COMMAND);
            if (line == null) return false;
            JsonObject request = PluginSettingsFile.parseJson(line.getBytes(StandardCharsets.UTF_8), MAX_COMMAND, 16, MAX_COMMAND);
            if (request.has("#") && request.get("#").isJsonPrimitive()) response.add("#", request.get("#"));
            String supplied = request.has("token") ? request.get("token").getAsString() : "";
            if (supplied.length() != 64 || !MessageDigest.isEqual(token, supplied.getBytes(StandardCharsets.US_ASCII))) {
                throw new IOException("DeveloperAuthenticationFailed");
            }
            String command = request.get("@").getAsString();
            elyx = command.startsWith("elyx_");
            PluginManager manager = PluginManager.get();
            String id = request.has("plugin_id") ? request.get("plugin_id").getAsString() : null;
            JsonObject result;
            switch (command) {
                case "ping":
                case "elyx_ping":
                    response.addProperty("pong", true);
                    break;
                case "art_targets":
                    int host = PluginManager.hostBuild();
                    int base = BuildConfig.BYGRAM_BASE_BUILD;
                    response.add("bygram", buildRange(host));
                    response.add("telegram", buildRange(base));
                    response.addProperty("backend_candidate", HookBridge.available());
                    response.add("hook_targets", JsonParser.parseString(HookBridge.describeTargets(
                            request.get("class").getAsString(), request.get("method").getAsString())));
                    break;
                case "write_plugin":
                    if (request.has("package")) {
                        byte[] contents = android.util.Base64.decode(request.get("package").getAsString(), android.util.Base64.DEFAULT);
                        if (contents.length > PluginPackage.MAX_ZIP) throw new IOException("PackageLimitExceeded");
                        result = manager.deployDeveloper(contents).get(60, TimeUnit.SECONDS);
                    } else {
                        byte[] source = request.get("content").getAsString().getBytes(StandardCharsets.UTF_8);
                        if (source.length > 2 * 1024 * 1024) throw new IOException("PackageLimitExceeded");
                        result = manager.deployDeveloperSource(id, source, false).get(60, TimeUnit.SECONDS);
                    }
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> value : result.entrySet()) response.add(value.getKey(), value.getValue());
                    break;
                case "elyx_write":
                    byte[] archive = android.util.Base64.decode(request.get("content").getAsString(), android.util.Base64.DEFAULT);
                    if (archive.length > PluginPackage.MAX_ZIP) throw new IOException("PackageLimitExceeded");
                    result = manager.deployDeveloperSource(id, archive, true).get(60, TimeUnit.SECONDS);
                    result.entrySet().forEach(value -> response.add(value.getKey(), value.getValue()));
                    break;
                case "elyx_compare_folder":
                case "elyx_changes":
                    result = manager.elyxDeveloper(command, decodePayload(request)).get(60, TimeUnit.SECONDS);
                    result.entrySet().forEach(value -> response.add(value.getKey(), value.getValue()));
                    break;
                case "patch_plugin":
                    result = manager.patchDeveloper(request.get("plugin_id").getAsString(), request.get("base_hash").getAsString(),
                            request.getAsJsonObject("changes"), request.getAsJsonArray("removed")).get(60, TimeUnit.SECONDS);
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> value : result.entrySet()) response.add(value.getKey(), value.getValue());
                    break;
                case "start_debugger":
                    if (request.has("platform")) {
                        String platform = request.get("platform").getAsString();
                        int remotePort = request.get("port").getAsInt();
                        if (!"127.0.0.1".equals(request.get("host").getAsString())
                                || remotePort < 1024 || remotePort > 65535 || remotePort == PORT)
                            throw new IOException("InvalidIdeDebugRequest");
                        manager.ideSession(platform, remotePort, true);
                        response.addProperty("success", true);
                        break;
                    }
                    terminal.finishLine();
                    String method = request.get("method").getAsString();
                    if (!method.matches("[A-Za-z][A-Za-z0-9_]{0,119}")) throw new IOException("InvalidDebugMethod");
                    connection.setSoTimeout(120000);
                    response.addProperty("debugger", "pdb");
                    terminal.write(response + "\n");
                    debugging = true;
                    manager.debugDeveloper(id, method, terminal, "pdb", 0);
                    break;
                case "stop_debugger":
                    manager.ideSession(request.get("platform").getAsString(), 0, false);
                    response.addProperty("success", true);
                    break;
                case "start_ide_debugger":
                    String adapter = request.get("adapter").getAsString();
                    int port = request.get("port").getAsInt();
                    String target = request.get("method").getAsString();
                    if (!("debugpy".equals(adapter) || "pycharm".equals(adapter))
                            || port < 1024 || port > 65535 || port == PORT
                            || !target.matches("[A-Za-z][A-Za-z0-9_]{0,119}"))
                        throw new IOException("InvalidIdeDebugRequest");
                    manager.debugDeveloper(id, target, terminal, adapter, port);
                    response.addProperty("debugger", adapter);
                    break;
                case "get_plugins":
                case "get_elyx_plugins":
                case "enable_plugin":
                case "disable_plugin":
                case "reload_plugin":
                case "remove_plugin":
                    result = manager.developerCommand(command, id).get(30, TimeUnit.SECONDS);
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> value : result.entrySet()) response.add(value.getKey(), value.getValue());
                    break;
                default: throw new IOException("UnknownDeveloperCommand");
            }
            response.addProperty("ok", true);
        } catch (Exception failure) {
            Throwable cause = failure instanceof ExecutionException && failure.getCause() != null ? failure.getCause() : failure;
            response.addProperty("ok", false);
            String message = cause.getMessage();
            response.addProperty("error", message == null ? cause.getClass().getSimpleName() : message);
            if (elyx) {
                response.addProperty("success", false);
                com.google.gson.JsonArray errors = new com.google.gson.JsonArray();
                errors.add(response.get("error"));
                response.add("errors", errors);
            }
        }
        terminal.write(debugging ? "\n" + response + "\n" : response + "\n");
        return !debugging;
    }
    private static JsonObject buildRange(int build) {
        JsonObject range = new JsonObject();
        range.addProperty("min_build", build);
        range.addProperty("max_build", build);
        return range;
    }

    private static JsonObject decodePayload(JsonObject request) throws IOException {
        byte[] compressed = android.util.Base64.decode(request.get("data").getAsString(), android.util.Base64.DEFAULT);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (InputStream stream = new java.util.zip.InflaterInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[8192];
            for (int count; (count = stream.read(buffer)) != -1; ) {
                if (data.size() + count > MAX_COMMAND) throw new IOException("DeveloperMessageLimitExceeded");
                data.write(buffer, 0, count);
            }
        }
            return PluginSettingsFile.parseJson(data.toByteArray(), MAX_COMMAND, 16, MAX_COMMAND);
    }

    public static final class Terminal {
        private final BufferedReader input;
        private final Writer output;

        Terminal(Socket connection) throws IOException {
            input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
        }

        private String read(int limit) throws IOException {
            StringBuilder line = new StringBuilder();
            for (int value; (value = input.read()) != -1;) {
                if (value == '\n') return line.toString();
                if (line.length() == limit) throw new IOException("DeveloperMessageLimitExceeded");
                line.append((char) value);
            }
            return line.length() == 0 ? null : line.toString();
        }
        private String readObject(int limit) throws IOException {
            int first;
            do { first = input.read(); } while (first != -1 && Character.isWhitespace(first));
            if (first == -1) return null;
            if (first != '{') throw new IOException("InvalidDeveloperMessage");
            StringBuilder json = new StringBuilder().append('{');
            int depth = 1;
            boolean quoted = false, escaped = false;
            while (depth != 0) {
                int next = input.read();
                if (next == -1) throw new EOFException("IncompleteDeveloperMessage");
                if (json.length() == limit) throw new IOException("DeveloperMessageLimitExceeded");
                char value = (char) next;
                json.append(value);
                if (quoted) {
                    if (escaped) escaped = false;
                    else if (value == '\\') escaped = true;
                    else if (value == '"') quoted = false;
                } else if (value == '"') quoted = true;
                else if (value == '{' && ++depth > 16) throw new IOException("InvalidDeveloperMessage");
                else if (value == '}') depth--;
            }
            return json.toString();
        }
        private void finishLine() throws IOException {
            input.mark(1);
            if (input.read() != '\n') input.reset();
        }
        public String readLine() throws IOException { return read(8192); }
        public void write(String data) throws IOException { output.write(data); output.flush(); }
    }
}
