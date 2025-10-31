package org.maibot.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import ch.qos.logback.classic.Level;
import lombok.NoArgsConstructor;

import java.util.List;

/*
 * 主配置类，映射 config.toml 文件结构
 *
 * 请在配置项中使用包装类（如 Integer、Boolean）以支持 null 值
 * 请将字段设为final以防止意外修改
 */
@SuppressWarnings("unused") // 抑制警告：未使用
@NoArgsConstructor
public final class MainConfig {
    @JsonProperty(value = "log", required = true)
    public Log       log;
    @JsonProperty(value = "network", required = true)
    public Network   network;
    @JsonProperty(value = "local_data", required = true)
    public LocalData localData;
    @JsonProperty(value = "thinking", required = true)
    public Thinking  thinking;

    @NoArgsConstructor
    public final static class Log {
        @JsonProperty(value = "console", required = true)
        public ConsoleLogSettings console;
        @JsonProperty(value = "file", required = true)
        public FileLogSettings    file;

        @NoArgsConstructor
        public static final class ConsoleLogSettings {
            @JsonProperty(value = "level", defaultValue = "INFO")
            public Level        level;
            @JsonProperty(value = "filter_rule")
            public List<String> filterRule;
        }

        @NoArgsConstructor
        public static final class FileLogSettings {
            @JsonProperty(value = "level", defaultValue = "INFO")
            public Level        level;
            @JsonProperty(value = "filter_rule")
            public List<String> filterRule;
            @JsonProperty(value = "log_dir", defaultValue = "logs")
            public String       logDir;
        }
    }

    @NoArgsConstructor
    public static final class Network {
        @JsonProperty(value = "host", defaultValue = "127.0.0.1")
        public String  host;
        @JsonProperty(value = "port", defaultValue = "8080")
        public Integer port;
    }

    @NoArgsConstructor
    public static final class LocalData {
        @JsonProperty(value = "database", required = true)
        public Database database;

        @NoArgsConstructor
        public static class Database {
            @JsonProperty(value = "sqlite_path", defaultValue = "data/maibot.db")
            public String sqlitePath;
        }
    }

    @NoArgsConstructor
    public static final class Thinking {
        @JsonProperty(value = "observation_window_size", defaultValue = "20")
        public Integer observationWindowSize;
    }
}
