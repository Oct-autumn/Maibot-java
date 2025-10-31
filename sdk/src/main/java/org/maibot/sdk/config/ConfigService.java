package org.maibot.sdk.config;

import org.maibot.sdk.exceptions.InvalidConfigPath;
import tools.jackson.databind.JsonNode;

/**
 * 配置服务接口
 * <p>
 * 用于进行全局配置管理
 */
public interface ConfigService {
    /**
     * 获取原始的 JSON 配置对象
     *
     * @param key 配置键
     * @return JSON 对象
     * @throws InvalidConfigPath 如果配置路径无效
     */
    JsonNode getRawJson(String key)
    throws InvalidConfigPath;

    /**
     * 获取指定类型的配置对象
     *
     * @param key   配置键
     * @param clazz 配置类
     * @param <T>   配置类型
     * @return 配置对象
     * @throws InvalidConfigPath 如果配置路径无效
     */
    <T> T getConfig(String key, Class<T> clazz)
    throws InvalidConfigPath;
}
