package org.telegram.messenger.plugins;

import java.nio.file.*;
import java.security.*;
import java.util.Base64;
import com.google.gson.*;

public final class PluginSignatureCheck {
    private static void sign(java.io.File directory, KeyPair key) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(key.getPrivate()); signer.update(PluginSignature.payload(directory));
        JsonObject envelope = new JsonObject(); envelope.addProperty("format", 1);
        envelope.addProperty("public_key", Base64.getEncoder().encodeToString(key.getPublic().getEncoded()));
        envelope.addProperty("signature", Base64.getEncoder().encodeToString(signer.sign()));
        Files.write(directory.toPath().resolve("signature.json"), envelope.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        java.io.File root = Files.createTempDirectory("plugin-signature-").toFile();
        try {
            java.io.File content = new java.io.File(root, "source"); content.mkdir();
            Files.copy(Paths.get(args[0]), content.toPath().resolve("manifest.json"));
            Files.write(content.toPath().resolve("main.py"), new byte[]{1, 2});
            assert PluginSignature.verify(content) == null;
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
            KeyPair first = generator.generateKeyPair(), second = generator.generateKeyPair();
            sign(content, first);
            String fingerprint = PluginSignature.verify(content);
            assert fingerprint.equals(PluginSignature.hash(first.getPublic().getEncoded()));
            for (String change : new String[]{"modify", "add", "remove"}) {
                if (change.equals("modify")) Files.write(content.toPath().resolve("main.py"), new byte[]{3});
                if (change.equals("add")) Files.write(content.toPath().resolve("extra.txt"), new byte[]{4});
                if (change.equals("remove")) Files.delete(content.toPath().resolve("main.py"));
                try { PluginSignature.verify(content); throw new AssertionError("tampering accepted: " + change); }
                catch (java.io.IOException expected) { }
                Files.deleteIfExists(content.toPath().resolve("extra.txt")); Files.write(content.toPath().resolve("main.py"), new byte[]{1, 2});
            }
            PluginStore store = new PluginStore(new java.io.File(root, "store"));
            java.lang.reflect.Constructor<PluginPackage> ctor = PluginPackage.class.getDeclaredConstructor(PluginManifest.class, String.class, java.io.File.class); ctor.setAccessible(true);
            PluginManifest manifest = PluginManifest.parse(Files.readAllBytes(Paths.get(args[0])), 1, 72379, 7237);
            PluginPackage initial = ctor.newInstance(manifest, "a".repeat(64), content); initial.signer = fingerprint;
            store.install(initial);
            java.io.File update = new java.io.File(root, "update"); update.mkdir();
            PluginPackage replacement = ctor.newInstance(manifest, "b".repeat(64), update);
            for (String otherKey : new String[]{PluginSignature.hash(second.getPublic().getEncoded()), null}) {
                replacement.signer = otherKey;
                try { store.install(replacement); throw new AssertionError("author key replacement accepted"); }
                catch (java.io.IOException expected) { assert expected.getMessage().equals("AuthorKeyChanged"); }
            }
            assert store.plugins().getAsJsonObject(manifest.id()).get("hash").getAsString().equals("a".repeat(64));
            if (args.length > 1) {
                PluginPackage signed = PluginPackage.stage(Files.newInputStream(Paths.get(args[1])), new java.io.File(root, "cross-language"), 1, 72379, 7237);
                assert signed.signer != null;
                System.out.println("AUTHOR_KEY=" + signed.signer);
            }
        } finally { PluginPackage.delete(root); }
        System.out.println("PASS: RSA package signature, modified/added/removed file denial, changed/missing author key update rejected");
    }
}
