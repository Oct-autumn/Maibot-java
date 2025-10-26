package org.maibot.sdk;

import java.io.InputStream;
import java.util.Properties;

public class SdkVersion {
    public static String get() {
        try (InputStream input = SdkVersion.class.getResourceAsStream("/META-INF/build-inf.properties")) {
            assert input != null;

            Properties prop = new Properties();
            prop.load(input);

            return prop.getProperty("version", "0.0.0");
        } catch (Exception e) {
            throw new RuntimeException("An error occurred when loading version info.", e);
        }
    }
}
