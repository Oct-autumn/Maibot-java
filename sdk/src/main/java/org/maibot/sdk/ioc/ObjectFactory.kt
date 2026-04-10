package org.maibot.sdk.ioc

/**
 * 标记一个类为对象工厂，表示该类负责创建某些对象的实例。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Component(singleton = false)
annotation class ObjectFactory
