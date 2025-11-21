package org.maibot.sdk.util;

public class UnwrapUtils {
    public static <T> T unwrap(Class<T> clazz, Object... objects) {
        for (Object obj : objects) {
            if (clazz.isInstance(obj)) {
                return clazz.cast(obj);
            }
        }
        throw new ClassCastException("Cannot unwarp to " + clazz.getName());
    }
}
