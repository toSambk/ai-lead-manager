--liquibase formatted sql
--changeset ai-lead-manager:003-lead-events
CREATE TABLE "AI_LEAD_MANAGER".lead_events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lead_id bigint NOT NULL REFERENCES "AI_LEAD_MANAGER".leads(id) ON DELETE CASCADE,
    actor_id bigint REFERENCES "AI_LEAD_MANAGER".users(id) ON DELETE SET NULL,
    event_type varchar(32) NOT NULL,
    old_status varchar(24),
    new_status varchar(24),
    old_owner_id bigint REFERENCES "AI_LEAD_MANAGER".users(id) ON DELETE SET NULL,
    new_owner_id bigint REFERENCES "AI_LEAD_MANAGER".users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT lead_events_type_valid CHECK (event_type IN ('STATUS_CHANGED', 'OWNER_CHANGED')),
    CONSTRAINT lead_events_old_status_valid CHECK (old_status IS NULL OR old_status IN ('NEW', 'CLARIFICATION', 'IN_PROGRESS', 'COMPLETED', 'REJECTED')),
    CONSTRAINT lead_events_new_status_valid CHECK (new_status IS NULL OR new_status IN ('NEW', 'CLARIFICATION', 'IN_PROGRESS', 'COMPLETED', 'REJECTED')),
    CONSTRAINT lead_events_payload_valid CHECK (
        (event_type = 'STATUS_CHANGED'
            AND old_status IS NOT NULL
            AND new_status IS NOT NULL
            AND old_status <> new_status
            AND old_owner_id IS NULL
            AND new_owner_id IS NULL)
        OR
        (event_type = 'OWNER_CHANGED'
            AND old_status IS NULL
            AND new_status IS NULL
            AND old_owner_id IS DISTINCT FROM new_owner_id)
    )
);

CREATE INDEX lead_events_lead_created_at_idx
    ON "AI_LEAD_MANAGER".lead_events (lead_id, created_at DESC, id DESC);
