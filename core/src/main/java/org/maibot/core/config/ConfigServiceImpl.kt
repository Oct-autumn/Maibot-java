package org.maibot.core.config

import org.maibot.sdk.config.ConfigService
import org.maibot.sdk.config.ModelApiConfig
import org.maibot.sdk.exceptions.FatalError
import org.maibot.sdk.exceptions.InvalidConfigPath
import org.maibot.sdk.exceptions.NamespaceAlreadyExist
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.InitializableComponent
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.toml.TomlMapper
import tools.jackson.module.kotlin.KotlinModule
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import kotlin.system.exitProcess

// TODO: 支持热重载配置文件
// TODO: 支持版本合并
@Component
class ConfigServiceImpl : ConfigService, InitializableComponent {
    private val namespacedConfigs = ConcurrentHashMap<String, AtomicReference<JsonNode>>()
    private val jsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    override fun postConstruct() {
        // 确保配置目录存在
        ensureConfigDirExists()
        if (!(loadCoreConfig() && loadModelConfig())) {
            // 如果核心配置或模型配置加载失败，说明默认配置文件已创建但未修改，提示用户修改后重启程序
            exitProcess(-1)
        }
    }

    /**
     * 确保配置目录存在
     */
    private fun ensureConfigDirExists() {
        val configDir = File(CONFIG_DIR)
        if (!configDir.exists()) {
            if (!configDir.mkdirs()) {
                throw FatalError(
                    "Failed to create config directory '%s'. Please check the accessibility and permissions of the parent directory.",
                    CONFIG_DIR
                )
            }
        }
    }

    /**
     * 加载核心配置文件
     */
    private fun loadCoreConfig(): Boolean {
        val configFile = File(CORE_CONFIG_PATH)
        if (!configFile.exists()) {
            println("配置文件不存在，正在创建默认配置文件...")
            createDefaultConfig(CORE_CONFIG_PATH, "/org/maibot/core/config_template/config.template.toml")
            println("默认配置文件创建成功，请根据需要修改 $CORE_CONFIG_PATH 后重新启动程序。")
            return false
        }

        // 读取Toml配置
        val tomlReader =
            TomlMapper.builder()
                .addModule(KotlinModule.Builder().build())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build()
                .readerFor(CoreConfig::class.java)
        try {
            val coreConfig = tomlReader.readValue<CoreConfig>(configFile)
            this.putConfigNameSpace("core", jsonMapper.valueToTree(coreConfig))
        } catch (e: JacksonException) {
            throw FatalError("Failed to parse core config file.", e)
        }

        return true
    }

    /**
     * 加载模型配置文件
     */
    private fun loadModelConfig(): Boolean {
        val configFile = File(MODEL_CONFIG_PATH)
        if (!configFile.exists()) {
            println("Model配置文件不存在，正在创建默认LLM配置文件...")
            createDefaultConfig(MODEL_CONFIG_PATH, "/org/maibot/core/config_template/model_config.template.toml")
            println("默认配置文件创建成功，请根据需要修改 $MODEL_CONFIG_PATH 后重新启动程序。")
            return false
        }

        // 读取Toml配置
        val tomlReader =
            TomlMapper.builder()
                .addModule(KotlinModule.Builder().build())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build()
                .readerFor(ModelApiConfig::class.java)
        try {
            val modelApiConfig = tomlReader.readValue<ModelApiConfig>(configFile)
            this.putConfigNameSpace("choosableModels", jsonMapper.valueToTree(modelApiConfig))
        } catch (e: JacksonException) {
            throw FatalError("Failed to parse choosableModels config file.", e)
        }

        return true
    }

    /**
     * 存储一个配置命名空间
     * 
     * @param namespace      命名空间
     * @param configJsonNode 配置 JSON 对象
     * @throws NamespaceAlreadyExist 如果命名空间已存在
     */
    @Throws(NamespaceAlreadyExist::class)
    fun putConfigNameSpace(namespace: String, configJsonNode: JsonNode) {
        val ref = AtomicReference(configJsonNode)
        namespacedConfigs.putIfAbsent(namespace, ref)?.run {
            throw NamespaceAlreadyExist("Namespace %s already exists.", namespace)
        }
    }

    /**
     * 创建默认的配置文件
     * 
     * @param configFilePath       配置文件路径
     * @param templateResourcePath 模板资源路径
     */
    private fun createDefaultConfig(configFilePath: String, templateResourcePath: String) {
        try {
            javaClass.getResourceAsStream(templateResourcePath).use { inputStream ->
                if (inputStream == null) {
                    throw FatalError("Config template '%s' not found in resources.", templateResourcePath)
                }
                createDefaultConfig(configFilePath, inputStream)
            }
        } catch (e: IOException) {
            throw FatalError(
                "Failed to create default config file '%s'. Please check the accessibility and permissions of the config directory.",
                configFilePath,
                e
            )
        }
    }

    /**
     * 创建默认的配置文件
     * 
     * @param configFilePath      配置文件路径
     * @param templateInputStream 模板输入流
     */
    private fun createDefaultConfig(configFilePath: String, templateInputStream: InputStream) {
        try {
            val configFile = File(configFilePath)
            if (!configFile.exists()) {
                Files.copy(templateInputStream, configFile.toPath())
            }
        } catch (e: IOException) {
            throw FatalError(
                "Failed to create default config file '%s'. Please check the accessibility and permissions of the config directory.",
                configFilePath,
                e
            )
        }
    }

