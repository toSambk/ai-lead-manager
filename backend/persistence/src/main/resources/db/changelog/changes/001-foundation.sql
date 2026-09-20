--liquibase formatted sql
--changeset ai-lead-manager:001-foundation
CREATE SCHEMA "AI_LEAD_MANAGER";

CREATE TABLE "AI_LEAD_MANAGER".users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    telegram_user_id bigint NOT NULL UNIQUE,
    role varchar(16) NOT NULL DEFAULT 'CUSTOMER',
    display_name text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_telegram_user_id_positive CHECK (telegram_user_id > 0),
    CONSTRAINT users_role_valid CHECK (role IN ('CUSTOMER', 'MANAGER', 'ADMIN')),
    CONSTRAINT users_display_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE TABLE "AI_LEAD_MANAGER".service_categories (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code varchar(64) NOT NULL UNIQUE,
    name text NOT NULL,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT service_categories_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT service_categories_name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE "AI_LEAD_MANAGER".leads (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL REFERENCES "AI_LEAD_MANAGER".users(id),
    category_id bigint NOT NULL REFERENCES "AI_LEAD_MANAGER".service_categories(id),
    description text NOT NULL,
    estimated_budget_amount numeric(14,2),
    budget_currency char(3),
    desired_deadline date,
    contact_details text NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'NEW',
    owner_id bigint REFERENCES "AI_LEAD_MANAGER".users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT leads_description_not_blank CHECK (btrim(description) <> ''),
    CONSTRAINT leads_contact_details_not_blank CHECK (btrim(contact_details) <> ''),
    CONSTRAINT leads_budget_nonnegative CHECK (estimated_budget_amount >= 0),
    CONSTRAINT leads_budget_pair CHECK ((estimated_budget_amount IS NULL) = (budget_currency IS NULL)),
    CONSTRAINT leads_budget_currency_format CHECK (budget_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT leads_status_valid CHECK (status IN ('NEW', 'CLARIFICATION', 'IN_PROGRESS', 'COMPLETED', 'REJECTED')),
    CONSTRAINT leads_version_nonnegative CHECK (version >= 0)
);

CREATE INDEX leads_customer_created_at_idx ON "AI_LEAD_MANAGER".leads (customer_id, created_at DESC);
CREATE INDEX leads_status_created_at_idx ON "AI_LEAD_MANAGER".leads (status, created_at DESC);
CREATE INDEX leads_owner_created_at_idx ON "AI_LEAD_MANAGER".leads (owner_id, created_at DESC);

INSERT INTO "AI_LEAD_MANAGER".service_categories (code, name) VALUES
    ('website_development', 'Website development'),
    ('automation', 'Automation');
