package org.maibot.sdk.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {
    public static String getSha256Hash(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            byte[] hashBytes = digest.digest();

            // 将字节数组转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        } catch (IOException e) {
            throw new RuntimeException("Error reading input stream", e);
        }
    }

    public static String getSha256Hash(byte[] data) {
        try (InputStream inputStream = new java.io.ByteArrayInputStream(data)) {
            return getSha256Hash(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Error reading byte array", e);
        }
    }
}
