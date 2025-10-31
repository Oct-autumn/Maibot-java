package org.maibot.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class GroupInfo {
    /// 群组在平台的ID
    private final String platformId;
    /// 群名称
    private final String groupName;
}
