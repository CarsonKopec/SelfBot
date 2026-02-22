package com.github.imagineforgee.selfbotlib.voice;

import org.bouncycastle.crypto.modes.ChaCha20Poly1305;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

public class SodiumEncryption {

    public static byte[] encrypt(byte[] plaintext, byte[] aad, byte[] nonce24, byte[] key) {
        try {
            byte[] subkey = hChaCha20(key, nonce24);

            byte[] nonce12 = new byte[12];
            System.arraycopy(nonce24, 16, nonce12, 4, 8);

            ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
            AEADParameters params = new AEADParameters(new KeyParameter(subkey), 128, nonce12, aad);
            cipher.init(true, params);

            byte[] output = new byte[cipher.getOutputSize(plaintext.length)];
            int len = cipher.processBytes(plaintext, 0, plaintext.length, output, 0);
            cipher.doFinal(output, len);
            return output;

        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    private static byte[] hChaCha20(byte[] key, byte[] nonce) {
        int[] s = new int[16];
        s[0] = 0x61707865; s[1] = 0x3320646e; s[2] = 0x79622d32; s[3] = 0x6b206574;
        for (int i = 0; i < 8; i++) s[4 + i] = leInt(key, i * 4);
        s[12] = leInt(nonce, 0);
        s[13] = leInt(nonce, 4);
        s[14] = leInt(nonce, 8);
        s[15] = leInt(nonce, 12);

        int[] w = s.clone();
        for (int i = 0; i < 10; i++) {
            qr(w, 0, 4, 8,  12); qr(w, 1, 5, 9,  13);
            qr(w, 2, 6, 10, 14); qr(w, 3, 7, 11, 15);
            qr(w, 0, 5, 10, 15); qr(w, 1, 6, 11, 12);
            qr(w, 2, 7, 8,  13); qr(w, 3, 4, 9,  14);
        }

        byte[] out = new byte[32];
        for (int i = 0; i < 4; i++) writeLeInt(out, i * 4, w[i]);
        for (int i = 0; i < 4; i++) writeLeInt(out, 16 + i * 4, w[12 + i]);
        return out;
    }

    private static void qr(int[] s, int a, int b, int c, int d) {
        s[a] += s[b]; s[d] ^= s[a]; s[d] = Integer.rotateLeft(s[d], 16);
        s[c] += s[d]; s[b] ^= s[c]; s[b] = Integer.rotateLeft(s[b], 12);
        s[a] += s[b]; s[d] ^= s[a]; s[d] = Integer.rotateLeft(s[d],  8);
        s[c] += s[d]; s[b] ^= s[c]; s[b] = Integer.rotateLeft(s[b],  7);
    }

    private static int leInt(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off+1] & 0xFF) << 8)
                | ((b[off+2] & 0xFF) << 16) | ((b[off+3] & 0xFF) << 24);
    }

    private static void writeLeInt(byte[] b, int off, int v) {
        b[off] = (byte)v; b[off+1] = (byte)(v>>>8);
        b[off+2] = (byte)(v>>>16); b[off+3] = (byte)(v>>>24);
    }
}