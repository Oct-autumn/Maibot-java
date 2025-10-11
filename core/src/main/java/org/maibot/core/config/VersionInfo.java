package org.maibot.core.config;

import org.maibot.core.cdi.annotation.Component;
import org.semver4j.Semver;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

@Component
public class VersionInfo {
    private final Semver version;
    private final Instant buildTime;

    private VersionInfo() {
        try (InputStream input = getClass().getResourceAsStream("/org/maibot/core/version.properties")) {
            assert input != null;

            Properties prop = new Properties();
            prop.load(input);

            String versionStr = prop.getProperty("version", "0.0.0");
            String buildTimeStr = prop.getProperty("buildTime", "0");

            this.version = new Semver(versionStr);
            this.buildTime = Instant.ofEpochSecond(Long.parseLong(buildTimeStr));
        } catch (Exception e) {
            throw new RuntimeException("An error occurred when loading version info.", e);
        }
    }

    public String getVersion() {
        return version.getVersion();
    }

    public String getBuildTime() {
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));
        return formatter.format(buildTime);
    }
}
