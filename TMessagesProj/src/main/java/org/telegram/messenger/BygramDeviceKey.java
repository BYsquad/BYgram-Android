package org.telegram.messenger;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

final class BygramDeviceKey {
    private static final String STORE = "AndroidKeyStore";

    private BygramDeviceKey() {}

    static String publicKey(long userId) throws Exception {
        KeyStore store = load();
        String alias = alias(userId);
        if (!store.containsAlias(alias)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, STORE);
            generator.initialize(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build());
            generator.generateKeyPair();
        }
        return Base64.encodeToString(store.getCertificate(alias).getPublicKey().getEncoded(), Base64.NO_WRAP);
    }

    static String sign(long userId, String value) throws Exception {
        publicKey(userId);
        PrivateKey key = (PrivateKey) load().getKey(alias(userId), null);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(key);
        signature.update(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP);
    }

    static void delete(long userId) {
        try {
            KeyStore store = load();
            if (store.containsAlias(alias(userId))) store.deleteEntry(alias(userId));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static KeyStore load() throws Exception {
        KeyStore store = KeyStore.getInstance(STORE);
        store.load(null);
        return store;
    }

    private static String alias(long userId) {
        return "bygram_full_device_v1_" + userId;
    }
}
