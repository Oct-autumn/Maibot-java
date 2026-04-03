package org.maibot.sdk.mod

import org.maibot.sdk.ioc.Component

/**
 * 标记一个类为Mod的主类
 * 
 * 
 * 一个Mod必须有且仅有一个主类，否则会抛出异常
 * 
 * 
 * 主类需要继承自`org.maibot.sdk.mod.Mod`
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Component
annotation class ModMainClass(val description: String = "", val author: String = "Unknown")
