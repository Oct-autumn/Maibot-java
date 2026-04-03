package org.maibot.sdk.config

/**
 * 标记一个类为配置类
 * 
 * 
 * 一个Mod最多允许有一个配置类，若有多个则会抛出异常
 * 
 * 
 * 配置类需要有对应的模板配置文件，置于`resources`目录下，
 * 文件名为`config.template.toml`
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Configuration(
    /** 配置命名空间，默认为`config`
     * 该字段尚未启用，保留以备将来使用 */
    val namespace: String = "config"
)
