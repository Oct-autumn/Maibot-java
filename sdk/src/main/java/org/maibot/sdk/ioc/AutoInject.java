package org.maibot.sdk.ioc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 如果构造方法上标注了<code>@AutoInject</code>，IoC容器将使用该构造方法创建实例
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.CONSTRUCTOR)
public @interface AutoInject {
}
