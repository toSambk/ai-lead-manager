--liquibase formatted sql
--changeset ai-lead-manager:004-lead-messages
CREATE TABLE "AI_LEAD_MANAGER".lead_messages (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lead_id bigint NOT NULL REFERENCES "AI_LEAD_MANAGER".leads(id) ON DELETE CASCADE,
    sender_id bigint REFERENCES "AI_LEAD_MANAGER".users(id) ON DELETE SET NULL,
    kind varchar(32) NOT NULL,
    body text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT lead_messages_kind_valid CHECK (
        kind IN ('INTERNAL_NOTE', 'CUSTOMER_REPLY', 'MANAGER_REPLY', 'FOLLOW_UP_QUESTION')
    ),
    CONSTRAINT lead_messages_body_not_blank CHECK (length(btrim(body)) > 0),
    CONSTRAINT lead_messages_body_length CHECK (char_length(body) <= 2000)
);

CREATE INDEX lead_messages_lead_created_at_idx
    ON "AI_LEAD_MANAGER".lead_messages (lead_id, created_at DESC, id DESC);
