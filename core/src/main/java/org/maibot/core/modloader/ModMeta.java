package org.maibot.core.modloader;

import com.google.gson.annotations.SerializedName;

import java.util.List;

@SuppressWarnings("ClassCanBeRecord")
public class ModMeta {
    public static class ModDependency {
        @SerializedName("mod_id")
        public final String modId;
        @SerializedName("version")
        public final String version;
        @SerializedName("mandatory")
        public final boolean mandatory;

        public ModDependency(String modId, String version, boolean mandatory) {
            this.modId = modId;
            this.version = version;
            this.mandatory = mandatory;
        }
    }

    @SerializedName("mod_id")
    public final String modId;
    @SerializedName("version")
    public final String version;
    @SerializedName("main_class")
    public final String mainClass;
    @SerializedName("sdk_version")
    public final String sdkVersion;

    public final List<ModDependency> dependencies;

    public ModMeta(String modId, String version, String mainClass, String sdkVersion, List<ModDependency> dependencies) {
        this.modId = modId;
        this.version = version;
        this.mainClass = mainClass;
        this.sdkVersion = sdkVersion;
        this.dependencies = dependencies;
    }
}
