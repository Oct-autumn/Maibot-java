package org.maibot.core

import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

data class LaunchArgs(
    @field:JsonProperty("mod_list") val modList: Array<String>
) {
    companion object {
        fun parse(args: String): LaunchArgs {
            // 解析启动参数
            val reader = JsonMapper.builder()
                .addModule(KotlinModule.Builder().build())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build()
                .readerFor(LaunchArgs::class.java)
            try {
                return reader.readValue(args)
            } catch (e: JacksonException) {
                throw RuntimeException("解析启动参数失败", e)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LaunchArgs

        return modList.contentEquals(other.modList)
    }

    override fun hashCode(): Int {
        return modList.contentHashCode()
    }
}
