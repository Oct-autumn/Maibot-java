package org.maibot.core.config;

import org.maibot.sdk.config.ConfigService;
import org.maibot.sdk.exceptions.FatalError;
import org.maibot.sdk.exceptions.IgnorableException;
import org.maibot.sdk.exceptions.InvalidConfigPath;
import org.maibot.sdk.exceptions.NamespaceAlreadyExist;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.ioc.InitializableComponent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
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
    public static final  String CONFIG_DIR               = "config";
    public static final  String MOD_CONFIG_TEMPLATE_FILE = "config.template.toml";
    private static final String CORE_CONFIG_PATH         = "config.toml";

    /// 访问语法：
    /// - <code>namespcae:field1.field2</code>: 指定命名空间，访问某个字段
    /// - <code>field1.field2</code>: 默认命名空间为 core，访问某个字段
    /// - <code>namespace:*</code>: 直接获取整个命名空间的 JsonNode
    ///
    /// 命名空间由字母、数字、下划线组成，不能包含空格；
    /// 字段路径由字母、数字、下划线和点号组成，不能包含空格。
    private static final Pattern KEY_SPLIT_PATTERN  = Pattern.compile(
      "(?:(?<namespace>[a-zA-Z0-9_]+):)?(?<path>[a-zA-Z0-9_.*]+)$"
    );
    private static final Pattern FIELD_PATH_PATTERN = Pattern.compile(
      "^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)*$"
    );

    private final Map<String, AtomicReference<JsonNode>> namespacedConfigs = new ConcurrentHashMap<>();
    private final ObjectMapper                           objectMapper      = new ObjectMapper();

    @Override
    public void postConstruct() {
        // 确保配置目录存在
        ensureConfigDirExists();
        // 加载core配置
        loadCoreConfig();
    }

    /**
     * 确保配置目录存在
     */
    private void ensureConfigDirExists() {
        File configDir = new File(CONFIG_DIR);
        if (!configDir.exists()) {
            if (!configDir.mkdirs()) {
                throw new FatalError(
                  "Failed to create config directory '%s'. Please check the accessibility and permissions of the parent directory.",
                  CONFIG_DIR
                );
            }
        }
    }

    /**
     * 加载配置文件
     */
    private void loadCoreConfig() {
        File configFile = new File(CORE_CONFIG_PATH);
        if (!configFile.exists()) {
            System.out.println("配置文件不存在，正在创建默认配置文件...");
            try (var inputStream = this.getClass()
                                       .getClassLoader()
                                       .getResourceAsStream("/org/maibot/core/Config.template.toml")) {
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
        var tomlReader = new TomlMapper().readerFor(MainConfig.class)
                                         .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try {
            MainConfig mainConfig = tomlReader.readValue(configFile);

            this.putConfigNameSpace("core", objectMapper.valueToTree(mainConfig));
        } catch (JacksonException e) {
            throw new FatalError("Failed to parse core config file.", e);
        }
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
     * 获取原始的配置 JSON 节点
     * <p>访问语法：
     * <ul>
     * <li><code>namespcae:field1.field2</code>: 指定命名空间，访问某个字段</li>
     * <li><code>field1.field2</code>: 默认命名空间为 core，访问某个字段</li>
     * <li><code>namespace:*</code>: 直接获取整个命名空间的 JsonNode</li>
     * </ul>
     * <p>命名空间由字母、数字、下划线组成，不能包含空格；
     * 字段路径由字母、数字、下划线和点号组成，不能包含空格。
     *
     * @param key 配置键
     * @return 配置 JSON 节点
     * @throws InvalidConfigPath 如果配置路径无效
     */
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
        String path = matcher.group("path");
        if (namespace == null || namespace.isBlank()) {
            namespace = "core";
        }

        AtomicReference<JsonNode> namespaceRef = namespacedConfigs.get(namespace);
        if (namespaceRef == null) {
            throw new InvalidConfigPath("Namespace '%s' not found.", namespace);
        }

        if (path.equals("*")) {
            // 直接返回整个命名空间
            return namespaceRef.get();
        } else if (!FIELD_PATH_PATTERN.matcher(path).matches()) {
            throw new InvalidConfigPath(
              "Invalid field path format: '%s'. Expected format like 'field1.field2'.",
              path
            );
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

    /**
     * 移除一个配置命名空间
     *
     * @param namespace 命名空间
     */
    public void removeConfigNameSpace(String namespace) {
        namespacedConfigs.remove(namespace);
    }

    /**
     * 加载配置文件
     *
     * @param namespace           命名空间
     * @param configClass         配置类
     * @param configFileName      配置文件路径
     * @param templateInputStream 模板文件路径
     * @return 是否成功加载配置
     */
    public <T> boolean loadExtraConfig(
      String namespace,
      Class<T> configClass,
      String configFileName,
      InputStream templateInputStream
    ) {
        var configFilePath = Path.of(CONFIG_DIR, configFileName).toString();
        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            this.createDefaultConfig(configFilePath, templateInputStream);
            return false;
        }

        // 根据配置文件的文件名后缀选择解析器
        if (configFile.getName().endsWith(".toml")) {
            var tomlReader = new TomlMapper().readerFor(configClass)
                                             .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            try {
                T rawJsonMap = tomlReader.readValue(configFile);

                this.putConfigNameSpace(namespace, objectMapper.valueToTree(rawJsonMap));
            } catch (JacksonException e) {
                throw new FatalError("Failed to parse config file '%s'.", configFilePath, e);
            }
        } else {
            throw new FatalError("Unsupported config file format: '%s'. Only .toml is supported.", configFilePath);
        }

        return true;
    }

    @Override
    public <T> T getConfig(String key, Class<T> clazz)
    throws InvalidConfigPath {
        JsonNode rawJson = getRawJson(key);
        try {
            return this.objectMapper.treeToValue(rawJson, clazz);
        } catch (JacksonException e) {
            throw new InvalidConfigPath("Failed to convert config value to class %s.", clazz.getName(), e);
        }
    }


}
