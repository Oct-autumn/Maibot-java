package org.maibot.core.config;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public final class MainConfig {
    public final static class Log {
        public static class FilterSettings {
            public String level;

            @SerializedName("filter_rule")
            public List<String> filterRule;
        }


        public static final class ConsoleLogSettings extends FilterSettings {
        }

        public static final class FileLogSettings extends FilterSettings {
            public String path;
        }

        public ConsoleLogSettings console;
        public FileLogSettings file;
    }

    public static final class Network {
        public String host;
        public int port;
    }

    public static final class LocalData {
        public static class Database {
            @SerializedName("sqlite_path")
            public String sqlitePath;
        }

        public Database database;
    }

    public static final class Chat {
        @SerializedName("observation_window_size")
        public int observationWindowSize;
    }

    @SerializedName("log")
    public Log log;

    @SerializedName("network")
    public Network network;

    @SerializedName("local_data")
    public LocalData localData;

    @SerializedName("chat")
    public Chat chat;
}
