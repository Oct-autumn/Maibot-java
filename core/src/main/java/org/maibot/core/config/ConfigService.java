package org.maibot.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moandjiezana.toml.Toml;
import org.maibot.core.cdi.annotation.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

// TODO: 支持热重载配置文件
// TODO: 支持版本合并
@Component
public class ConfigService {
    private static final String CONFIG_PATH = "config.toml";

    /// Config 的原子引用，确保线程安全
    private final AtomicReference<MainConfig> configRef = new AtomicReference<>();
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
                throw new RuntimeException("Config file already exists!");
            }
            Files.copy(input, configFile.toPath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create default config file: " + CONFIG_PATH, e);
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
        try {
            Toml configToml = new Toml().read(new File(CONFIG_PATH));
            MainConfig config = configToml.to(MainConfig.class);
            configRef.set(config);
            rawConfigRef.set(new Gson().toJsonTree(config, MainConfig.class).getAsJsonObject());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config file: " + CONFIG_PATH, e);
        }
    }

    public MainConfig get() {
        if (configRef.get() == null) {
            throw new IllegalStateException("Config not loaded. Please call load() before accessing the config.");
        }

        return configRef.get();
    }

    public <T> T getFromRaw(String path, Class<T> clazz) {
        if (rawConfigRef.get() == null) {
            throw new IllegalStateException("Config not loaded. Please call load() before accessing the config.");
        }

        String[] keys = path.split("\\.");
        JsonElement current = rawConfigRef.get();
        for (String key : keys) {
            if (current.isJsonObject()) {
                JsonObject obj = current.getAsJsonObject();
                if (obj.has(key)) {
                    current = obj.get(key);
                } else {
                    throw new IllegalArgumentException("Path not found in config: " + path);
                }
            } else {
                throw new IllegalArgumentException("Invalid path in config: " + path);
            }
        }
        return new Gson().fromJson(current, clazz);
    }
}
