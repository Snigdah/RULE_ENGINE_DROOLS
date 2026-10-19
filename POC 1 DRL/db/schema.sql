-- PostgreSQL schema for the Drools rule-engine POC (3 flows: transfer, loan, closure).
-- Library tables (4 tables, FK-linked) + this POC's RE_* business tables.
-- Hibernate ddl-auto=update creates the same objects from the entities; this script is the
-- manual equivalent for a DBA-managed (ddl-auto=none/validate) deployment.
--   psql -U postgres -d ruleengine -f db/schema.sql
--
-- Routing is 1:1 - one request_class maps to one flow - so request_class is a column on
-- RULE_FLOW, not a separate mapping table. A rule belongs to a specific group ROW (group_id),
-- so two flows can each have a "COMPLIANCE" group with completely different rules, and the same
-- display name may repeat across different groups. flow_id NULL on a group/field = shared (COMMON).
--
-- Upgrading an existing POC database created by the OLD entities? Drop the renamed legacy tables
-- first (they are no longer used):
--   DROP TABLE IF EXISTS flow_group, request_flow_map CASCADE;
--   DROP SEQUENCE IF EXISTS flow_group_seq, request_flow_map_seq;

-- ============================ LIBRARY TABLES ================================

-- ---- rule_flow : the flow master + router (holds request_class + fact_type once) --
CREATE SEQUENCE rule_flow_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE rule_flow (
    id            BIGINT        NOT NULL PRIMARY KEY,
    flow_code     VARCHAR(255)  NOT NULL UNIQUE,
    request_class VARCHAR(500)  UNIQUE,
    fact_type     VARCHAR(500)  NOT NULL,
    label         VARCHAR(255),
    active        BOOLEAN       NOT NULL DEFAULT TRUE
);

-- ---- rule_group : a flow's ordered agenda groups (flow_id NULL = shared COMMON) ----
CREATE SEQUENCE rule_group_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE rule_group (
    id         BIGINT        NOT NULL PRIMARY KEY,
    flow_id    BIGINT,
    group_code VARCHAR(100)  NOT NULL,
    order_no   INTEGER       NOT NULL,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_rule_group_flow FOREIGN KEY (flow_id) REFERENCES rule_flow (id),
    CONSTRAINT uk_rule_group UNIQUE (flow_id, group_code)
);
CREATE INDEX ix_rule_group_flow ON rule_group (flow_id);
-- Postgres treats NULLs as distinct in a UNIQUE, so enforce one shared group per code:
CREATE UNIQUE INDEX uk_rule_group_shared ON rule_group (group_code) WHERE flow_id IS NULL;

-- ---- rule_definition : the rules (each points at ONE group) ----------------
CREATE SEQUENCE rule_definition_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE rule_definition (
    id           BIGINT        NOT NULL PRIMARY KEY,
    group_id     BIGINT        NOT NULL,
    rule_name    VARCHAR(255)  NOT NULL,
    source_type  VARCHAR(20)   NOT NULL DEFAULT 'DRL',
    source_json  TEXT,
    drl_text     TEXT          NOT NULL,
    version_no   INTEGER       NOT NULL DEFAULT 1,
    status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by   VARCHAR(100),
    updated_at   TIMESTAMP     NOT NULL,
    CONSTRAINT fk_rule_def_group FOREIGN KEY (group_id) REFERENCES rule_group (id),
    CONSTRAINT uk_rule_definition_name UNIQUE (group_id, rule_name),
    CONSTRAINT ck_rule_definition_src CHECK (source_type IN ('DRL', 'BUILDER'))
);
CREATE INDEX ix_rule_definition_group ON rule_definition (group_id);

-- ---- rule_field_catalog : builder palette, scoped by flow (flow_id NULL = shared) --
CREATE SEQUENCE rule_field_catalog_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE rule_field_catalog (
    id             BIGINT        NOT NULL PRIMARY KEY,
    flow_id        BIGINT,
    field_key      VARCHAR(200)  NOT NULL,
    label          VARCHAR(200)  NOT NULL,
    data_type      VARCHAR(20)   NOT NULL,
    drl_path       VARCHAR(300)  NOT NULL,
    allowed_ops    VARCHAR(200)  NOT NULL,
    allowed_values VARCHAR(1000),
    CONSTRAINT fk_rule_field_flow FOREIGN KEY (flow_id) REFERENCES rule_flow (id),
    CONSTRAINT uk_rule_field UNIQUE (flow_id, field_key),
    CONSTRAINT ck_rule_field_type CHECK (data_type IN ('NUMBER', 'ENUM', 'BOOLEAN', 'STRING'))
);
CREATE INDEX ix_rule_field_flow ON rule_field_catalog (flow_id);
CREATE UNIQUE INDEX uk_rule_field_shared ON rule_field_catalog (field_key) WHERE flow_id IS NULL;

-- ============================ BUSINESS TABLES ================================

CREATE SEQUENCE re_user_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE re_user (
    id           BIGINT      NOT NULL PRIMARY KEY,
    user_id      VARCHAR(64) NOT NULL UNIQUE,
    kyc_verified BOOLEAN     NOT NULL DEFAULT FALSE,
    full_name    VARCHAR(128)
);

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

CREATE SEQUENCE re_customer_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE re_customer (
    id           BIGINT      NOT NULL PRIMARY KEY,
    user_id      VARCHAR(64) NOT NULL UNIQUE,
    credit_score INTEGER     NOT NULL,
    full_name    VARCHAR(128)
);

CREATE SEQUENCE re_account_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE re_account (
    id         BIGINT         NOT NULL PRIMARY KEY,
    account_no VARCHAR(64)    NOT NULL UNIQUE,
    balance    NUMERIC(18,2)  NOT NULL,
    status     VARCHAR(16)
);
