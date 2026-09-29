--liquibase formatted sql
--changeset ai-lead-manager:007-lead-clarification
ALTER TABLE "AI_LEAD_MANAGER".lead_messages
    ADD COLUMN delivery_status varchar(16),
    ADD COLUMN telegram_chat_id bigint,
    ADD COLUMN telegram_message_id bigint,
    ADD CONSTRAINT lead_messages_delivery_status_valid
        CHECK (delivery_status IS NULL OR delivery_status IN ('PENDING', 'SENT', 'FAILED')),
    ADD CONSTRAINT lead_messages_telegram_identity_pair
        CHECK ((telegram_chat_id IS NULL) = (telegram_message_id IS NULL));

CREATE UNIQUE INDEX lead_messages_telegram_identity_idx
    ON "AI_LEAD_MANAGER".lead_messages (telegram_chat_id, telegram_message_id)
    WHERE telegram_chat_id IS NOT NULL AND telegram_message_id IS NOT NULL;

CREATE TABLE "AI_LEAD_MANAGER".reply_drafts (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lead_id bigint NOT NULL REFERENCES "AI_LEAD_MANAGER".leads(id) ON DELETE CASCADE,
    author_id bigint NOT NULL REFERENCES "AI_LEAD_MANAGER".users(id) ON DELETE RESTRICT,
    body text NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    approved_at timestamptz,
    sent_at timestamptz,
    CONSTRAINT reply_drafts_body_not_blank CHECK (length(btrim(body)) > 0),
    CONSTRAINT reply_drafts_body_length CHECK (char_length(body) <= 2000),
    CONSTRAINT reply_drafts_status_valid CHECK (status IN ('DRAFT', 'APPROVED', 'SENT', 'FAILED'))
);

CREATE INDEX reply_drafts_lead_created_at_idx
    ON "AI_LEAD_MANAGER".reply_drafts (lead_id, created_at DESC, id DESC);

CREATE TABLE "AI_LEAD_MANAGER".telegram_conversations (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL REFERENCES "AI_LEAD_MANAGER".users(id) ON DELETE CASCADE,
    lead_id bigint NOT NULL REFERENCES "AI_LEAD_MANAGER".leads(id) ON DELETE CASCADE,
    question_message_id bigint NOT NULL REFERENCES "AI_LEAD_MANAGER".lead_messages(id) ON DELETE CASCADE,
    status varchar(24) NOT NULL DEFAULT 'PENDING_DELIVERY',
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    answered_at timestamptz,
    CONSTRAINT telegram_conversations_status_valid CHECK (
        status IN ('PENDING_DELIVERY', 'AWAITING_REPLY', 'ANSWERED', 'CANCELLED')
    )
);

CREATE UNIQUE INDEX telegram_conversations_one_active_per_customer_idx
    ON "AI_LEAD_MANAGER".telegram_conversations (customer_id)
    WHERE status IN ('PENDING_DELIVERY', 'AWAITING_REPLY');

CREATE INDEX telegram_conversations_lead_created_at_idx
    ON "AI_LEAD_MANAGER".telegram_conversations (lead_id, created_at DESC, id DESC);

ALTER TABLE "AI_LEAD_MANAGER".telegram_delivery_jobs
    ADD COLUMN reply_draft_id bigint REFERENCES "AI_LEAD_MANAGER".reply_drafts(id) ON DELETE CASCADE,
    ADD COLUMN lead_message_id bigint REFERENCES "AI_LEAD_MANAGER".lead_messages(id) ON DELETE CASCADE,
    ADD COLUMN conversation_id bigint REFERENCES "AI_LEAD_MANAGER".telegram_conversations(id) ON DELETE CASCADE;

ALTER TABLE "AI_LEAD_MANAGER".telegram_delivery_jobs
    DROP CONSTRAINT telegram_delivery_jobs_type_valid,
    ADD CONSTRAINT telegram_delivery_jobs_type_valid CHECK (message_type IN (
        'START_REPLY', 'HELP_REPLY', 'MANAGER_NEW_LEAD', 'CUSTOMER_LEAD_CONFIRMATION',
        'MANAGER_APPROVED_REPLY'
    ));

CREATE UNIQUE INDEX telegram_delivery_jobs_reply_draft_idx
    ON "AI_LEAD_MANAGER".telegram_delivery_jobs (reply_draft_id)
    WHERE reply_draft_id IS NOT NULL;
