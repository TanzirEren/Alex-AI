package com.alex.assistant;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM encryption backed by the Android Keystore. Values never touch logs. */
public class SecureStore {
    private static final String ALIAS = "alex_secure_key";

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    static String get(Context c, String name) {
        String v = Prefs.p(c).getString("sec_" + name, "");
        if (v.isEmpty()) return "";
        try {
            String[] s = v.split(":");
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(s[0], 0)));
            return new String(ci.doFinal(Base64.decode(s[1], 0)), "UTF-8");
        } catch (Exception e) { return ""; }
    }

    static void put(Context c, String name, String val) {
        if (val.isEmpty()) { Prefs.p(c).edit().remove("sec_" + name).apply(); return; }
        try {
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.ENCRYPT_MODE, key());
            byte[] ct = ci.doFinal(val.getBytes("UTF-8"));
            Prefs.p(c).edit().putString("sec_" + name,
                Base64.encodeToString(ci.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)).apply();
        } catch (Exception ignored) {}
    }
}
