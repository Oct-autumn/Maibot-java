package org.maibot.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public record LaunchArgs(
  @JsonProperty("mod_list") @JsonPropertyDescription("要加载的Mod的绝对路径列表") String[] modList
) {
    public static LaunchArgs parse(String args) {
        // 解析启动参数
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(args, LaunchArgs.class);
        } catch (JacksonException e) {
            throw new RuntimeException("解析启动参数失败", e);
        }
    }
}
