package org.maibot.sdk.mod;

import org.maibot.sdk.ioc.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为Mod的主类
 * <p>
 * 一个Mod必须有且仅有一个主类，否则会抛出异常
 * <p>
 * 主类需要继承自<code>org.maibot.sdk.mod.Mod</code>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface ModMainClass {
    String description() default "";

    String author() default "Unknown";
}
