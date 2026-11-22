import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.json.JSONObject;

public final class BygramIconPackCheck {
    private static final UUID PACK_ID = UUID.fromString("f6341948-fbb5-4f8b-865c-c236dd4fe4ba");
    private static final String AUTHOR = "Fixture author";

    public static void main(String[] args) throws Exception {
        Class<?> packs = Class.forName("org.telegram.messenger.BygramIconPacks");
        Method username = packs.getMethod("authorUsername", String.class);
        require("@Pack_Author".equals(username.invoke(null, " Pack_Author ")), "Author username is normalized");
        require("@Pack_Author".equals(username.invoke(null, "@Pack_Author")), "Author username keeps one @");
        require(username.invoke(null, "@@Pack_Author") == null && username.invoke(null, "pack author") == null
                && username.invoke(null, "") == null, "Invalid author usernames are rejected");
        Method read = packs.getDeclaredMethod("read", InputStream.class);
        read.setAccessible(true);
        Method prepareIcon = packs.getMethod("prepareIcon", InputStream.class);
        Bitmap fixture = Bitmap.createBitmap(384, 192, Bitmap.Config.ARGB_8888);
        fixture.eraseColor(Color.RED);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        fixture.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        fixture.recycle();
        byte[] image = (byte[]) prepareIcon.invoke(null, new ByteArrayInputStream(bytes.toByteArray()));
        Bitmap preview = (Bitmap) packs.getMethod("preview", byte[].class).invoke(null, image);
        require(preview.getWidth() == 128 && preview.getHeight() == 64, "Normalized icon dimensions");
        require(preview.getPixel(64, 32) == Color.RED, "Bitmap pixels survived scaling");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair key = generator.generateKeyPair();
        byte[] signed = pack(key, image, false);
        if (args.length == 1) {
            try (FileOutputStream output = new FileOutputStream(args[0])) {
                output.write(signed);
            }
        }
        Object imported = read.invoke(null, new ByteArrayInputStream(signed));
        Class<?> type = imported.getClass();
        require(PACK_ID.equals(type.getField("id").get(imported)), "Pack identity");
        require(AUTHOR.equals(type.getField("author").get(imported)), "Author preserved");
        require(type.getField("authorId").getLong(imported) == 42L, "Author ID preserved");
        require(((Map<?, ?>) type.getField("icons").get(imported)).containsKey("msg_add"), "Icon mapping");
        int authorOffset = indexOf(signed, AUTHOR.getBytes("UTF-8"));
        signed[authorOffset] ^= 1;
        reject(read, signed, "Modified author must fail signature verification");
        reject(read, pack(key, image, true), "Duplicate icon must fail validation");
        reject(read, new byte[]{0, 1, 2, 3}, "Invalid header must fail");
        System.out.println("PASS: signed pack, pinned author, validation and normalized image");

        Method readExtera = packs.getDeclaredMethod("readExtera", InputStream.class, Set.class);
        readExtera.setAccessible(true);
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 12'>"
                + "<defs><linearGradient id='g'><stop stop-color='red'/><stop offset='1' stop-color='blue'/>"
                + "</linearGradient></defs><path fill='url(#g)' d='M0 0H24V12H0Z'/></svg>";
        byte[] archive = exteraArchive(svg, image);
        Set<String> catalog = new HashSet<>();
        catalog.add("msg_add");
        catalog.add("msg_delete");
        Object foreign = readExtera.invoke(null, new ByteArrayInputStream(archive), catalog);
        require("@fixture".equals(type.getField("author").get(foreign)), "exteraGram author preserved");
        require(type.getField("authorId").getLong(foreign) == 0, "exteraGram author has no Telegram ID");
        Map<?, ?> importedIcons = (Map<?, ?>) type.getField("icons").get(foreign);
        require(importedIcons.keySet().equals(catalog), "Only catalog icons imported");
        Bitmap vector = (Bitmap) packs.getMethod("preview", byte[].class).invoke(null, importedIcons.get("msg_add"));
        require(vector.getWidth() == 128 && vector.getHeight() == 64, "SVG proportions");
        int left = vector.getPixel(10, 32);
        int right = vector.getPixel(118, 32);
        require(Color.alpha(left) == 255 && Color.red(left) > Color.blue(left) &&
                Color.alpha(right) == 255 && Color.red(right) < Color.blue(right), "SVG gradient colors");
        try {
            readExtera.invoke(null, new ByteArrayInputStream(exteraArchive("<!DOCTYPE svg>" + svg, image)), catalog);
            throw new AssertionError("SVG document type accepted");
        } catch (InvocationTargetException error) {
            require(error.getCause() instanceof java.io.IOException, "SVG document type rejected");
        }
        System.out.println("PASS: exteraGram PNG, SVG gradient, catalog matching and SVG validation");

        if (args.length == 2) checkAttachedPack(readExtera, type, args[0], args[1]);
    }

