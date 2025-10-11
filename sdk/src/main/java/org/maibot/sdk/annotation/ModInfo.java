package org.maibot.sdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModInfo {
    String type();
    String name();
    String version();
    String supportedCoreVersion();
    String description() default "";
    String author() default "Unknown";
    String[] dependencies() default {};
}
