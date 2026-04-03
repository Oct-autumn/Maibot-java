package org.maibot.sdk.util

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object HashUtils {
    @JvmStatic
    fun getSha256Hash(inputStream: InputStream): String {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            val hashBytes = digest.digest()

            // 将字节数组转换为十六进制字符串
            val sb = StringBuilder()
            for (b in hashBytes) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("SHA-256 algorithm not found", e)
        } catch (e: IOException) {
            throw RuntimeException("Error reading input stream", e)
        }
    }

    @JvmStatic
    fun getSha256Hash(data: ByteArray): String {
        try {
            ByteArrayInputStream(data).use { inputStream ->
                return getSha256Hash(inputStream)
            }
        } catch (e: IOException) {
            throw RuntimeException("Error reading byte array", e)
        }
    }
}
