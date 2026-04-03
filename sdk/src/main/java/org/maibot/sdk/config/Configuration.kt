package org.maibot.sdk.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为配置类
 * <p>
 * 一个Mod最多允许有一个配置类，若有多个则会抛出异常
 * <p>
 * 配置类需要有对应的模板配置文件，置于<code>resources</code>目录下，
 * 文件名为<code>config.template.toml</code>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Configuration {
    /// 配置命名空间，默认为<code>config</code>
    /// 该字段尚未启用，保留以备将来使用
    String namespace() default "config";
}