    /**
     * 移除一个配置命名空间
     * 
     * @param namespace 命名空间
     */
    fun removeConfigNameSpace(namespace: String) {
        namespacedConfigs.remove(namespace)
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
    fun <T> loadExtraConfig(
        namespace: String, configClass: Class<T>, configFileName: String, templateInputStream: InputStream
    ): Boolean {
        val configFilePath = Path.of(CONFIG_DIR, configFileName).toString()
        val configFile = File(configFilePath)
        if (!configFile.exists()) {
            this.createDefaultConfig(configFilePath, templateInputStream)
            return false
        }

        // 根据配置文件的文件名后缀选择解析器
        if (configFile.getName().endsWith(".toml")) {
            val tomlReader =
                TomlMapper.builder()
                    .addModule(KotlinModule.Builder().build())
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build()
                    .readerFor(configClass)
            try {
                val rawJsonMap = tomlReader.readValue<T>(configFile)
                this.putConfigNameSpace(namespace, jsonMapper.valueToTree(rawJsonMap))
            } catch (e: JacksonException) {
                throw FatalError("Failed to parse config file '%s'.", configFilePath, e)
            }
        } else {
            throw FatalError("Unsupported config file format: '%s'. Only .toml is supported.", configFilePath)
        }

        return true
    }

    /**
     * 获取原始的配置 JSON 节点
     * 
     * 访问语法：
     * 
     *  * `namespcae:field1.field2`: 指定命名空间，访问某个字段
     *  * `field1.field2`: 默认命名空间为 core，访问某个字段
     *  * `namespace:*`: 直接获取整个命名空间的 JsonNode
     * 
     * 
     * 命名空间由字母、数字、下划线组成，不能包含空格；
     * 字段路径由字母、数字、下划线和点号组成，不能包含空格。
     * 
     * @param key 配置键
     * @return 配置 JSON 节点
     * @throws InvalidConfigPath 如果配置路径无效
     */
    @Throws(InvalidConfigPath::class)
    override fun getRawJson(key: String): JsonNode {
        val matcher = KEY_SPLIT_PATTERN.matcher(key)
        if (!matcher.matches()) {
            throw InvalidConfigPath(
                $$"Invalid config key format: '%s'. Expected format like '${namespace:field1.field2}' or '${field1.field2}'.",
                key
            )
        }

        val namespace = matcher.group("namespace").orEmpty().ifBlank { "core" }
        val path = matcher.group("path")

        val namespaceRef: AtomicReference<JsonNode> =
            namespacedConfigs[namespace] ?: throw InvalidConfigPath("Namespace '%s' not found.", namespace)

        if (path == "*") {
            // 直接返回整个命名空间
            return namespaceRef.get()
        } else if (!FIELD_PATH_PATTERN.matcher(path).matches()) {
            throw InvalidConfigPath(
                "Invalid field path format: '%s'. Expected format like 'field1.field2'.", path
            )
        }

        val keys = path.split("\\.".toRegex()).filter { it.isNotEmpty() }
        var current = namespaceRef.get()
        for (k in keys) {
            if (current.has(k)) {
                current = current.get(k)
                continue
            }
            throw InvalidConfigPath("Path '%s' not found in namespace '%s'.", path, namespace)
        }
        return current
    }

    @Throws(InvalidConfigPath::class)
    override fun <T> getConfig(key: String, type: Type): T {
        val rawJson = getRawJson(key)

        try {
            when (type) {
                is Class<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    return this.jsonMapper.treeToValue(rawJson, type) as T
                }

                is ParameterizedType -> {
                    val javaType = this.jsonMapper.typeFactory.constructType(type)
                    return this.jsonMapper.convertValue(rawJson, javaType)
                }

                else -> throw InvalidConfigPath("Unsupported type for config deserialization: %s", type.typeName)
            }
        } catch (e: JacksonException) {
            throw InvalidConfigPath("Failed to convert config value to class %s.", type.typeName, e)
        }
    }

    companion object {
        const val CONFIG_DIR: String = "config"
        const val MOD_CONFIG_TEMPLATE_FILE: String = "config.template.toml"
        private const val CORE_CONFIG_PATH = "config.toml"
        private const val MODEL_CONFIG_PATH = "model_config.toml"

        /** 访问语法：
         * - `namespcae:field1.field2`: 指定命名空间，访问某个字段
         * - `field1.field2`: 默认命名空间为 core，访问某个字段
         * - `namespace:*`: 直接获取整个命名空间的 JsonNode
         * 
         * 命名空间由字母、数字、下划线组成，不能包含空格；
         * 字段路径由字母、数字、下划线和点号组成，不能包含空格。 */
        private val KEY_SPLIT_PATTERN: Pattern = Pattern.compile(
            "(?:(?<namespace>[a-zA-Z0-9_-]+):)?(?<path>[a-zA-Z0-9_.*-]+)$"
        )
        private val FIELD_PATH_PATTERN: Pattern = Pattern.compile(
            "^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)*$"
        )
    }
}
