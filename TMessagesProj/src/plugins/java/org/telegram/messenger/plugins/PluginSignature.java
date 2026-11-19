package org.telegram.messenger.plugins;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** An author key signs every package file; catalogue approval is a separate trust decision. */
public final class PluginSignature {
    public static final String FILE = "signature.json";
    public static String verify(File directory) throws IOException {
        File signature = new File(directory, FILE);
        if (!signature.exists()) return null;
        try {
            JsonObject envelope = PluginSettingsFile.parseJson(PluginPackage.read(signature), 65536, 3, 20);
            PluginPackage.check(envelope.size() == 3 && envelope.has("format") && envelope.get("format").isJsonPrimitive() && envelope.getAsJsonPrimitive("format").isNumber()
                    && envelope.get("format").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) == 0
                    && envelope.has("public_key") && envelope.getAsJsonPrimitive("public_key").isString()
                    && envelope.has("signature") && envelope.getAsJsonPrimitive("signature").isString(), "InvalidAuthorSignature");
            byte[] keyBytes = android.util.Base64.decode(envelope.get("public_key").getAsString(), android.util.Base64.NO_WRAP);
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
            PluginPackage.check(Arrays.equals(keyBytes, key.getEncoded()), "InvalidAuthorSignature");
            int bits = ((RSAPublicKey) key).getModulus().bitLength();
            PluginPackage.check(bits >= 2048 && bits <= 4096, "InvalidAuthorSignature");
            Signature verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(key); verifier.update(payload(directory));
            PluginPackage.check(verifier.verify(android.util.Base64.decode(envelope.get("signature").getAsString(), android.util.Base64.NO_WRAP)), "InvalidAuthorSignature");
            return hash(keyBytes);
        } catch (IOException error) { throw new IOException("InvalidAuthorSignature", error); }
        catch (Exception error) { throw new IOException("InvalidAuthorSignature", error); }
    }
    static byte[] payload(File directory) throws IOException {
        TreeMap<String, File> files = new TreeMap<>((left, right) -> {
            byte[] a = left.getBytes(StandardCharsets.UTF_8), b = right.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < Math.min(a.length, b.length); i++) {
                int order = Integer.compare(a[i] & 255, b[i] & 255); if (order != 0) return order;
            }
            return Integer.compare(a.length, b.length);
        });
        collect(directory, "", files);
        StringBuilder payload = new StringBuilder("BYGRAM-PLUGIN-SIGNATURE-V1\n");
        for (Map.Entry<String, File> entry : files.entrySet()) {
            byte[] bytes = PluginPackage.read(entry.getValue());
            payload.append(hash(bytes)).append(' ').append(bytes.length).append(' ').append(entry.getKey()).append('\n');
        }
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void collect(File root, String prefix, Map<String, File> result) throws IOException {
        File[] files = root.listFiles(); PluginPackage.check(files != null, "InvalidAuthorSignature");
        for (File file : files) {
            String name = prefix + file.getName();
            if (name.equals(FILE)) continue;
            if (file.isDirectory()) collect(file, name + "/", result);
            else { PluginPackage.check(result.size() < 512, "InvalidAuthorSignature"); result.put(name, file); }
        }
    }
    static String hash(byte[] bytes) throws IOException {
        try {
            StringBuilder hash = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
            return hash.toString();
        } catch (GeneralSecurityException error) { throw new IOException("InvalidAuthorSignature", error); }
    }
}
