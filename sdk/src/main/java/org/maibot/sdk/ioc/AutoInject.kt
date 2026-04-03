package org.maibot.sdk.ioc

/**
 * 如果构造方法上标注了`@AutoInject`，IoC容器将使用该构造方法创建实例
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CONSTRUCTOR)
annotation class AutoInject 
