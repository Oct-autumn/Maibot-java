package org.maibot.sdk.util

object UnwrapUtils {
    @JvmStatic
    fun <T> unwrap(clazz: Class<T>, vararg objects: Any?): T {
        for (obj in objects) {
            if (clazz.isInstance(obj)) {
                return clazz.cast(obj)
            }
        }
        throw ClassCastException("Cannot unwarp to " + clazz.getName())
    }
}
