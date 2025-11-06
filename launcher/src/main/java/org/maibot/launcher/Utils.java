package org.maibot.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Utils {
    private static final Logger log = LoggerFactory.getLogger("Launcher.Utils");

    static List<File> findFiles(Path dirPath, String namePattern) {
        var workDir = new File(dirPath.toUri());
        if (!workDir.exists()) {
            log.error("目录不存在: {}", dirPath);
            System.exit(1);
        } else if (!workDir.isDirectory()) {
            log.error("指定路径不是目录: {}", dirPath);
            System.exit(1);
        }

        Pattern pattern = Pattern.compile(namePattern);

        List<File> matchedFiles = new ArrayList<>();
        // 列出目录下的所有文件
        File[] listOfFiles = workDir.listFiles();
        if (listOfFiles == null) {
            return matchedFiles;
        } else {
            for (File file : listOfFiles) {
                if (file.isFile() && pattern.matcher(file.getName()).matches()) {
                    matchedFiles.add(file);
                }
            }
        }

        return matchedFiles;
    }

    public static void ensureDirExists(Path dirPath) {
        var dirFile = new File(dirPath.toUri());
        if (!dirFile.exists()) {
            if (dirFile.mkdirs()) {
                log.info("创建目录: {}", dirPath);
            } else {
                log.error("创建目录失败: {}", dirPath);
                System.exit(1);
            }
        } else if (!dirFile.isDirectory()) {
            log.error("指定路径已存在文件: {}", dirPath);
            System.exit(1);
        }
    }

    public static String calculateMD5(InputStream inputStream) {
        try {
            var md = java.security.MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            return byteArrayToHexString(digest);
        } catch (Exception e) {
            log.error("MD5计算失败", e);
            return null;
        }
    }

    public static String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    public static void copyFile(File file, File destFile) {
        try (var inStream = new java.io.FileInputStream(file);
             var outStream = new java.io.FileOutputStream(destFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            log.error("文件复制失败: {} -> {}", file.getAbsolutePath(), destFile.getAbsolutePath(), e);
        }
    }
}
