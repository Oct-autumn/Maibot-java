package org.maibot.sdk.util

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

object LocalBinFileUtils {
    const val BASE_DIR: String = "data/files"

    @JvmStatic
    @Throws(IOException::class)
    fun saveFile(type: String, hash: String, data: ByteArray) {
        val filePath = Path.of(BASE_DIR, String.format("%s.%s", hash, type))

        // 保存文件
        Files.createDirectories(filePath.parent)
        Files.write(filePath, data)
    }

    @JvmStatic
    fun getFile(type: String, hash: String): File? {
        return Path.of(BASE_DIR, String.format("%s.%s", hash, type))
            .takeIf { it.exists() }?.toFile()
    }
}
