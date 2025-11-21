package org.maibot.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.util.List;

public class CoreBooter {
    private static final Logger log               = LoggerFactory.getLogger("Launcher.CoreBooter");
    // Maibot Core 主类
    private static final String MAIBOT_MAIN_CLASS = "org.maibot.core.MaibotMain";

    public static void launchMaibotCore(URLClassLoader urlClassLoader, List<String> modList) {
        // 构造传递给 Maibot Core 的启动参数
        var coreLaunchArgs = createCoreLaunchArgs(modList);

        log.info("引导 Maibot Core 启动...");
        log.debug("启动参数: {}", coreLaunchArgs);
        System.out.print("\n".repeat(2));

        try {
            // 使用反射调用Maibot Core的主类
            var mainClass = urlClassLoader.loadClass(MAIBOT_MAIN_CLASS);
            var mainMethod = mainClass.getMethod("main", String[].class);
            try {
                // 调用 Maibot Core 的主方法
                mainMethod.invoke(null, (Object) new String[]{coreLaunchArgs});
            } catch (InvocationTargetException e) {
                // Maibot Core 的主方法抛出的错误
                log.error("Maibot Core 运行时发生错误", e.getCause());
                System.exit(1);
            }
        } catch (ReflectiveOperationException e) {
            // 其他反射相关错误
            log.error("启动 Maibot Core 时发生错误，已终止启动", e);
            System.exit(1);
        }
    }

    public static String createCoreLaunchArgs(List<String> modList) {
        var gson = new Gson();
        var rootNode = new JsonObject();

        rootNode.add("mod_list", gson.toJsonTree(modList));

        return gson.toJson(rootNode);
    }
}
