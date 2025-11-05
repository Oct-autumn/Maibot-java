package org.maibot.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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
}
