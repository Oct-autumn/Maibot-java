package org.maibot.core.modloader

import com.fasterxml.jackson.annotation.JsonProperty

data class ModMeta(
    @field:JsonProperty(value = "mod_id", required = true) var modId: String,
    @field:JsonProperty(value = "version", required = true) var version: String,
    @field:JsonProperty(value = "package_name", required = true) var packageName: String,
    @field:JsonProperty(value = "sdk_version", required = true) var sdkVersion: String,
    @field:JsonProperty("dependencies") var dependencies: List<ModDependency> = listOf()
) {
    data class ModDependency(
        @field:JsonProperty(value = "mod_id", required = true) var modId: String,
        @field:JsonProperty(value = "version", required = true) var version: String,
        @field:JsonProperty(value = "mandatory", required = true) var mandatory: Boolean,
    )
}
