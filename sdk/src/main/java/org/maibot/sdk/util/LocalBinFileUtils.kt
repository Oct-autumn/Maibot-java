package org.maibot.sdk.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalBinFileUtils {
    public static final String BASE_DIR = "data/files";

    public static void saveFile(String type, String hash, byte[] data)
    throws IOException {
        var filePath = Path.of(BASE_DIR, String.format("%s.%s", hash, type));

        // 保存文件
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, data);
    }

    public static File getFile(String type, String hash) {
        var filePath = Path.of(BASE_DIR, String.format("%s.%s", hash, type));
        if (Files.exists(filePath)) {
            return filePath.toFile();
        } else {
            return null;
        }
    }
}
