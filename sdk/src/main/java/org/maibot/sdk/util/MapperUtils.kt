package org.maibot.sdk.util

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

object MapperUtils {
    val jsonMapper: JsonMapper by lazy {
        JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .build()
    }
}