package org.maibot.launcher;

import org.semver4j.Semver;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class BuildInfo {
    private final Semver  coreVersion;
    private final Instant buildTime;

    public BuildInfo()
    throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/META-INF/build-inf.properties")) {
            assert input != null;

            Properties prop = new Properties();
            prop.load(input);

            var versionStr = prop.getProperty("version", "0.0.0");
            var buildTimeStr = prop.getProperty("buildTime", "0");

            this.coreVersion = new Semver(versionStr);
            this.buildTime = Instant.ofEpochSecond(Long.parseLong(buildTimeStr));
        } catch (IOException e) {
            throw new Exception("An error occurred when loading build info.", e);
        }
    }

    public Semver coreVersion() {
        return this.coreVersion;
    }

    public String getBuildTime() {
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));
        return formatter.format(this.buildTime);
    }
}
