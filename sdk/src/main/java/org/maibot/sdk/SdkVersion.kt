package org.maibot.sdk

import org.maibot.sdk.exceptions.FatalError
import java.io.IOException
import java.util.*

class SdkVersion {
    companion object {
        fun get(): String {
            try {
                SdkVersion::class.java.getResourceAsStream("/org/maibot/sdk/build-inf.properties").use { input ->
                    checkNotNull(input)
                    val prop = Properties()
                    prop.load(input)
                    return prop.getProperty("version", "0.0.0")
                }
            } catch (e: IOException) {
                throw FatalError("An error occurred when loading version info.", e)
            }
        }
    }
}
