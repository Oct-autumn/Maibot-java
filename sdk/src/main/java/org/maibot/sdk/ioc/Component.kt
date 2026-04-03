package org.maibot.sdk.ioc

/**
 * 用于标注一个类为IoC容器的组件
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
annotation class Component(
    /** 是否为单例组件，默认为true */
    val singleton: Boolean = true,
    /** 组件名称（若不填写则默认使用`class.getSimpleName()`） */
    val name: String = "",
    /** 是否为主要实现，默认为false */
    val primaryImpl: Boolean = false
)
