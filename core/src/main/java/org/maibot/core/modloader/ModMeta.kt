package org.maibot.core.modloader;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;

import java.util.List;

@SuppressWarnings("unused") // 抑制警告：未使用
@NoArgsConstructor
public class ModMeta {
    @JsonProperty(value = "mod_id", required = true)
    public String              modId;
    @JsonProperty(value = "version", required = true)
    public String              version;
    @JsonProperty(value = "package_name", required = true)
    public String              packageName;
    @JsonProperty(value = "main_class", required = true)
    public String              mainClass;
    @JsonProperty(value = "sdk_version", required = true)
    public String              sdkVersion;
    @JsonProperty("dependencies")
    public List<ModDependency> dependencies;

    @NoArgsConstructor
    public static class ModDependency {
        @JsonProperty(value = "mod_id", required = true)
        public String  modId;
        @JsonProperty(value = "version", required = true)
        public String  version;
        @JsonProperty(value = "mandatory", required = true)
        public boolean mandatory;
    }
}
