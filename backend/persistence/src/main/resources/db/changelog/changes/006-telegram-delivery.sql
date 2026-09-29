--liquibase formatted sql
--changeset ai-lead-manager:006-telegram-delivery
CREATE TABLE "AI_LEAD_MANAGER".telegram_updates (
    update_id bigint PRIMARY KEY,
    update_type varchar(32) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz,
    CONSTRAINT telegram_updates_type_not_blank CHECK (length(btrim(update_type)) > 0),
    CONSTRAINT telegram_updates_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX telegram_updates_received_at_idx
    ON "AI_LEAD_MANAGER".telegram_updates (received_at DESC);

CREATE TABLE "AI_LEAD_MANAGER".telegram_chat_bindings (
    user_id bigint PRIMARY KEY REFERENCES "AI_LEAD_MANAGER".users(id) ON DELETE CASCADE,
    telegram_chat_id bigint NOT NULL UNIQUE,
    chat_type varchar(16) NOT NULL DEFAULT 'PRIVATE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT telegram_chat_bindings_chat_type_valid CHECK (chat_type = 'PRIVATE')
);

CREATE TABLE "AI_LEAD_MANAGER".telegram_delivery_jobs (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_key varchar(160) NOT NULL UNIQUE,
    message_type varchar(48) NOT NULL,
    chat_id bigint NOT NULL,
    lead_id bigint REFERENCES "AI_LEAD_MANAGER".leads(id) ON DELETE CASCADE,
    message_text text NOT NULL,
    button_text varchar(100),
    button_url varchar(2048),
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 5,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    locked_at timestamptz,
    locked_until timestamptz,
    locked_by varchar(100),
    last_error_code varchar(64),
    last_error_message varchar(1000),
    telegram_message_id bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    delivered_at timestamptz,
    CONSTRAINT telegram_delivery_jobs_type_valid CHECK (message_type IN (
        'START_REPLY', 'HELP_REPLY', 'MANAGER_NEW_LEAD', 'CUSTOMER_LEAD_CONFIRMATION'
    )),
    CONSTRAINT telegram_delivery_jobs_status_valid CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT telegram_delivery_jobs_message_not_blank CHECK (length(btrim(message_text)) > 0),
    CONSTRAINT telegram_delivery_jobs_attempts_valid CHECK (
        attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts
    ),
    CONSTRAINT telegram_delivery_jobs_button_pair CHECK ((button_text IS NULL) = (button_url IS NULL)),
    CONSTRAINT telegram_delivery_jobs_lock_valid CHECK (
        (status = 'RUNNING' AND locked_at IS NOT NULL AND locked_until IS NOT NULL AND locked_by IS NOT NULL)
        OR
        (status <> 'RUNNING' AND locked_at IS NULL AND locked_until IS NULL AND locked_by IS NULL)
    )
);

CREATE INDEX telegram_delivery_jobs_available_idx
    ON "AI_LEAD_MANAGER".telegram_delivery_jobs (next_attempt_at, created_at, id)
    WHERE status = 'PENDING';

CREATE INDEX telegram_delivery_jobs_stale_idx
    ON "AI_LEAD_MANAGER".telegram_delivery_jobs (locked_until)
    WHERE status = 'RUNNING';

CREATE INDEX telegram_delivery_jobs_lead_idx
    ON "AI_LEAD_MANAGER".telegram_delivery_jobs (lead_id, created_at DESC)
    WHERE lead_id IS NOT NULL;
