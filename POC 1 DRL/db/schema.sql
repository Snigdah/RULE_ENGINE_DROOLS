-- PostgreSQL schema for the Drools transaction POC.
-- Library tables (rule_file, flow_group, request_flow_map) plus this service's RE_* tables.
-- Hibernate ddl-auto=update creates the same objects; this script is the manual equivalent.
--
--   psql -U postgres -d ruleengine -f db/schema.sql

-- ---- rule_file : stored DRL. Only active rows are compiled. -------------------
CREATE SEQUENCE rule_file_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE rule_file (
    id          BIGINT       NOT NULL PRIMARY KEY,
    file_name   VARCHAR(255) NOT NULL UNIQUE,
    drl_text    TEXT         NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version_no  INTEGER      NOT NULL DEFAULT 1,
    updated_at  TIMESTAMP    NOT NULL
);

-- ---- flow_group : flow name -> ordered agenda groups -------------------------
CREATE SEQUENCE flow_group_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE flow_group (
    id           BIGINT       NOT NULL PRIMARY KEY,
    flow_name    VARCHAR(255) NOT NULL,
    agenda_group VARCHAR(255) NOT NULL,
    order_no     INTEGER      NOT NULL
);

CREATE INDEX ix_flow_group_flow ON flow_group (flow_name);

-- ---- request_flow_map : request DTO class -> flow ----------------------------
CREATE SEQUENCE request_flow_map_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE request_flow_map (
    id            BIGINT       NOT NULL PRIMARY KEY,
    request_class VARCHAR(500) NOT NULL UNIQUE,
    flow_name     VARCHAR(255) NOT NULL
);

-- ---- Business tables owned by this POC ---------------------------------------
CREATE SEQUENCE re_user_limit_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE re_user_limit (
    id               BIGINT         NOT NULL PRIMARY KEY,
    user_id          VARCHAR(64)    NOT NULL,
    transaction_mode VARCHAR(32),
    dr_cr_type       VARCHAR(8),
    limit_amount     NUMERIC(18,2)  NOT NULL
);

CREATE SEQUENCE re_product_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE re_product (
    id             BIGINT      NOT NULL PRIMARY KEY,
    source_account VARCHAR(64) NOT NULL UNIQUE,
    dr_res         INTEGER
);

CREATE SEQUENCE re_user_block_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE re_user_block (
    id       BIGINT      NOT NULL PRIMARY KEY,
    user_id  VARCHAR(64) NOT NULL UNIQUE,
    blocked  BOOLEAN     NOT NULL DEFAULT FALSE
);
