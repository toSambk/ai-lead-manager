--liquibase formatted sql
--changeset ai-lead-manager:002-sessions
CREATE TABLE public.spring_session (
    primary_id char(36) NOT NULL PRIMARY KEY,
    session_id char(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval int NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name varchar(100)
);

CREATE UNIQUE INDEX spring_session_ix1 ON public.spring_session (session_id);
CREATE INDEX spring_session_ix2 ON public.spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON public.spring_session (principal_name);

CREATE TABLE public.spring_session_attributes (
    session_primary_id char(36) NOT NULL REFERENCES public.spring_session(primary_id) ON DELETE CASCADE,
    attribute_name varchar(200) NOT NULL,
    attribute_bytes bytea NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name)
);
