package org.telegram.messenger.plugins;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class PluginChannelCatalog {
    private PluginChannelCatalog() {}

    static JsonArray list(String channel, int account, PluginCatalog.Request request, PluginRuntime runtime,
                          int host, int base) throws Exception {
        String identity = PluginContext.currentIdentity(account);
        TLRPC.Chat chat = resolve(channel, account, request);
        TLRPC.TL_inputPeerChannel peer = new TLRPC.TL_inputPeerChannel();
        peer.channel_id = chat.id;
        peer.access_hash = chat.access_hash;
        TLRPC.TL_messages_search search = new TLRPC.TL_messages_search();
        search.peer = peer;
        search.q = "";
        search.filter = new TLRPC.TL_inputMessagesFilterDocument();
        search.limit = 100;
        java.util.ArrayList<JsonObject> entries = new java.util.ArrayList<>();
        java.util.Map<Integer, TLRPC.Message> attachments = new java.util.HashMap<>();
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (int page = 0; page < 20; page++) {
            TLRPC.messages_Messages messages = rpc(account, search, TLRPC.messages_Messages.class, request);
            checkIdentity(account, identity);
            int offset = search.offset_id;
            for (TLRPC.Message message : messages.messages) {
                if (message.id <= 0) continue;
                if (offset == 0 || message.id < offset) offset = message.id;
                if (!seen.add(message.id)) continue;
                JsonObject entry = entry(chat.id, identity, message);
                if (entry != null) {
                    entries.add(entry);
                    attachments.put(message.id, message);
                }
            }
            if (messages.messages.size() < search.limit || offset == search.offset_id) break;
            search.offset_id = offset;
        }
        entries.sort(java.util.Comparator.comparingInt((JsonObject item) -> item.get("published").getAsInt())
                .thenComparingInt(item -> item.get("message").getAsInt()).reversed());
        JsonArray result = new JsonArray();
        java.util.Set<String> pluginIds = new java.util.HashSet<>();
        // shortcut: bound automatic metadata reads, use an incremental cache for very large channels.
        long budget = 32L * 1024 * 1024;
        long deadline = android.os.SystemClock.elapsedRealtime() + 12000;
        for (JsonObject item : entries) {
            request.check();
            TLRPC.Message message = attachments.get(item.get("message").getAsInt());
            TLRPC.Document document = document(message);
            File cached = FileLoader.getInstance(account).getPathToAttach(document, true);
            if (cached.isFile() && cached.length() == document.size
                    || budget >= document.size && android.os.SystemClock.elapsedRealtime() < deadline) {
                try {
                    if (!cached.isFile() || cached.length() != document.size) {
                        budget -= document.size;
                        cached = downloadFile(account, message, document, request, deadline);
                    }
                    checkIdentity(account, identity);
                    PluginPackage.check(cached.length() == document.size, "CatalogPackageMismatch");
                    JsonObject manifest = runtime.catalogMetadata(cached, item.get("format").getAsString(), host, base);
                    if (!pluginIds.add(manifest.get("id").getAsString())) continue;
                    item.add("manifest", manifest);
                } catch (Exception ignored) {
                    request.check();
                    checkIdentity(account, identity);
                }
            }
            result.add(item);
        }
        return result;
    }

    private static JsonObject entry(long channel, String identity, TLRPC.Message message) {
        TLRPC.Document document = document(message);
        if (document == null) return null;
        String name = FileLoader.getDocumentFileName(document);
        String format = format(name);
        if (format == null || document.size <= 0 || document.size > limit(format)) return null;
        JsonObject entry = new JsonObject(), manifest = new JsonObject();
        manifest.addProperty("name", name);
        manifest.addProperty("version", "");
        entry.add("manifest", manifest);
        entry.addProperty("channel", channel);
        entry.addProperty("message", message.id);
        entry.addProperty("document", document.id);
        entry.addProperty("size", document.size);
        entry.addProperty("format", format);
        entry.addProperty("identity", identity);
        entry.addProperty("published", Math.max(message.date, message.edit_date));
        return entry;
    }

    static byte[] download(String channel, int account, JsonObject entry, PluginCatalog.Request request) throws Exception {
        String identity = entry.get("identity").getAsString();
        checkIdentity(account, identity);
        TLRPC.Chat chat = resolve(channel, account, request);
        PluginPackage.check(chat.id == entry.get("channel").getAsLong(), "CatalogPackageMismatch");
        TLRPC.TL_inputChannel peer = new TLRPC.TL_inputChannel();
        peer.channel_id = chat.id;
        peer.access_hash = chat.access_hash;
        TLRPC.TL_channels_getMessages messageRequest = new TLRPC.TL_channels_getMessages();
        messageRequest.channel = peer;
        int messageId = entry.get("message").getAsInt();
        messageRequest.id.add(messageId);
        TLRPC.messages_Messages response = rpc(account, messageRequest, TLRPC.messages_Messages.class, request);
        checkIdentity(account, identity);
        PluginPackage.check(response.messages.size() == 1 && response.messages.get(0).id == messageId, "CatalogPackageMismatch");
        TLRPC.Message message = response.messages.get(0);
        TLRPC.Document document = document(message);
        String format = entry.get("format").getAsString();
        PluginPackage.check(document != null && document.id == entry.get("document").getAsLong()
                && document.size == entry.get("size").getAsLong() && format.equals(format(FileLoader.getDocumentFileName(document))), "CatalogPackageMismatch");
        File file = downloadFile(account, message, document, request, android.os.SystemClock.elapsedRealtime() + 30000);
        checkIdentity(account, identity);
        byte[] bytes = PluginPackage.read(file, limit(format));
        PluginPackage.check(bytes.length == document.size, "CatalogPackageMismatch");
        return bytes;
    }

    private static TLRPC.Document document(TLRPC.Message message) {
        TLRPC.MessageMedia media = MessageObject.getMedia(message);
        return media == null ? null : media.document;
    }

    private static String format(String file) {
        if (file == null) return null;
        String name = file.toLowerCase(Locale.ROOT);
        if (name.endsWith(".byplugin")) return "bygram";
        if (name.endsWith(".plugin")) return "source";
        if (name.endsWith(".elyx") || name.endsWith(".elyx.zip") || name.endsWith(".eaf") || name.endsWith(".eaf.zip")) return "archive";
        return null;
    }

    private static int limit(String format) {
        return format.equals("source") ? 2 * 1024 * 1024 : PluginPackage.MAX_ZIP;
    }

    private static void checkIdentity(int account, String expected) throws IOException {
        PluginPackage.check(expected.equals(PluginContext.currentIdentity(account)), "AccountUnavailable");
    }

    private static TLRPC.Chat resolve(String username, int account, PluginCatalog.Request request) throws Exception {
        TLRPC.TL_contacts_resolveUsername resolve = new TLRPC.TL_contacts_resolveUsername();
        resolve.username = username;
        TLRPC.TL_contacts_resolvedPeer result = rpc(account, resolve, TLRPC.TL_contacts_resolvedPeer.class, request);
        if (result.peer instanceof TLRPC.TL_peerChannel) {
            for (TLRPC.Chat chat : result.chats) if (chat.id == result.peer.channel_id && chat instanceof TLRPC.TL_channel) {
                AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(account).putChats(result.chats, false));
                return chat;
            }
        }
        throw new IOException("CatalogUnavailable");
    }

    private static <T extends TLObject> T rpc(int account, TLObject query, Class<T> type, PluginCatalog.Request request) throws Exception {
        request.check();
        ConnectionsManager manager = ConnectionsManager.getInstance(account);
        CompletableFuture<T> result = new CompletableFuture<>();
        int token = manager.sendRequest(query, (response, error) -> {
            if (type.isInstance(response) && error == null) result.complete(type.cast(response));
            else result.completeExceptionally(new IOException("CatalogUnavailable"));
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
        try { return await(result, request); }
        finally { if (!result.isDone()) manager.cancelRequest(token, true); }
    }

    private static File downloadFile(int account, TLRPC.Message message, TLRPC.Document document,
                                     PluginCatalog.Request request, long deadline) throws Exception {
        FileLoader loader = FileLoader.getInstance(account);
        File cached = loader.getPathToAttach(document, true);
        if (cached.isFile() && cached.length() == document.size) return cached;
        NotificationCenter notifications = NotificationCenter.getInstance(account);
        String key = FileLoader.getAttachFileName(document);
        CompletableFuture<File> result = new CompletableFuture<>();
        NotificationCenter.NotificationCenterDelegate observer = (event, slot, args) -> {
            if (args.length == 0 || !key.equals(args[0])) return;
            if (event == NotificationCenter.fileLoaded && args.length > 1 && args[1] instanceof File)
                result.complete((File) args[1]);
            else if (event == NotificationCenter.fileLoadFailed)
                result.completeExceptionally(new IOException("CatalogUnavailable"));
        };
        AndroidUtilities.runOnUIThread(() -> {
            notifications.addObserver(observer, NotificationCenter.fileLoaded);
            notifications.addObserver(observer, NotificationCenter.fileLoadFailed);
            if (cached.isFile() && cached.length() == document.size) result.complete(cached);
            else loader.loadFile(document, new MessageObject(account, message, false, false), FileLoader.PRIORITY_NORMAL, 1);
        });
        try { return await(result, request, deadline); }
        finally { AndroidUtilities.runOnUIThread(() -> {
            notifications.removeObserver(observer, NotificationCenter.fileLoaded);
            notifications.removeObserver(observer, NotificationCenter.fileLoadFailed);
        }); }
    }

    private static <T> T await(CompletableFuture<T> result, PluginCatalog.Request request) throws Exception {
        return await(result, request, android.os.SystemClock.elapsedRealtime() + 30000);
    }
    private static <T> T await(CompletableFuture<T> result, PluginCatalog.Request request, long deadline) throws Exception {
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            request.check();
            try { return result.get(500, TimeUnit.MILLISECONDS); }
            catch (TimeoutException ignored) { }
            catch (java.util.concurrent.ExecutionException error) { throw new IOException("CatalogUnavailable", error.getCause()); }
        }
        throw new IOException("CatalogUnavailable");
    }
}
