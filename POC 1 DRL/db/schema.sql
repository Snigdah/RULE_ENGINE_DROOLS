-- PostgreSQL schema for the Drools transaction-validation POC
--   psql -U postgres -d ruleengine -f db/schema.sql

CREATE TABLE user_limit (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           VARCHAR(50)   NOT NULL,
    transaction_mode  VARCHAR(20)   NOT NULL,   -- 'TRANSFER' | 'CREDIT'
    dr_cr_type        VARCHAR(10)   NOT NULL,   -- 'DR'       | 'CR'
    limit_amount      NUMERIC(19,2) NOT NULL,
    CONSTRAINT uq_user_limit UNIQUE (user_id, transaction_mode, dr_cr_type),
    CONSTRAINT ck_user_limit_mode CHECK (transaction_mode IN ('TRANSFER','CREDIT')),
    CONSTRAINT ck_user_limit_drcr CHECK (dr_cr_type IN ('DR','CR')),
    CONSTRAINT ck_user_limit_amt  CHECK (limit_amount >= 0)
);

CREATE TABLE product (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_account  VARCHAR(50)  NOT NULL UNIQUE,
    product_type    VARCHAR(30)  NOT NULL,      -- e.g. 'DEPOSIT'
    frequency       VARCHAR(30),                -- e.g. 'MONTHLY'
    dr_res          SMALLINT     NOT NULL DEFAULT 0,   -- debit-restriction flag 0/1
    CONSTRAINT ck_product_dr_res CHECK (dr_res IN (0,1))
);
