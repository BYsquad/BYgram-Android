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
    static PluginPackage stage(JsonObject entry, byte[] bytes, File directory, int host, int base) throws Exception {
        PluginPackage.check(bytes.length == entry.get("size").getAsLong(), "CatalogPackageMismatch");
        PluginPackage pkg = PluginPackage.stage(new ByteArrayInputStream(bytes), directory, 1, host, base);
        PluginPackage.check(pkg.sha256.equals(entry.get("sha256").getAsString())
                && pkg.manifest.metadata().equals(entry.getAsJsonObject("manifest")), "CatalogPackageMismatch");
        pkg.source = "catalog";
        if (entry.has("author_key")) {
            PluginPackage.check(entry.get("author_key").getAsString().equals(pkg.signer), "InvalidAuthorSignature");
        }
        if (entry.has("release")) pkg.catalogRelease = entry.getAsJsonObject("release").deepCopy();
        return pkg;
    }
    static byte[] fetch(String file, int limit) throws IOException {
        return fetch(file, limit, new Request());
    }
    static byte[] fetch(String file, int limit, Request request) throws IOException {
        request.check();
        if (!file.equals("index.json") && !file.matches("[0-9a-f]{64}\\.byplugin")) throw new IOException("InvalidCatalog");
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE + file).openConnection();
        request.connection = connection;
        ScheduledFuture<?> deadline = deadlines.schedule(request::close, 30, TimeUnit.SECONDS);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
        try {
            request.check();
            if (connection.getResponseCode() != 200) throw new IOException("CatalogUnavailable");
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
