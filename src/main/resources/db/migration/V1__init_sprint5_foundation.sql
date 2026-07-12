CREATE SCHEMA IF NOT EXISTS quest;

CREATE TABLE IF NOT EXISTS quest.quest_definition (
    id UUID PRIMARY KEY,
    quest_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    dsl TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_quest_definition_quest_id
    ON quest.quest_definition (quest_id);

CREATE TABLE IF NOT EXISTS quest.quest_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    quest_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    game_state JSONB NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_quest_session_user_id
    ON quest.quest_session (user_id);

CREATE INDEX IF NOT EXISTS idx_quest_session_active_lookup
    ON quest.quest_session (user_id, quest_id, status);
