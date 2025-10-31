package org.maibot.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class EntityInfo {
    /// 实体在平台的ID
    private final String platformId;
    /// 昵称
    private final String nickname;
    /// 备注名/好友备注
    private final String cardNickname;
}
