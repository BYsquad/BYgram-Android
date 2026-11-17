package org.telegram.messenger.plugins;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Curated HTTPS index; downloaded code still passes the local installer and explicit consent. */
public final class PluginCatalog {
    public static final String BASE = "https://byxarek.site/TEST-PROJECT/BYgram/plugins/";
    private static final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "BYgramCatalogTimeout"); thread.setDaemon(true); return thread;
    });
    public static final class Request implements AutoCloseable {
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;
        public void check() throws IOException { if (cancelled) throw new IOException("CatalogCancelled"); }
        @Override public void close() {
            cancelled = true;
            HttpURLConnection current = connection;
            if (current != null) deadlines.execute(current::disconnect);
        }
    }
    public static JsonArray parse(byte[] bytes, int host, int base) throws Exception {
        PluginPackage.check(bytes.length <= 1024 * 1024, "CatalogLimitExceeded");
        // A JSON token needs at least one input byte; the transport byte limit bounds this budget.
        JsonObject root = PluginSettingsFile.parseJson(bytes, 1024 * 1024, 12, 1024 * 1024);
        PluginPackage.check(root.size() == 2 && root.has("format") && root.get("format").isJsonPrimitive()
                && root.getAsJsonPrimitive("format").isNumber() && root.get("format").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) == 0
                && root.has("plugins") && root.get("plugins").isJsonArray(), "InvalidCatalog");
        JsonArray entries = root.getAsJsonArray("plugins"), result = new JsonArray();
        PluginPackage.check(entries.size() <= 100, "CatalogLimitExceeded");
        Set<String> ids = new HashSet<>();
        for (JsonElement value : entries) {
            JsonObject item = value.getAsJsonObject();
            PluginPackage.check(item.size() == 4 + (item.has("release") ? 1 : 0) + (item.has("author_key") ? 1 : 0) && item.has("manifest") && item.get("manifest").isJsonObject()
                    && item.has("sha256") && item.get("sha256").isJsonPrimitive() && item.getAsJsonPrimitive("sha256").isString()
                    && item.has("file") && item.get("file").isJsonPrimitive() && item.getAsJsonPrimitive("file").isString()
                    && item.has("size") && item.get("size").isJsonPrimitive() && item.getAsJsonPrimitive("size").isNumber(), "InvalidCatalog");
            String hash = item.get("sha256").getAsString();
            if (item.has("author_key")) PluginPackage.check(item.get("author_key").isJsonPrimitive()
                    && item.getAsJsonPrimitive("author_key").isString() && item.get("author_key").getAsString().matches("[0-9a-f]{64}"), "InvalidCatalog");
            if (item.has("release")) validateRelease(item.getAsJsonObject("release"), item.getAsJsonObject("manifest"));
            PluginPackage.check(hash.matches("[0-9a-f]{64}") && item.get("file").getAsString().equals(hash + ".byplugin"), "InvalidCatalog");
            PluginPackage.check(item.get("size").getAsBigDecimal().longValueExact() > 0 && item.get("size").getAsBigDecimal().longValueExact() <= PluginPackage.MAX_ZIP, "InvalidCatalog");
            try {
                PluginManifest manifest = PluginManifest.parse(item.get("manifest").toString().getBytes(StandardCharsets.UTF_8), 1, host, base);
                PluginPackage.check(ids.add(manifest.id()), "InvalidCatalog");
                result.add(item.deepCopy());
            } catch (PluginManifest.ValidationException rejected) {
                // One rejected manifest must not hide the remaining valid catalogue entries.
            }
        }
        return result;
    }
    static JsonArray parseRepository(byte[] bytes, String address, int host, int base) throws Exception {
        JsonObject root = PluginSettingsFile.parseJson(bytes, 1024 * 1024, 12, 1024 * 1024);
        if (!root.has("schema")) return parse(bytes, host, base);
        PluginPackage.check(root.get("schema").isJsonPrimitive() && root.getAsJsonPrimitive("schema").isNumber()
                && root.get("schema").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) == 0
                && root.has("plugins") && root.get("plugins").isJsonArray(), "InvalidCatalog");
        JsonArray entries = root.getAsJsonArray("plugins"), result = new JsonArray();
        PluginPackage.check(entries.size() <= 100, "CatalogLimitExceeded");
        Set<String> ids = new HashSet<>();
        for (JsonElement value : entries) {
            PluginPackage.check(value.isJsonObject(), "InvalidCatalog");
            JsonObject entry = value.getAsJsonObject();
            String id = exteraString(entry, "id", 32);
            String name = exteraString(entry, "name", 80);
            String version = exteraString(entry, "version", 80);
            String file = exteraString(entry, "file", 2048);
            String hash = exteraString(entry, "sha256", 64);
            URL url = resolveExtera(address, file);
            String path = url.getPath().toLowerCase(Locale.ROOT);
            boolean single = path.endsWith(".plugin");
            PluginPackage.check(single || path.endsWith(".elyx") || path.endsWith(".eaf")
                    || path.endsWith(".elyx.zip") || path.endsWith(".eaf.zip"), "InvalidCatalog");
            PluginPackage.check(id.matches(single ? "[A-Za-z][A-Za-z0-9_-]{1,31}" : "[A-Za-z0-9_]{2,32}")
                    && ids.add(id) && version.matches("(0|[1-9][0-9]{0,9})(\\.(0|[1-9][0-9]{0,9})){0,2}")
                    && hash.matches("[0-9a-f]{64}") && entry.has("size") && entry.get("size").isJsonPrimitive()
                    && entry.getAsJsonPrimitive("size").isNumber(), "InvalidCatalog");
            long size = entry.get("size").getAsBigDecimal().longValueExact();
            PluginPackage.check(size > 0 && size <= (single ? 2 * 1024 * 1024 : PluginPackage.MAX_ZIP), "InvalidCatalog");
            JsonObject item = new JsonObject(), manifest = new JsonObject();
            manifest.addProperty("name", name);
            manifest.addProperty("version", version);
            JsonArray categories = new JsonArray();
            JsonElement category = entry.get("category");
            if (category != null) {
                JsonArray candidates = category.isJsonArray() ? category.getAsJsonArray() : new JsonArray();
                if (!category.isJsonArray()) candidates.add(category);
                for (JsonElement candidate : candidates) {
                    if (categories.size() == 3) break;
                    if (candidate.isJsonPrimitive() && candidate.getAsJsonPrimitive().isString()
                            && candidate.getAsString().matches("appearance|chats|media|privacy|tools|integrations|fun|libraries")
                            && !categories.contains(candidate)) categories.add(candidate);
                }
            }
            if (categories.size() != 0) manifest.add("category", categories);
            item.add("manifest", manifest);
            item.addProperty("extera_id", id);
            item.addProperty("file", url.toExternalForm());
            item.addProperty("size", size);
            item.addProperty("sha256", hash);
            item.addProperty("archive", !single);
            result.add(item);
        }
        return result;
    }
    private static String exteraString(JsonObject entry, String key, int limit) throws IOException {
        JsonElement value = entry.get(key);
        PluginPackage.check(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), "InvalidCatalog");
        String text = value.getAsString();
        PluginPackage.check(!text.trim().isEmpty() && text.length() <= limit, "InvalidCatalog");
        return text;
    }
    static void verifyExtera(JsonObject entry, byte[] bytes) throws Exception {
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(64);
        for (byte value : hash) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
        PluginPackage.check(bytes.length == entry.get("size").getAsLong() && hex.toString().equals(entry.get("sha256").getAsString()),
                "CatalogPackageMismatch");
    }
    private static URL resolveExtera(String address, String file) throws IOException {
        try {
            URI uri = new URI(address).resolve(new URI(file));
            PluginPackage.check("https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getFragment() == null, "InvalidCatalog");
            return uri.toURL();
        } catch (URISyntaxException | IllegalArgumentException invalid) {
            throw new IOException("InvalidCatalog", invalid);
        }
    }
    static byte[] fetchExtera(JsonObject entry, Request request) throws IOException {
        URL address = resolveExtera(BASE, entry.get("file").getAsString());
        return fetch(address, entry.get("size").getAsInt(), request, false, 4);
    }
    private static void validateRelease(JsonObject release, JsonObject manifest) throws IOException {
        PluginPackage.check(release.size() == 5 && release.has("repository") && release.get("repository").isJsonPrimitive()
                && release.getAsJsonPrimitive("repository").isString()
                && release.get("repository").getAsString().matches("https://github\\.com/[a-z0-9][a-z0-9-]{0,38}/[a-z0-9_.-]{1,100}")
                && release.has("tag") && release.get("tag").isJsonPrimitive() && release.getAsJsonPrimitive("tag").isString(), "InvalidCatalog");
        String tag = release.get("tag").getAsString();
        PluginPackage.check(tag.equals(manifest.get("version").getAsString()) || tag.equals("v" + manifest.get("version").getAsString()), "InvalidCatalog");
        for (String key : new String[]{"repository_id", "release_id", "asset_id"}) {
            PluginPackage.check(release.has(key) && release.get(key).isJsonPrimitive() && release.getAsJsonPrimitive(key).isNumber(), "InvalidCatalog");
            try { PluginPackage.check(release.get(key).getAsBigDecimal().longValueExact() > 0, "InvalidCatalog"); }
            catch (ArithmeticException invalid) { throw new IOException("InvalidCatalog", invalid); }
        }
    }
    static PluginPackage stage(JsonObject entry, byte[] bytes, File directory, int host, int base, boolean approved) throws Exception {
        PluginPackage.check(bytes.length == entry.get("size").getAsLong(), "CatalogPackageMismatch");
        PluginPackage pkg = PluginPackage.stage(new ByteArrayInputStream(bytes), directory, 1, host, base);
        PluginPackage.check(pkg.sha256.equals(entry.get("sha256").getAsString())
                && pkg.manifest.metadata().equals(entry.getAsJsonObject("manifest")), "CatalogPackageMismatch");
        if (approved) pkg.source = "catalog";
        if (entry.has("author_key")) {
            PluginPackage.check(entry.get("author_key").getAsString().equals(pkg.signer), "InvalidAuthorSignature");
        }
        if (approved && entry.has("release")) pkg.catalogRelease = entry.getAsJsonObject("release").deepCopy();
        return pkg;
    }
    static Set<String> fetchRevocations(Request request) throws IOException {
        int limit = 1024 * 1024;
        byte[] bytes = fetch("revoked.json", limit, request);
        return PluginStore.parseRevocations(PluginSettingsFile.parseJson(bytes, limit, 3, limit));
    }

    static byte[] fetch(String file, int limit) throws IOException {
        return fetch(file, limit, new Request());
    }
    static byte[] fetch(String file, int limit, Request request) throws IOException {
        if (!file.equals("index.json") && !file.equals("revoked.json") && !file.matches("[0-9a-f]{64}\\.byplugin")) throw new IOException("InvalidCatalog");
        return fetch(new URL(BASE + file), limit, request, !file.equals("revoked.json"));
    }

    public static URL repository(String address) throws IOException {
        try {
            if (address == null || address.length() > 1024) throw new IOException("InvalidCatalog");
            URI uri = new URI(address);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IOException("InvalidCatalog");
            }
            if ("github.com".equalsIgnoreCase(uri.getHost()) && uri.getPort() == -1) {
                java.util.regex.Matcher github = java.util.regex.Pattern.compile(
                        "/([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+)(?:/tree/([A-Za-z0-9_.-]+)((?:/[A-Za-z0-9_.-]+)*))?/?").matcher(uri.getRawPath());
                if (github.matches()) {
                    for (String segment : uri.getRawPath().split("/")) {
                        if (segment.equals(".") || segment.equals("..")) throw new IOException("InvalidCatalog");
                    }
                    String ref = github.group(3) == null ? "HEAD" : github.group(3);
                    String directory = github.group(4) == null ? "" : github.group(4);
                    return new URL("https://raw.githubusercontent.com/" + github.group(1) + "/" + github.group(2)
                            + "/" + ref + directory + "/extera-plugins.json");
                }
            }
            if (!uri.getRawPath().endsWith(".json")) throw new IOException("InvalidCatalog");
            return uri.toURL();
        } catch (URISyntaxException | IllegalArgumentException malformed) {
            throw new IOException("InvalidCatalog", malformed);
        }
    }
    public static String sourceAddress(String address) throws IOException {
        String channel = channelName(address);
        if (channel == null && address != null && address.length() <= 1024
                && address.startsWith("https://t.me/exteraPluginRepo?git=")) {
            try {
                address = URLDecoder.decode(address.substring("https://t.me/exteraPluginRepo?git=".length()), "UTF-8");
            } catch (IllegalArgumentException malformed) {
                throw new IOException("InvalidCatalog", malformed);
            }
        }
        return channel == null ? repository(address).toExternalForm() : "https://t.me/" + channel;
    }
    public static String channelName(String address) {
        if (address == null) return null;
        try {
            URI uri = new URI(address);
            if (!"https".equals(uri.getScheme()) || !"t.me".equalsIgnoreCase(uri.getHost()) || uri.getUserInfo() != null
                    || uri.getPort() != -1 || uri.getFragment() != null) return null;
            String path = uri.getPath();
            String name = path != null && path.matches("/[A-Za-z][A-Za-z0-9_]{3,31}/?") ? path.substring(1).replace("/", "") : null;
            String query = uri.getRawQuery();
            if (query == null) return name;
            return "exteraPluginRepo".equalsIgnoreCase(name) && query.matches("c=[A-Za-z][A-Za-z0-9_]{3,31}") ? query.substring(2) : null;
        } catch (URISyntaxException invalid) { return null; }
    }

    static byte[] fetchRepository(String address, String file, int limit, Request request) throws IOException {
        URL index = repository(address);
        if (file != null && !file.matches("[0-9a-f]{64}\\.byplugin")) throw new IOException("InvalidCatalog");
        return fetch(file == null ? index : new URL(index, file), limit, request, false);
    }

    private static byte[] fetch(URL url, int limit, Request request, boolean cache) throws IOException {
        return fetch(url, limit, request, cache, 0);
    }
    private static byte[] fetch(URL url, int limit, Request request, boolean cache, int redirects) throws IOException {
        request.check();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        request.connection = connection;
        ScheduledFuture<?> deadline = deadlines.schedule(request::close, 30, TimeUnit.SECONDS);
        connection.setInstanceFollowRedirects(false);
        if (!cache) connection.setUseCaches(false);
        connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
        try {
            request.check();
            int code = connection.getResponseCode();
            if (redirects > 0 && (code == 301 || code == 302 || code == 303 || code == 307 || code == 308)) {
                String location = connection.getHeaderField("Location");
                if (location == null) throw new IOException("CatalogUnavailable");
                return fetch(resolveExtera(url.toExternalForm(), location), limit, request, false, redirects - 1);
            }
            if (code != 200) throw new IOException("CatalogUnavailable");
            if (connection.getContentLengthLong() > limit) throw new IOException("CatalogLimitExceeded");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    request.check();
                    if (out.size() + count > limit) throw new IOException("CatalogLimitExceeded");
                    out.write(buffer, 0, count);
                }
                return out.toByteArray();
            }
        } catch (IOException unavailable) {
            request.check(); throw new IOException("CatalogUnavailable", unavailable);
        } finally { deadline.cancel(false); request.connection = null; connection.disconnect(); }
    }
}
