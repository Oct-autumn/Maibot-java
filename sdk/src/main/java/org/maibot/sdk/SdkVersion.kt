package org.maibot.sdk;

import org.maibot.sdk.exceptions.FatalError;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class SdkVersion {
    public static String get() {
        try (InputStream input = SdkVersion.class.getResourceAsStream("/org/maibot/sdk/build-inf.properties")) {
            assert input != null;

            Properties prop = new Properties();
            prop.load(input);

            return prop.getProperty("version", "0.0.0");
        } catch (IOException e) {
            throw new FatalError("An error occurred when loading version info.", e);
        }
    }
}
