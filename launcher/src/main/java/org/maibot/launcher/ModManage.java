package org.maibot.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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
        var workDirMods = Utils.findFiles(Path.of(LAUNCHER_WORK_DIR, "mods"), ".*\\.jar");
        var runDirMods = Utils.findFiles(Path.of("mods"), ".*\\.jar");

        Set<ModFile> workDirModSet = workDirMods.stream()
                                                .map(ModFile::getIt)
                                                .collect(Collectors.toSet());
        Set<ModFile> runDirModSet = runDirMods.stream()
                                              .map(ModFile::getIt)
                                              .collect(Collectors.toSet());

        // 计算新增和删除的Mod文件
        Set<ModFile> modsToAdd = runDirModSet.stream()
                                             .filter(mod -> !workDirModSet.contains(mod))
                                             .collect(Collectors.toSet());
        Set<ModFile> modsToRemove = workDirModSet.stream()
                                                 .filter(mod -> !runDirModSet.contains(mod))
                                                 .collect(Collectors.toSet());

        // 处理新增的Mod文件
        for (ModFile mod : modsToAdd) {
            var sourceFilePath = mod.file.getPath();
            var targetFile = Path.of(LAUNCHER_WORK_DIR, "mods", mod.md5 + ".jar");
            try {
                Files.copy(Path.of(sourceFilePath), targetFile);
                log.info("已添加Mod: {}", mod.file.getName());
            } catch (IOException e) {
                log.error("添加Mod失败: {}", mod.file.getName(), e);
            }
        }

        // 处理删除的Mod文件
        for (ModFile mod : modsToRemove) {
            var targetFilePath = mod.file.toPath();
            try {
                Files.deleteIfExists(targetFilePath);
                log.info("已删除Mod: {}", mod.file.getName());
            } catch (IOException e) {
                log.error("删除Mod失败: {}", mod.file.getName(), e);
            }
        }

        // 更新运行目录的Mod文件列表
        workDirModSet.removeAll(modsToRemove);
        workDirModSet.addAll(modsToAdd);

        return workDirModSet.stream()
                            .map(mod -> mod.file.getAbsolutePath())
                            .collect(Collectors.toList());
    }

    private record ModFile(
      File file,
      String md5
    ) {
        public static ModFile getIt(File file) {
            try {
                var fileStream = new FileInputStream(file);
                return new ModFile(file, Utils.calculateMD5(fileStream));
            } catch (FileNotFoundException ignored) {
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
