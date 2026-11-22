package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.LruCache;
import android.util.Xml;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class BygramIconPacks {
    public static final String MIME_TYPE = "application/vnd.bygram.icons";
    public static final String EXTENSION = ".byicons";
    public static final String EXTERA_EXTENSION = ".icons";

    private static final int MAGIC = 0x42474943; // BGIC
    private static final int VERSION = 1;
    private static final int EXTERA_VERSION = 2;
    private static final int ZIP_MAGIC = 0x504b0304;
    private static final int MAX_ICONS = 4096;
    private static final int MAX_SIDE = 128;
    private static final int MAX_ICON_BYTES = 96 * 1024;
    private static final int MAX_PACK_BYTES = 24 * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRY_BYTES = 512 * 1024;
    private static final Pattern ICON_NAME = Pattern.compile("[a-z_][a-z0-9_]{0,95}");
    private static final Pattern USERNAME = Pattern.compile("@?[A-Za-z0-9_]{1,32}");
    public static final int COVER_SIZE = 4;
    private static final LruCache<byte[], Bitmap> bitmaps = new LruCache<byte[], Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(byte[] key, Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    };
    private static volatile Pack active;
    private static volatile boolean activeLoaded;
    private static int activeVersion;

    private BygramIconPacks() {}

    public static class Info {
        public final UUID id;
        public final String title;
        public final long authorId;
        public final String author;

        private Info(UUID id, String title, long authorId, String author) {
            this.id = id;
            this.title = title;
            this.authorId = authorId;
            this.author = author;
        }
    }

    public static final class Pack extends Info {
        public final Map<String, byte[]> icons;
        private final byte[] publicKey;

        private Pack(Info info, byte[] publicKey, Map<String, byte[]> icons) {
            super(info.id, info.title, info.authorId, info.author);
            this.publicKey = publicKey;
            this.icons = Collections.unmodifiableMap(icons);
        }
    }

    public static final class Summary extends Info {
        public final Map<String, byte[]> cover;
        public final boolean editable;

        private Summary(Info info, Map<String, byte[]> cover, boolean editable) {
            super(info.id, info.title, info.authorId, info.author);
            this.cover = Collections.unmodifiableMap(cover);
            this.editable = editable;
        }
    }

    private static Context context() {
        return ApplicationLoader.applicationContext;
    }

    private static SharedPreferences preferences() {
        return context().getSharedPreferences("bygram_icon_packs", Context.MODE_PRIVATE);
    }

    private static File directory() {
        return new File(context().getNoBackupFilesDir(), "bygram-icons");
    }

    private static AtomicFile file(UUID id) {
        return new AtomicFile(new File(directory(), id + EXTENSION));
    }

    public static synchronized ArrayList<Summary> list(long account) {
        ArrayList<Summary> result = new ArrayList<>();
        File[] files = directory().listFiles((dir, name) -> name.endsWith(EXTENSION));
        if (files != null && files.length > 0) {
            byte[] authorPublicKey = null;
            if (account > 0) {
                try {
                    KeyStore.PrivateKeyEntry author = authorKey(account, false);
                    if (author != null) authorPublicKey = author.getCertificate().getPublicKey().getEncoded();
                } catch (IOException | GeneralSecurityException e) {
                    FileLog.e("Unable to read icon pack author key");
                }
            }
            for (File file : files) {
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                        new AtomicFile(file).openRead()))) {
                    boolean signed = readHeader(in);
                    byte[] publicKey = signed ? readBytes(in, in.readUnsignedShort(), 256) : null;
                    if (signed) readBytes(in, in.readUnsignedShort(), 128);
                    int length = in.readInt();
                    if (length <= 0 || length > MAX_PACK_BYTES) continue;
                    Info info = readInfo(in, signed);
                    if (!file.getName().equals(info.id + EXTENSION)) continue;
                    int count = readIconCount(in);
                    Map<String, byte[]> cover = new LinkedHashMap<>();
                    for (int i = 0; i < Math.min(count, COVER_SIZE); i++) {
                        String name = in.readUTF();
                        if (!ICON_NAME.matcher(name).matches() || cover.containsKey(name)) throw new IOException("Invalid cover icon");
                        cover.put(name, readIcon(in));
                    }
                    result.add(new Summary(info, cover, signed && info.authorId == account && Arrays.equals(publicKey, authorPublicKey)));
                } catch (IOException ignored) {
                }
            }
        }
        result.sort((left, right) -> left.title.compareToIgnoreCase(right.title));
        return result;
    }

    public static Pack load(UUID id) throws IOException, GeneralSecurityException {
        try (InputStream input = file(id).openRead()) {
            Pack pack = read(input);
            if (!pack.id.equals(id)) throw new IOException("Pack ID mismatch");
            return pack;
        }
    }

    public static synchronized UUID activeId() {
        String id = preferences().getString("active", null);
        try {
            return id == null ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static synchronized void activate(Pack pack) {
        preferences().edit().putString("active", pack == null ? null : pack.id.toString()).apply();
        active = pack;
        activeLoaded = true;
        activeVersion++;
        bitmaps.evictAll();
        AndroidUtilities.runOnUIThread(BygramIconDrawable::invalidateAll);
    }

    private static synchronized void loadActive() {
        if (activeLoaded || context() == null) return;
        activeLoaded = true;
        UUID id = activeId();
        if (id == null) return;
        final int version = activeVersion;
        Utilities.themeQueue.postRunnable(() -> {
            try {
                Pack pack = load(id);
                synchronized (BygramIconPacks.class) {
                    if (version != activeVersion) return;
                    active = pack;
                }
                AndroidUtilities.runOnUIThread(BygramIconDrawable::invalidateAll);
            } catch (IOException | GeneralSecurityException e) {
                FileLog.e("Unable to load active icon pack");
            }
        });
    }

    static Bitmap replacement(String name) {
        if (!activeLoaded) loadActive();
        Pack pack = active;
        return pack == null ? null : preview(pack.icons.get(name));
    }

    static boolean preservesColors() {
        Pack pack = active;
        return pack != null && pack.authorId == 0;
    }

    public static Bitmap preview(byte[] image) {
        if (image == null) return null;
        Bitmap bitmap = bitmaps.get(image);
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
            if (bitmap != null) bitmaps.put(image, bitmap);
        }
        return bitmap;
    }

    private static String keyAlias(long authorId) {
        return "bygram-icons:" + authorId;
    }

    private static KeyStore keyStore() throws IOException, GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    private static KeyStore.PrivateKeyEntry authorKey(long account, boolean create) throws IOException, GeneralSecurityException {
        KeyStore store = keyStore();
        String alias = keyAlias(account);
        if (create && !store.containsAlias(alias)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256).build());
            generator.generateKeyPair();
        }
        return (KeyStore.PrivateKeyEntry) store.getEntry(alias, null);
    }

    public static synchronized boolean canEdit(Pack pack, long account) throws IOException, GeneralSecurityException {
        if (pack.authorId != account || account == 0) return false;
        KeyStore.PrivateKeyEntry key = authorKey(account, false);
        return key != null && Arrays.equals(pack.publicKey, key.getCertificate().getPublicKey().getEncoded());
    }

    public static String authorUsername(String input) {
        String username = input.trim();
        if (!USERNAME.matcher(username).matches()) return null;
        return username.startsWith("@") ? username : "@" + username;
    }

    public static synchronized Pack create(long account, String username, String title) throws IOException, GeneralSecurityException {
        String author = authorUsername(username);
        if (author == null) throw new IOException("Invalid author username");
        Info info = new Info(UUID.randomUUID(), title, account, author);
        return save(info, authorKey(account, true), new LinkedHashMap<>());
    }

    public static synchronized Pack edit(Pack pack, long account, String iconName, byte[] image) throws IOException, GeneralSecurityException {
        if (!canEdit(pack, account) || !ICON_NAME.matcher(iconName).matches()) throw new IOException("Cannot edit pack");
        Map<String, byte[]> icons = new LinkedHashMap<>(pack.icons);
        if (image == null) icons.remove(iconName); else icons.put(iconName, image);
        return save(pack, authorKey(account, false), icons);
    }

    private static Pack save(Info info, KeyStore.PrivateKeyEntry key, Map<String, byte[]> icons) throws IOException, GeneralSecurityException {
        validateInfo(info, true);
        byte[] publicKey = key.getCertificate().getPublicKey().getEncoded();
        byte[] payload = encode(info, icons);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(key.getPrivateKey());
        signer.update(payload);
        write(info.id, publicKey, signer.sign(), payload);
        Pack result = new Pack(info, publicKey, icons);
        if (info.id.equals(activeId())) activate(result);
        return result;
    }

    private static byte[] encode(Info info, Map<String, byte[]> icons) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeLong(info.id.getMostSignificantBits());
        data.writeLong(info.id.getLeastSignificantBits());
        data.writeUTF(info.title);
        data.writeLong(info.authorId);
        data.writeUTF(info.author);
        data.writeInt(icons.size());
        for (Map.Entry<String, byte[]> icon : icons.entrySet()) {
            data.writeUTF(icon.getKey());
            data.writeInt(icon.getValue().length);
            data.write(icon.getValue());
            if (bytes.size() > MAX_PACK_BYTES) throw new IOException("Pack too large");
        }
        return bytes.toByteArray();
    }

    public static synchronized Pack importPack(InputStream input) throws IOException, GeneralSecurityException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(input));
        in.mark(4);
        int magic = in.readInt();
        in.reset();
        if (magic == ZIP_MAGIC) {
            Pack pack = readExtera(in, catalog());
            return storeImported(pack, null, encode(pack, pack.icons));
        }
        return readStored(in, true);
    }

    private static Pack read(InputStream input) throws IOException, GeneralSecurityException {
        return readStored(new DataInputStream(new BufferedInputStream(input)), false);
    }

    private static Pack readStored(DataInputStream in, boolean importing) throws IOException, GeneralSecurityException {
        boolean signed = readHeader(in);
        byte[] key = signed ? readBytes(in, in.readUnsignedShort(), 256) : null;
        byte[] signature = signed ? readBytes(in, in.readUnsignedShort(), 128) : null;
        byte[] payload = readBytes(in, in.readInt(), MAX_PACK_BYTES);
        Pack pack = decode(in, key, signature, payload);
        return importing ? storeImported(pack, signature, payload) : pack;
    }

    private static Pack storeImported(Pack pack, byte[] signature, byte[] payload) throws IOException, GeneralSecurityException {
        if (file(pack.id).getBaseFile().exists()) {
            Pack current = load(pack.id);
            if (!Arrays.equals(current.publicKey, pack.publicKey) || current.authorId != pack.authorId ||
                    !current.author.equals(pack.author)) throw new IOException("Pack author mismatch");
        }
        write(pack.id, pack.publicKey, signature, payload);
        if (pack.id.equals(activeId())) activate(pack);
        return pack;
    }

    private static Set<String> catalog() {
        Resources resources = context().getResources();
        TypedArray ids = resources.obtainTypedArray(R.array.bygram_icon_catalog);
        Set<String> names = new HashSet<>();
        try {
            for (int i = 0; i < ids.length(); i++) {
                int id = ids.getResourceId(i, 0);
                if (id != 0) names.add(resources.getResourceEntryName(id));
            }
        } finally {
            ids.recycle();
        }
        return names;
    }

    private static Pack readExtera(InputStream input, Set<String> catalog) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        int total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entries.size() >= MAX_ICONS + 1 || name.length() > 256 || entries.containsKey(name)) {
                    throw new IOException("Invalid icon archive entry");
                }
                byte[] data = readBounded(zip, Math.min(MAX_ARCHIVE_ENTRY_BYTES, MAX_PACK_BYTES - total));
                entries.put(name, data);
                total += data.length;
            }
        }
        byte[] metadata = entries.remove("metadata.json");
        if (metadata == null) throw new IOException("Missing icon archive metadata");
        try {
            JSONObject json = new JSONObject(new String(metadata, StandardCharsets.UTF_8));
            if (json.getInt("schemaVersion") != 1) throw new IOException("Unsupported exteraGram icon pack");
            String packId = json.getString("packId").trim();
            if (packId.isEmpty() || packId.length() > 128) throw new IOException("Invalid exteraGram pack ID");
            String title = json.getString("packName").trim();
            String author = json.getString("author").trim();
            UUID id = UUID.nameUUIDFromBytes(("exteraGram:" + packId + "\u0000" + author).getBytes(StandardCharsets.UTF_8));
            Info info = new Info(id, title, 0, author);
            validateInfo(info, false);
            JSONObject mapping = json.getJSONObject("icons");
            if (mapping.length() > MAX_ICONS) throw new IOException("Too many icons");

            Map<String, byte[]> icons = new LinkedHashMap<>();
            Map<String, byte[]> normalized = new LinkedHashMap<>();
            int iconBytes = 0;
            for (Iterator<String> keys = mapping.keys(); keys.hasNext();) {
                String name = keys.next();
                if (!catalog.contains(name) || !ICON_NAME.matcher(name).matches()) continue;
                String path = mapping.getString(name);
                byte[] source = entries.get(path);
                if (source == null) throw new IOException("Missing icon image");
                byte[] image = normalized.get(path);
                if (image == null) {
                    if (path.endsWith(".svg")) {
                        image = prepareSvg(source);
                    } else if (path.endsWith(".png")) {
                        image = prepareIcon(source);
                    } else {
                        continue;
                    }
                    normalized.put(path, image);
                }
                iconBytes += image.length;
                if (iconBytes > MAX_PACK_BYTES) throw new IOException("Pack too large");
                icons.put(name, image);
            }
            if (icons.isEmpty()) throw new IOException("No compatible icons");
            return new Pack(info, null, icons);
        } catch (JSONException | StackOverflowError e) {
            throw new IOException("Invalid exteraGram icon pack metadata", e);
        }
    }

    private static byte[] prepareSvg(byte[] image) throws IOException {
        String xml = new String(image, StandardCharsets.UTF_8);
        if (xml.contains("<!DOCTYPE")) throw new IOException("SVG document types are not supported");
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));
            for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event != XmlPullParser.START_TAG) continue;
                String name = parser.getName();
                if (parser.getDepth() > 32 || "use".equals(name) || "image".equals(name) ||
                        "pattern".equals(name) || "marker".equals(name)) {
                    throw new IOException("Unsupported SVG element");
                }
                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    String attribute = parser.getAttributeName(i);
                    if ("href".equals(attribute) || attribute.endsWith(":href")) {
                        throw new IOException("Unsupported SVG reference");
                    }
                }
            }

            SVG svg = SVG.getFromString(xml);
            RectF viewBox = svg.getDocumentViewBox();
            float width = viewBox == null ? svg.getDocumentWidth() : viewBox.width();
            float height = viewBox == null ? svg.getDocumentHeight() : viewBox.height();
            if (!(width > 0 && height > 0 && width <= 8192 && height <= 8192)) {
                throw new IOException("Invalid SVG dimensions");
            }
            if (viewBox == null) svg.setDocumentViewBox(0, 0, width, height);
            svg.setDocumentWidth("100%");
            svg.setDocumentHeight("100%");
            float scale = MAX_SIDE / Math.max(width, height);
            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
            try {
                svg.renderToCanvas(new Canvas(bitmap));
            } catch (RuntimeException | StackOverflowError e) {
                bitmap.recycle();
                throw new IOException("Cannot render SVG icon", e);
            }
            return encodePng(bitmap);
        } catch (XmlPullParserException | SVGParseException e) {
            throw new IOException("Invalid SVG icon", e);
        }
    }

    private static boolean readHeader(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("Unsupported icon pack");
        int version = in.readUnsignedByte();
        if (version != VERSION && version != EXTERA_VERSION) throw new IOException("Unsupported icon pack");
        return version == VERSION;
    }

    private static Info readInfo(DataInputStream in, boolean signed) throws IOException {
        Info info = new Info(new UUID(in.readLong(), in.readLong()), in.readUTF(), in.readLong(), in.readUTF());
        validateInfo(info, signed);
        return info;
    }

    private static void validateInfo(Info info, boolean signed) throws IOException {
        if (info.title.trim().isEmpty() || info.title.length() > 60 || info.author.trim().isEmpty() ||
                info.author.length() > 100 || (signed ? info.authorId <= 0 : info.authorId != 0)) {
            throw new IOException("Invalid pack metadata");
        }
    }

    private static Pack decode(DataInputStream in, byte[] key, byte[] signature, byte[] payload) throws IOException, GeneralSecurityException {
        if (in.read() != -1) throw new IOException("Unexpected pack data");
        if (key != null) {
            PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(key));
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(payload);
            if (!verifier.verify(signature)) throw new IOException("Invalid pack signature");
        }

        DataInputStream data = new DataInputStream(new ByteArrayInputStream(payload));
        Info info = readInfo(data, key != null);
        int count = readIconCount(data);
        Map<String, byte[]> icons = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String name = data.readUTF();
            if (!ICON_NAME.matcher(name).matches() || icons.containsKey(name)) throw new IOException("Invalid icon name");
            byte[] image = readIcon(data);
            Bitmap decoded = BitmapFactory.decodeByteArray(image, 0, image.length);
            if (decoded == null) throw new IOException("Invalid icon pixels");
            decoded.recycle();
            icons.put(name, image);
        }
        if (data.read() != -1) throw new IOException("Unexpected icon data");
        return new Pack(info, key, icons);
    }

    private static int readIconCount(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_ICONS) throw new IOException("Invalid icon count");
        return count;
    }

    private static byte[] readIcon(DataInputStream in) throws IOException {
        byte[] image = readBytes(in, in.readInt(), MAX_ICON_BYTES);
        BitmapFactory.Options metadata = bounds(image);
        if (!"image/png".equals(metadata.outMimeType) || metadata.outWidth > MAX_SIDE || metadata.outHeight > MAX_SIDE) {
            throw new IOException("Invalid icon image");
        }
        return image;
    }

    private static byte[] readBytes(DataInputStream in, int length, int maximum) throws IOException {
        if (length <= 0 || length > maximum) throw new IOException("Invalid pack field length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    private static void write(UUID id, byte[] key, byte[] signature, byte[] payload) throws IOException {
        File directory = directory();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create icon directory");
        AtomicFile file = file(id);
        FileOutputStream out = file.startWrite();
        try {
            DataOutputStream data = new DataOutputStream(out);
            data.writeInt(MAGIC);
            data.writeByte(key == null ? EXTERA_VERSION : VERSION);
            if (key != null) {
                data.writeShort(key.length);
                data.write(key);
                data.writeShort(signature.length);
                data.write(signature);
            }
            data.writeInt(payload.length);
            data.write(payload);
            file.finishWrite(out);
        } catch (IOException e) {
            file.failWrite(out);
            throw e;
        }
    }

    public static synchronized void shareCopy(Pack pack, File target) throws IOException {
        try (FileInputStream input = file(pack.id).openRead(); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    public static synchronized void remove(UUID id) throws IOException {
        file(id).delete();
        if (file(id).getBaseFile().exists()) throw new IOException("Cannot remove icon pack");
        if (id.equals(activeId())) activate(null);
    }

    private static BitmapFactory.Options bounds(byte[] image) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Invalid image size");
        return options;
    }

    private static byte[] readBounded(InputStream in, int maximum) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (bytes.size() + count > maximum) throw new IOException("Icon data too large");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }

    public static byte[] prepareIcon(InputStream in) throws IOException {
        return prepareIcon(readBounded(in, 16 * 1024 * 1024));
    }

    private static byte[] prepareIcon(byte[] image) throws IOException {
        BitmapFactory.Options options = bounds(image);
        int longestSide = Math.max(options.outWidth, options.outHeight);
        if (longestSide > 8192) throw new IOException("Image dimensions too large");
        options.inJustDecodeBounds = false;
        options.inSampleSize = Math.max(1, Integer.highestOneBit(longestSide / MAX_SIDE));
        Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length, options);
        if (bitmap == null) throw new IOException("Cannot decode image");
        if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > MAX_SIDE) {
            float scale = (float) MAX_SIDE / Math.max(bitmap.getWidth(), bitmap.getHeight());
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * scale)),
                    Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
            bitmap.recycle();
            bitmap = scaled;
        }
        return encodePng(bitmap);
    }

    private static byte[] encodePng(Bitmap bitmap) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes) || bytes.size() > MAX_ICON_BYTES) {
                throw new IOException("Cannot encode icon");
            }
            return bytes.toByteArray();
        } finally {
            bitmap.recycle();
        }
    }
}
