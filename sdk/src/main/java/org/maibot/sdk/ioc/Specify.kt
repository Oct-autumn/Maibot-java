package org.maibot.sdk.ioc

/**
 * 当用在构造方法参数上时，指定注入的实现类名称
 *
 * 当用在类上时，指定该类的名称
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.CLASS)
annotation class Specify(val name: String)

