package org.maibot.core.config;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;

import java.util.List;

/*
 * 主配置类，映射 config.toml 文件结构
 *
 * 请在配置项中使用包装类（如 Integer、Boolean）以支持 null 值
 * 请将字段设为final以防止意外修改
 */

@SuppressWarnings({"unused", "ClassCanBeRecord"}) // 抑制警告：未使用、可以转化为记录类
@AllArgsConstructor
public final class MainConfig {
    @AllArgsConstructor
    public final static class Log {
        @AllArgsConstructor
        public static class FilterSettings {
            public final String level;

            @SerializedName("filter_rule")
            public final List<String> filterRule;
        }


        public static final class ConsoleLogSettings extends FilterSettings {
            public ConsoleLogSettings(String level, List<String> filterRule) {
                super(level, filterRule);
            }
        }

        public static final class FileLogSettings extends FilterSettings {
            public final String path;

            public FileLogSettings(String level, List<String> filterRule, String path) {
                super(level, filterRule);
                this.path = path;
            }
        }

        public final ConsoleLogSettings console;
        public final FileLogSettings file;
    }

    @AllArgsConstructor
    public static final class Network {
        public final String host;
        public final Integer port;
    }

    @AllArgsConstructor
    public static final class LocalData {
        @AllArgsConstructor
        public static class Database {
            @SerializedName("sqlite_path")
            public final String sqlitePath;
        }

        public final Database database;
    }

    @AllArgsConstructor
    public static final class Thinking {
        @SerializedName("observation_window_size")
        public final Integer observationWindowSize;
    }

    @SerializedName("log")
    public final Log log;

    @SerializedName("network")
    public final Network network;

    @SerializedName("local_data")
    public final LocalData localData;

    @SerializedName("thinking")
    public final Thinking thinking;
}
