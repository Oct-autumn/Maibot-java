package org.maibot.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moandjiezana.toml.Toml;
import org.maibot.core.util.JsonValidator;
import org.maibot.sdk.exceptions.FatalError;
import org.maibot.sdk.exceptions.IgnorableException;
import org.maibot.sdk.exceptions.InvalidConfigPath;
import org.maibot.sdk.exceptions.NotInitialized;
import org.maibot.sdk.ioc.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

// TODO: 支持热重载配置文件
// TODO: 支持版本合并
@Component
public class ConfigService {
    private static final String CONFIG_PATH = "config.toml";

    /// Config 的原子引用，确保线程安全
    private final AtomicReference<MainConfig> configRef    = new AtomicReference<>();
    private final AtomicReference<JsonObject> rawConfigRef = new AtomicReference<>();

    private ConfigService() {
        this.load();
    }

    /**
     * 创建默认的配置文件
     */
    private void createDefaultConfig() {
        try (InputStream input = getClass().getResourceAsStream("/org/maibot/core/Config.template.toml")) {
            assert input != null;

            File configFile = new File(CONFIG_PATH);
            if (configFile.exists()) {
                throw new IgnorableException("Config file already exists!");
            }
            Files.copy(input, configFile.toPath());
        } catch (IOException e) {
            throw new FatalError("Failed to create default config file: " + CONFIG_PATH, e);
        }
    }

    /**
     * 加载配置文件
     */
    public void load() {
        File configFile = new File(CONFIG_PATH);
        if (!configFile.exists()) {
            System.out.println("配置文件不存在，正在创建默认配置文件...");
            createDefaultConfig();
            System.out.println("默认配置文件创建成功，请根据需要修改 " + CONFIG_PATH + " 后重新启动程序。");
            System.exit(-1);
        }

        Toml configToml;
        try {
            configToml = new Toml().read(new File(CONFIG_PATH));
        } catch (IllegalStateException e) {
            throw new FatalError("Failed to parse config file.", e);
        }

        var gson = new Gson();

        var rawJsonMap = gson.toJsonTree(configToml.toMap());

        // 检查是否缺失字段
        var missingField = JsonValidator.validateJsonAgainstClass(rawJsonMap.getAsJsonObject(), MainConfig.class);
        if (!missingField.isEmpty()) {
            throw new FatalError("Missing fields in config file: %s", String.join(", ", missingField));
        }

        // 反序列化为 MainConfig 对象
        MainConfig config = gson.fromJson(rawJsonMap, MainConfig.class);

        configRef.set(config);
        rawConfigRef.set(gson.toJsonTree(config, MainConfig.class).getAsJsonObject());
    }

    /**
     * 获取配置对象
     *
     * @return 配置对象
     */
    public MainConfig get() {
        if (configRef.get() == null) {
            throw new NotInitialized("Config not loaded. Please call load() before accessing the config.");
        }

        return configRef.get();
    }

    /**
     * 从原始 JSON 配置中获取指定路径的值
     *
     * @param path  配置路径，使用点号分隔
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 指定路径的配置值
     * @throws InvalidConfigPath 如果路径无效
     */
    public <T> T getFromRaw(String path, Class<T> clazz)
    throws InvalidConfigPath {
        if (rawConfigRef.get() == null) {
            throw new NotInitialized("Config not loaded. Please call load() before accessing the config.");
        }

        String[] keys = path.split("\\.");
        JsonElement current = rawConfigRef.get();
        for (String key : keys) {
            if (current.isJsonObject()) {
                JsonObject obj = current.getAsJsonObject();
                if (obj.has(key)) {
                    current = obj.get(key);
                    continue;
                }
            }
            throw new InvalidConfigPath("Path %s not found in config.", path);
        }
        return new Gson().fromJson(current, clazz);
    }
}
