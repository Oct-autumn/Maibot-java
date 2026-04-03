package org.maibot.sdk.ioc

/**
 * 用于在构造方法参数上指定注入的实现类
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Specify(val name: String)

