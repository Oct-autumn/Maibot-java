package org.maibot.core.config;

import org.maibot.sdk.config.ConfigService;
import org.maibot.sdk.exceptions.FatalError;
import org.maibot.sdk.exceptions.IgnorableException;
import org.maibot.sdk.exceptions.InvalidConfigPath;
import org.maibot.sdk.exceptions.NamespaceAlreadyExist;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.ioc.InitializableComponent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.toml.TomlMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

// TODO: 支持热重载配置文件
// TODO: 支持版本合并
@Component
public class ConfigServiceImpl implements ConfigService, InitializableComponent {
    public static final  String  CONFIG_DIR               = "config";
    public static final  String  MOD_CONFIG_TEMPLATE_FILE = "config.template.toml";
    private static final String  CORE_CONFIG_PATH         = "config.toml";
    private static final Pattern KEY_SPLIT_PATTERN        = Pattern.compile(
      "^((?<namespace>[0-9a-zA-Z-_]+):)?(?<path>[0-9a-zA-Z_.]+)$");

    private final Map<String, AtomicReference<JsonNode>> namespacedConfigs = new ConcurrentHashMap<>();

    @Override
    public void postConstruct() {
        // 加载core配置
        loadCoreConfig();
    }

    /**
     * 存储一个配置命名空间
     *
     * @param namespace      命名空间
     * @param configJsonNode 配置 JSON 对象
     * @throws NamespaceAlreadyExist 如果命名空间已存在
     */
    public void putConfigNameSpace(String namespace, JsonNode configJsonNode)
    throws NamespaceAlreadyExist {
        var ref = new AtomicReference<>(configJsonNode);
        var existing = namespacedConfigs.putIfAbsent(namespace, ref);
        if (existing != null) {
            throw new NamespaceAlreadyExist("Namespace %s already exists.", namespace);
        }
    }

    /**
     * 移除一个配置命名空间
     *
     * @param namespace 命名空间
     */
    public void removeConfigNameSpace(String namespace) {
        namespacedConfigs.remove(namespace);
    }

    @Override
    public JsonNode getRawJson(String key)
    throws InvalidConfigPath {
        var matcher = KEY_SPLIT_PATTERN.matcher(key);
        if (!matcher.matches()) {
            throw new InvalidConfigPath(
              "Invalid config key format: '%s'. Expected format like '${namespace:field1.field2}' or '${field1.field2}'.",
              key
            );
        }
        String namespace = matcher.group("namespace");
        if (namespace == null || namespace.isBlank()) {
            namespace = "core";
        }
        String path = matcher.group("path");

        AtomicReference<JsonNode> namespaceRef = namespacedConfigs.get(namespace);
        if (namespaceRef == null) {
            throw new InvalidConfigPath("Namespace '%s' not found.", namespace);
        }
        String[] keys = path.split("\\.");
        JsonNode current = namespaceRef.get();
        for (String k : keys) {
            if (current.has(k)) {
                current = current.get(k);
                continue;
            }
            throw new InvalidConfigPath("Path '%s' not found in namespace '%s'.", path, namespace);
        }
        return current;
    }

    @Override
    public <T> T getConfig(String key, Class<T> clazz)
    throws InvalidConfigPath {
        JsonNode rawJson = getRawJson(key);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.treeToValue(rawJson, clazz);
        } catch (JacksonException e) {
            throw new InvalidConfigPath("Failed to convert config value to class %s.", clazz.getName(), e);
        }
    }

    /**
     * 创建默认的配置文件
     */
    private void createDefaultConfig(String configFilePath, InputStream templateInputStream) {
        try {
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                // 调用前应检查文件是否存在，这里抛出异常以防万一
                throw new IgnorableException("Config file '%s' already exists.", configFilePath);
            }
            Files.copy(templateInputStream, configFile.toPath());
        } catch (IOException e) {
            throw new FatalError(
              "Failed to create default config file '%s'. Please check the accessibility and permissions of the config directory.",
              CORE_CONFIG_PATH,
              e
            );
        }
    }

    /**
     * 加载配置文件
     */
    private void loadCoreConfig() {
        File configFile = new File(CORE_CONFIG_PATH);
        if (!configFile.exists()) {
            System.out.println("配置文件不存在，正在创建默认配置文件...");
            try (var inputStream = this.getClass().getClassLoader().getResourceAsStream(
              "/org/maibot/core/Config.template.toml"
            )) {
                if (inputStream == null) {
                    throw new FatalError("Default core config template not found in resources.");
                }
                this.createDefaultConfig(CORE_CONFIG_PATH, inputStream);
            } catch (IOException e) {
                throw new FatalError("Failed to create default core config file.", e);
            }
            System.out.println("默认配置文件创建成功，请根据需要修改 " + CORE_CONFIG_PATH + " 后重新启动程序。");
            System.exit(1);
        }

        // 读取Toml配置
        var tomlMapper = new TomlMapper();
        try (var tomlParser = tomlMapper.createParser(new File(CORE_CONFIG_PATH))) {
            JsonNode rawJsonMap = tomlMapper.readTree(tomlParser);

            // 检查是否为JsonObject
            if (!rawJsonMap.isObject()) {
                throw new FatalError("Core config root must be a JSON object.");
            }

            // 数据验证
            try {
                var objectMapper = new ObjectMapper();
                objectMapper.treeToValue(rawJsonMap, MainConfig.class);
            } catch (JacksonException e) {
                throw new FatalError("Core config validation failed.", e);
            }

            this.putConfigNameSpace("core", rawJsonMap);
        } catch (IllegalStateException e) {
            throw new FatalError("Failed to parse core config file.", e);
        }
    }

    /**
     * 加载配置文件
     *
     * @param namespace           命名空间
     * @param configClass         配置类
     * @param configFilePath      配置文件路径
     * @param templateInputStream 模板文件路径
     * @return 是否成功加载配置
     */
    public boolean loadExtraConfig(
      String namespace,
      Class<?> configClass,
      String configFilePath,
      InputStream templateInputStream
    ) {
        File configFile = new File(Path.of(CONFIG_DIR, configFilePath).toString());
        if (!configFile.exists()) {
            this.createDefaultConfig(configFilePath, templateInputStream);
            return false;
        }

        // 根据配置文件的文件名后缀选择解析器
        if (configFilePath.endsWith(".toml")) {
            var tomlMapper = new TomlMapper();
            try (var tomlParser = tomlMapper.createParser(new File(configFilePath))) {
                JsonNode rawJsonMap = tomlMapper.readTree(tomlParser);

                // 检查是否为JsonObject
                if (!rawJsonMap.isObject()) {
                    throw new FatalError("Config file '%s' root must be a JSON object.", configFilePath);
                }

                // 数据验证
                try {
                    var objectMapper = new ObjectMapper();
                    objectMapper.treeToValue(rawJsonMap, configClass);
                } catch (JacksonException e) {
                    throw new FatalError("Config file '%s' validation failed.", configFilePath, e);
                }

                this.putConfigNameSpace(namespace, rawJsonMap);
            } catch (JacksonException e) {
                throw new FatalError("Failed to parse config file '%s'.", configFilePath, e);
            }
        }

        return true;
    }
}
