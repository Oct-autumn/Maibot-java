package org.maibot.sdk.manager;

import jakarta.persistence.EntityManager;
import org.maibot.sdk.storage.db.dao.InteractionGroup;

public interface InteractionGroupManager {
    InteractionGroup getOrCreatIfAbsent(
      EntityManager em,
      String platform,
      String platformGroupId,
      String groupName
    );

    InteractionGroup get(EntityManager em, String platform, String platformGroupId);
}
