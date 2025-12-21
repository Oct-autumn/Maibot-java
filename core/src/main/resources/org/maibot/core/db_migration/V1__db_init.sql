-- Person Table
-- auto-inc id
CREATE TABLE IF NOT EXISTS 'person'
(
    'id'         INTEGER PRIMARY KEY AUTOINCREMENT,
    'name'       VARCHAR(255) NOT NULL,
    'created_at' BIGINT       NOT NULL,
    'updated_at' BIGINT       NOT NULL
);

-- InteractionEntity Table
CREATE TABLE IF NOT EXISTS 'interaction_entity'
(
    'id'               INTEGER PRIMARY KEY AUTOINCREMENT,
    'platform'         VARCHAR(255) NOT NULL,
    'platform_user_id' VARCHAR(255) NOT NULL,
    'nickname'         VARCHAR(255) NOT NULL,
    'person_id'        INTEGER      NOT NULL,
    'created_at'       BIGINT       NOT NULL,
    'updated_at'       BIGINT       NOT NULL,
    CONSTRAINT 'idx_interaction_entity_platform_user' UNIQUE ('platform', 'platform_user_id'),
    FOREIGN KEY ('person_id') REFERENCES 'person' ('id')
);

-- InteractionGroup Table
CREATE TABLE IF NOT EXISTS 'interaction_group'
(
    'id'                INTEGER PRIMARY KEY AUTOINCREMENT,
    'platform'          VARCHAR(255) NOT NULL,
    'platform_group_id' VARCHAR(255) NOT NULL,
    'group_name'        VARCHAR(255),
    'created_at'        BIGINT       NOT NULL,
    'updated_at'        BIGINT       NOT NULL,
    CONSTRAINT 'idx_interaction_group_platform_group' UNIQUE ('platform', 'platform_group_id')
);

-- GroupMember Table
CREATE TABLE IF NOT EXISTS 'group_member'
(
    'entity_id'  INTEGER NOT NULL,
    'group_id'   INTEGER NOT NULL,
    'card_name'  VARCHAR(255),
    'created_at' BIGINT  NOT NULL,
    'updated_at' BIGINT  NOT NULL,
    CONSTRAINT 'pk_group_member' PRIMARY KEY ('entity_id', 'group_id'),
    FOREIGN KEY ('entity_id') REFERENCES 'interaction_entity' ('id'),
    FOREIGN KEY ('group_id') REFERENCES 'interaction_group' ('id')
);

-- InteractionStream Table
CREATE TABLE IF NOT EXISTS 'interaction_stream'
(
    'id'         VARCHAR(255) PRIMARY KEY,
    'type'       SMALLINT NOT NULL,
    'entity_id'  INTEGER,
    'group_id'   INTEGER,
    'created_at' BIGINT   NOT NULL,
    'updated_at' BIGINT   NOT NULL,
    CONSTRAINT 'idx_interaction_stream_type_entity_group' UNIQUE ('type', 'entity_id', 'group_id'),
    FOREIGN KEY ('entity_id') REFERENCES 'interaction_entity' ('id'),
    FOREIGN KEY ('group_id') REFERENCES 'interaction_group' ('id')
);

-- Message Table

CREATE TABLE IF NOT EXISTS 'message'
(
    'id'               INTEGER PRIMARY KEY AUTOINCREMENT,
    'timestamp'        BIGINT       NOT NULL,
    'sequence'         BIGINT       NOT NULL,
    'prompt_str'       MEDIUMTEXT,
    'raw_content_json' MEDIUMTEXT   NOT NULL,
    'object_type'      TEXT         NOT NULL,
    'sender_id'        INTEGER      NOT NULL,
    'stream_id'        VARCHAR(255) NOT NULL,
    'created_at'       BIGINT       NOT NULL,
    'updated_at'       BIGINT       NOT NULL,
    FOREIGN KEY ('sender_id') REFERENCES 'interaction_entity' ('id'),
    FOREIGN KEY ('stream_id') REFERENCES 'interaction_stream' ('id')
);

---- Index - timestamp
CREATE INDEX IF NOT EXISTS 'idx_message_timestamp'
    ON 'message' ('timestamp');
---- Index - stream_id
CREATE INDEX IF NOT EXISTS 'idx_message_stream'
    ON 'message' ('stream_id');

-- BinFile Table
CREATE TABLE IF NOT EXISTS 'bin_file'
(
    'id'          INTEGER PRIMARY KEY AUTOINCREMENT,
    'hash_sha256' VARCHAR(64)  NOT NULL,
    'file_type'   VARCHAR(255) NOT NULL,
    'created_at'  BIGINT       NOT NULL,
    'updated_at'  BIGINT       NOT NULL,
    CONSTRAINT 'idx_bin_file_hash_sha256' UNIQUE ('hash_sha256')
);