package org.maibot.sdk.manager;

import jakarta.persistence.EntityManager;
import org.maibot.sdk.storage.db.dao.InteractionEntity;
import org.maibot.sdk.storage.db.dao.InteractionGroup;
import org.maibot.sdk.storage.db.dao.InteractionStream;
import org.maibot.sdk.storage.domain.StreamType;

public interface InteractionStreamManager {

    InteractionStream getOrCreateIfAbsent(
      EntityManager em,
      StreamType type,
      InteractionEntity interactionEntity,
      InteractionGroup interactionGroup
    );
}
