package org.maibot.sdk.ioc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于标注一个类为IoC容器的组件
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {
    /// 是否为单例组件，默认为true
    boolean singleton() default true;

    /// 组件名称（若不填写则默认使用<code>class.getSimpleName()</code>）
    String name() default "";

    /// 是否为主要实现，默认为false
    boolean primaryImpl() default false;
}
