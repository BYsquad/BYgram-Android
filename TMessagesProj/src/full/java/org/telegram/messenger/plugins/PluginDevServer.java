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
        if (!BuildConfig.DEBUG_VERSION || !BuildConfig.BYGRAM_FULL) throw new IOException("DeveloperModeUnavailable");
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
                execute(new Terminal(connection), connection);
            } catch (IOException ignored) {
            } finally {
                active = null;
            }
        }
    }

    private void execute(Terminal terminal, Socket connection) throws IOException {
        JsonObject response = new JsonObject();
        boolean debugging = false;
        try {
            String line = terminal.read(MAX_COMMAND);
            if (line == null) return;
            JsonObject request = JsonParser.parseString(line).getAsJsonObject();
            String supplied = request.has("token") ? request.get("token").getAsString() : "";
            if (supplied.length() != 64 || !MessageDigest.isEqual(token, supplied.getBytes(StandardCharsets.US_ASCII))) {
                throw new IOException("DeveloperAuthenticationFailed");
            }
            if (request.has("#") && request.get("#").isJsonPrimitive()) response.add("#", request.get("#"));
            String command = request.get("@").getAsString();
            PluginManager manager = PluginManager.get();
            String id = request.has("plugin_id") ? request.get("plugin_id").getAsString() : null;
            JsonObject result;
            switch (command) {
                case "ping":
                    response.addProperty("pong", true);
                    break;
                case "write_plugin":
                    byte[] contents = android.util.Base64.decode(request.get("package").getAsString(), android.util.Base64.DEFAULT);
                    if (contents.length > PluginPackage.MAX_ZIP) throw new IOException("PackageLimitExceeded");
                    result = manager.deployDeveloper(contents).get(60, TimeUnit.SECONDS);
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> value : result.entrySet()) response.add(value.getKey(), value.getValue());
                    break;
                case "patch_plugin":
                    result = manager.patchDeveloper(request.get("plugin_id").getAsString(), request.get("base_hash").getAsString(),
                            request.getAsJsonObject("changes"), request.getAsJsonArray("removed")).get(60, TimeUnit.SECONDS);
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> value : result.entrySet()) response.add(value.getKey(), value.getValue());
                    break;
                case "start_debugger":
                    String method = request.get("method").getAsString();
                    if (!method.matches("[A-Za-z][A-Za-z0-9_]{0,119}")) throw new IOException("InvalidDebugMethod");
                    connection.setSoTimeout(120000);
                    response.addProperty("debugger", "pdb");
                    terminal.write(response + "\n");
                    debugging = true;
                    manager.debugDeveloper(id, method, terminal, "pdb", 0);
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
        }
        terminal.write(debugging ? "\n" + response + "\n" : response + "\n");
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
        public String readLine() throws IOException { return read(8192); }
        public void write(String data) throws IOException { output.write(data); output.flush(); }
    }
}
