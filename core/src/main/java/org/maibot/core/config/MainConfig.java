package org.maibot.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/*
 * 主配置类，映射 config.toml 文件结构
 *
 * 请在配置项中使用包装类（如 Integer、Boolean）以支持 null 值
 * 请将字段设为final以防止意外修改
 */
@SuppressWarnings("unused") // 抑制警告：未使用
public record MainConfig(
  @JsonProperty(value = "version", required = true) String version,
  @JsonProperty(value = "bot_info", required = true) BotInfo botInfo,
  @JsonProperty(value = "log", required = true) Log log,
  @JsonProperty(value = "network", required = true) Network network,
  @JsonProperty(value = "local_data", required = true) LocalData localData,
  @JsonProperty(value = "thinking", required = true) Thinking thinking
) {
    public record BotInfo(
      @JsonProperty(value = "name", required = true) String name,
      @JsonProperty(value = "aliases", defaultValue = "[]") List<String> aliases
    ) {
    }

    public record Log(
      @JsonProperty(value = "console", required = true) ConsoleLogSettings console,
      @JsonProperty(value = "file", required = true) FileLogSettings file
    ) {
        public record ConsoleLogSettings(
          @JsonProperty(value = "level", defaultValue = "INFO") String level,
          @JsonProperty(value = "filter_rule", defaultValue = "[]") List<String> filterRule
        ) {
        }

        public record FileLogSettings(
          @JsonProperty(value = "level", defaultValue = "INFO") String level,
          @JsonProperty(value = "filter_rule", defaultValue = "[]") List<String> filterRule,
          @JsonProperty(value = "log_dir", defaultValue = "logs") String logDir
        ) {
        }
    }

    public record Network(
      @JsonProperty(value = "host", defaultValue = "127.0.0.1") String host,
      @JsonProperty(value = "port", required = true) Integer port
    ) {
    }

    public record LocalData(@JsonProperty(value = "database", required = true) Database database) {
        public record Database(
          @JsonProperty(value = "sqlite_path", defaultValue = "data/maibot.db") String sqlitePath
        ) {
        }
    }

    public record Thinking(
      @JsonProperty(value = "observation_window_size", defaultValue = "20") Integer observationWindowSize
    ) {
    }
}
