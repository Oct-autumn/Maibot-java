package org.maibot.core.config

import org.maibot.sdk.SdkVersion
import org.maibot.sdk.exceptions.FatalError
import org.maibot.sdk.ioc.Component
import org.semver4j.Semver
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Component
class BuildInfo private constructor() {
    private val buildTimeInstant: Instant

    val coreVersion: Semver
    val sdkVersion: Semver
    val buildTime: String
        get() = run {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))
            return formatter.format(this.buildTimeInstant)
        }

    init {
        try {
            BuildInfo::class.java.getResourceAsStream("/META-INF/build-inf.properties").use { input ->
                checkNotNull(input)
                val prop = Properties()
                prop.load(input)

                val versionStr = prop.getProperty("version", "0.0.0")
                val sdkVersionStr = SdkVersion.get()
                val buildTimeStr = prop.getProperty("buildTime", "0")

                this.coreVersion = Semver(versionStr)
                this.sdkVersion = Semver(sdkVersionStr)
                this.buildTimeInstant = Instant.ofEpochSecond(buildTimeStr.toLong())
            }
        } catch (e: IOException) {
            throw FatalError("An error occurred when loading build info.", e)
        }
    }


}