    private static byte[] exteraArchive(String svg, byte[] image) throws Exception {
        JSONObject names = new JSONObject().put("msg_add", "vector.svg")
                .put("msg_delete", "raster.png").put("missing_from_catalog", "vector.svg");
        JSONObject metadata = new JSONObject().put("schemaVersion", 1).put("packName", "Fixture")
                .put("packId", "fixture").put("author", "@fixture").put("icons", names);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("vector.svg"));
            zip.write(svg.getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("raster.png"));
            zip.write(image);
            zip.putNextEntry(new ZipEntry("metadata.json"));
            zip.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static void checkAttachedPack(Method readExtera, Class<?> type, String archive, String catalogPath) throws Exception {
        Set<String> catalog = new HashSet<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                new FileInputStream(catalogPath), StandardCharsets.UTF_8))) {
            String name;
            while ((name = reader.readLine()) != null) catalog.add(name);
        }
        Object pack;
        try (InputStream input = new FileInputStream(archive)) {
            pack = readExtera.invoke(null, input, catalog);
        }
        require("Liquid Glass Pack".equals(type.getField("title").get(pack)), "Attached pack title");
        Map<?, ?> icons = (Map<?, ?>) type.getField("icons").get(pack);
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"metadata.json".equals(entry.getName())) continue;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = zip.read(buffer)) != -1) bytes.write(buffer, 0, count);
                JSONObject mapping = new JSONObject(bytes.toString("UTF-8")).getJSONObject("icons");
                int vectors = 0;
                int rasters = 0;
                for (String name : catalog) {
                    String path = mapping.optString(name);
                    if (path.isEmpty()) continue;
                    require(icons.containsKey(name), "Missing compatible icon: " + name);
                    if (path.endsWith(".svg")) vectors++; else rasters++;
                }
                require(vectors > 0 && rasters > 0 && icons.size() == vectors + rasters, "All attached pack icons imported");
                System.out.println("PASS: Liquid Glass Pack, " + icons.size() + " replacements (" + rasters + " PNG, " + vectors + " SVG)");
                return;
            }
        }
        throw new AssertionError("Attached pack metadata missing");
    }

    private static byte[] pack(KeyPair key, byte[] image, boolean duplicate) throws Exception {
        ByteArrayOutputStream payloadBuffer = new ByteArrayOutputStream();
        DataOutputStream payload = new DataOutputStream(payloadBuffer);
        payload.writeLong(PACK_ID.getMostSignificantBits());
        payload.writeLong(PACK_ID.getLeastSignificantBits());
        payload.writeUTF("Fixture icon pack");
        payload.writeLong(42L);
        payload.writeUTF(AUTHOR);
        payload.writeInt(duplicate ? 2 : 1);
        for (int i = 0; i < (duplicate ? 2 : 1); i++) {
            payload.writeUTF("msg_add");
            payload.writeInt(image.length);
            payload.write(image);
        }
        byte[] body = payloadBuffer.toByteArray();
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(key.getPrivate());
        signer.update(body);
        byte[] signature = signer.sign();
        byte[] publicKey = key.getPublic().getEncoded();
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(file);
        out.writeInt(0x42474943);
        out.writeByte(1);
        out.writeShort(publicKey.length);
        out.write(publicKey);
        out.writeShort(signature.length);
        out.write(signature);
        out.writeInt(body.length);
        out.write(body);
        return file.toByteArray();
    }

    private static void reject(Method read, byte[] bytes, String message) throws Exception {
        try {
            read.invoke(null, new ByteArrayInputStream(bytes));
        } catch (InvocationTargetException error) {
            require(error.getCause() instanceof java.io.IOException, message);
            return;
        }
        throw new AssertionError(message);
    }

    private static int indexOf(byte[] bytes, byte[] needle) {
        outer: for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        throw new AssertionError("Author bytes missing");
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
