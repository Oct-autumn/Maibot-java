package org.maibot.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.maibot.launcher.LauncherMain.LAUNCHER_WORK_DIR;

/**
 * 管理Mod文件
 *
 * @author OctAutumn
 */
public class ModManage {
    private static final Logger log = LoggerFactory.getLogger("Launcher.ModManage");

    /**
     * 同步工作目录和运行目录下的Mod文件
     * <p>
     * 维护工作目录（workDir）下的mods文件列表（./.maibot-launcher/mods），比较它与运行目录（runDir）下的mods文件列表(./mods)的差异。<br>
     * 运行目录新增的Mod文件会被复制到运行目录下的mods目录中，删除的Mod文件会从运行目录下的mods目录中移除。文件的识别依据MD5值。
     *
     * @return 返回处理完后运行目录下所有Mod文件的绝对路径列表
     */
    public static List<String> syncMods() {
        var workDirModFiles = Utils.findFiles(Path.of(LAUNCHER_WORK_DIR, "mods"), ".*\\.jar");
        var runDirModFiles = Utils.findFiles(Path.of("mods"), ".*\\.jar");

        var workDirMods = createMd5Map(workDirModFiles);
        var runDirMods = createMd5Map(runDirModFiles);

        // 删除Mod文件
        for (var workDirModMd5 : workDirMods.keySet()) {
            if (!runDirMods.containsKey(workDirModMd5)) {
                // 运行目录中不存在该Mod文件，进行删除
                var modFile = workDirMods.get(workDirModMd5).file;
                if (modFile.delete()) {
                    log.debug("Deleted mod file: {}", modFile.getAbsolutePath());
                    workDirMods.remove(workDirModMd5);
                } else {
                    log.warn("Failed to delete mod file: {}", modFile.getName());
                }
            }
        }

        // 新增Mod文件
        for (var runDirModKey : runDirMods.keySet()) {
            if (!workDirMods.containsKey(runDirModKey)) {
                // 运行目录中不存在该Mod文件，进行复制
                var srcFile = runDirMods.get(runDirModKey).file;
                var destFile = Path.of(LAUNCHER_WORK_DIR, "mods", srcFile.getName()).toFile();
                try {
                    Utils.copyFile(srcFile, destFile);
                    log.debug("Copied mod file: {} -> {}", srcFile.getAbsolutePath(), destFile.getAbsolutePath());
                    workDirMods.put(runDirModKey, ModFile.getIt(destFile));
                } catch (Exception e) {
                    log.error("Failed to copy mod file: {}", srcFile.getName(), e);
                }
            }
        }

        // 更新运行目录的Mod文件列表
        return workDirMods.values().stream().map(mod -> mod.file.getAbsolutePath()).collect(Collectors.toList());
    }

    private static Map<String, ModFile> createMd5Map(List<File> runDirMods) {
        return runDirMods.stream().map(ModFile::getIt).filter(Objects::nonNull).collect(Collectors.toMap(
          modFile -> modFile.md5, modFile -> modFile, (existing, replacement) -> {
              throw new IllegalStateException("Duplicate MD5 found: " + existing.md5 + " for files " + existing.file.getAbsolutePath() + " and " + replacement.file.getAbsolutePath());
          }
        ));
    }

    private record ModFile(
      File file,
      String md5
    ) {
        public static ModFile getIt(File file) {
            try (var fileStream = new FileInputStream(file)) {
                return new ModFile(file, Utils.calculateMD5(fileStream));
            } catch (IOException ignore) {
                // 理论上不会发生（因为是从已存在的文件列表中获取的）
                // 不存在的文件直接忽略
                return null;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ModFile other = (ModFile) obj;
            return md5.equals(other.md5);
        }

        @Override
        public int hashCode() {
            return md5.hashCode();
        }
    }
}
