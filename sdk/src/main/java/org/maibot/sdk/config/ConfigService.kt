package org.maibot.sdk.config

import org.maibot.sdk.exceptions.InvalidConfigPath
import tools.jackson.databind.JsonNode
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

/**
 * 配置服务接口
 * 
 * 
 * 用于进行全局配置管理
 */
interface ConfigService {
    /**
     * 获取原始的 JSON 配置对象
     * 
     * @param key 配置键
     * @return JSON 对象
     * @throws InvalidConfigPath 如果配置路径无效
     */
    @Throws(InvalidConfigPath::class)
    fun getRawJson(key: String): JsonNode

    /**
     * 获取指定类型的配置对象
     * 
     * @param key   配置键
     * @param clazz 配置类
     * @param <T>   配置类型
     * @return 配置对象
     * @throws InvalidConfigPath 如果配置路径无效
     */
    @Throws(InvalidConfigPath::class)
    fun <T> getConfig(key: String, type: Type): T
}
