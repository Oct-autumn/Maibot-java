package org.maibot.core.config;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public final class MainConfig {
    public final static class Log {
        public static class FilterSettings {
            public String level = "INFO";

            @SerializedName("filter_rule")
            public List<String> filterRule = Collections.emptyList();
        }

        public static final class ConsoleLogSettings extends FilterSettings {
        }

        public static final class FileLogSettings extends FilterSettings {
            public String path = "logs";
        }

        public ConsoleLogSettings console = new ConsoleLogSettings();
        public FileLogSettings file = new FileLogSettings();
    }

    public static final class Network {
        public String host = "127.0.0.1";
        public int port = 8000;
    }

    public static final class LocalData {
        public static class Database {
            @SerializedName("sqlite_path")
            public String sqlitePath = "data/maibot.db";
        }

        public Database database = new Database();
    }

    @SerializedName("log")
    public Log log = new Log();

    @SerializedName("network")
    public Network network = new Network();

    @SerializedName("local_data")
    public LocalData localData = new LocalData();
}
