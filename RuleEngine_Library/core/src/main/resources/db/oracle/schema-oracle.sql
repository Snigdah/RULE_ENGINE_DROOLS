-- =============================================================================
--  Rule Engine library - Oracle schema (reference DDL) - 4 tables, FK-linked
-- =============================================================================
--  Code-first entities drive the tables. In a BANKING/production database the DBA
--  runs this vetted DDL and the app runs with `spring.jpa.hibernate.ddl-auto: none`
--  (or `validate`). Run once per service schema. Create order honours the FKs.
--
--  Tables:
--    RULE_FLOW          - the flow master + routing. One row per flow; holds the
--                         routing key (REQUEST_CLASS) and the FACT_TYPE once.
--    RULE_GROUP         - a flow's agenda groups, ordered; FLOW_ID NULL = shared (COMMON)
--    RULE_DEFINITION    - the rules; each points at ONE group (GROUP_ID)
--    RULE_FIELD_CATALOG - builder palette fields; FLOW_ID NULL = shared field
--
--  Routing is 1:1 - one REQUEST_CLASS maps to exactly one flow - so the router and
--  the flow master are the same row (no separate mapping table). A rule belongs to a
--  specific group ROW, so two flows can each have a "COMPLIANCE" group with completely
--  different rules; the same rule name is allowed across different groups (UNIQUE is
--  per group, not global).
-- =============================================================================

-- ---- RULE_FLOW : the flow master + router ----------------------------------
--  Developer-managed (seeded by DBA). REQUEST_CLASS is the fully-qualified request
--  DTO class the caller passes to execute(context, <RequestClass>.class, ...) - the
--  routing key; it is 1:1 with the flow, so it lives here as a UNIQUE column.
--  FACT_TYPE is the fully-qualified context class the flow's rules match on; the DRL
--  generator reads it once from here. (Shared COMMON rules match the base context and
--  are hand-written DRL, so they need no FACT_TYPE of their own.)
CREATE TABLE RULE_FLOW (
    ID            NUMBER(19)    NOT NULL,
    FLOW_CODE     VARCHAR2(255) NOT NULL,
    REQUEST_CLASS VARCHAR2(500),
    FACT_TYPE     VARCHAR2(500) NOT NULL,
    LABEL         VARCHAR2(255),
    ACTIVE        NUMBER(1)     DEFAULT 1 NOT NULL,
    CONSTRAINT PK_RULE_FLOW PRIMARY KEY (ID),
    CONSTRAINT UK_RULE_FLOW_CODE UNIQUE (FLOW_CODE),
    CONSTRAINT UK_RULE_FLOW_REQUEST UNIQUE (REQUEST_CLASS),
    CONSTRAINT CK_RULE_FLOW_ACTIVE CHECK (ACTIVE IN (0, 1))
);
CREATE SEQUENCE RULE_FLOW_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;

-- ---- RULE_GROUP : a flow's ordered agenda groups ----------------------------
--  FLOW_ID NULL = a shared group (COMMON) that every flow runs first.
--  GROUP_CODE is the Drools agenda-group name. Ordering: shared groups first,
--  then the flow's own groups by ORDER_NO.
CREATE TABLE RULE_GROUP (
    ID         NUMBER(19)    NOT NULL,
    FLOW_ID    NUMBER(19),
    GROUP_CODE VARCHAR2(100) NOT NULL,
    ORDER_NO   NUMBER(10)    NOT NULL,
    ACTIVE     NUMBER(1)     DEFAULT 1 NOT NULL,
    CONSTRAINT PK_RULE_GROUP PRIMARY KEY (ID),
    CONSTRAINT FK_RULE_GROUP_FLOW FOREIGN KEY (FLOW_ID) REFERENCES RULE_FLOW (ID),
    CONSTRAINT UK_RULE_GROUP UNIQUE (FLOW_ID, GROUP_CODE),
    CONSTRAINT CK_RULE_GROUP_ACTIVE CHECK (ACTIVE IN (0, 1))
);
CREATE SEQUENCE RULE_GROUP_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE INDEX IX_RULE_GROUP_FLOW ON RULE_GROUP (FLOW_ID);

-- ---- RULE_DEFINITION : the rules -------------------------------------------
--  Each rule points at ONE group (GROUP_ID). SOURCE_TYPE = 'BUILDER' -> SOURCE_JSON
--  set, DRL_TEXT generated; 'DRL' -> SOURCE_JSON null, DRL_TEXT hand-written. The
--  engine compiles DRL_TEXT of rows where ACTIVE = 1. UNIQUE is (GROUP_ID, RULE_NAME),
--  so the same display name may exist in two different groups.
CREATE TABLE RULE_DEFINITION (
    ID          NUMBER(19)    NOT NULL,
    GROUP_ID    NUMBER(19)    NOT NULL,
    RULE_NAME   VARCHAR2(255) NOT NULL,
    SOURCE_TYPE VARCHAR2(20)  DEFAULT 'DRL' NOT NULL,
    SOURCE_JSON CLOB,
    DRL_TEXT    CLOB          NOT NULL,
    VERSION_NO  NUMBER(10)    DEFAULT 1 NOT NULL,
    STATUS      VARCHAR2(20)  DEFAULT 'ACTIVE' NOT NULL,
    ACTIVE      NUMBER(1)     DEFAULT 1 NOT NULL,
    CREATED_BY  VARCHAR2(100),
    UPDATED_AT  TIMESTAMP     NOT NULL,
    CONSTRAINT PK_RULE_DEFINITION PRIMARY KEY (ID),
    CONSTRAINT FK_RULE_DEF_GROUP FOREIGN KEY (GROUP_ID) REFERENCES RULE_GROUP (ID),
    CONSTRAINT UK_RULE_DEFINITION_NAME UNIQUE (GROUP_ID, RULE_NAME),
    CONSTRAINT CK_RULE_DEFINITION_ACTIVE CHECK (ACTIVE IN (0, 1)),
    CONSTRAINT CK_RULE_DEFINITION_SRC CHECK (SOURCE_TYPE IN ('DRL', 'BUILDER'))
);
CREATE SEQUENCE RULE_DEFINITION_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE INDEX IX_RULE_DEFINITION_GROUP ON RULE_DEFINITION (GROUP_ID);

-- ---- RULE_FIELD_CATALOG : builder palette, scoped by flow -------------------
--  FLOW_ID holds a specific flow or NULL (shared = shows in every flow).
--  The UI reads: WHERE FLOW_ID = :flowId OR FLOW_ID IS NULL.
CREATE TABLE RULE_FIELD_CATALOG (
    ID             NUMBER(19)    NOT NULL,
    FLOW_ID        NUMBER(19),
    FIELD_KEY      VARCHAR2(200) NOT NULL,
    LABEL          VARCHAR2(200) NOT NULL,
    DATA_TYPE      VARCHAR2(20)  NOT NULL,
    DRL_PATH       VARCHAR2(300) NOT NULL,
    ALLOWED_OPS    VARCHAR2(200) NOT NULL,
    ALLOWED_VALUES VARCHAR2(1000),
    CONSTRAINT PK_RULE_FIELD_CATALOG PRIMARY KEY (ID),
    CONSTRAINT FK_RULE_FIELD_FLOW FOREIGN KEY (FLOW_ID) REFERENCES RULE_FLOW (ID),
    CONSTRAINT UK_RULE_FIELD UNIQUE (FLOW_ID, FIELD_KEY),
    CONSTRAINT CK_RULE_FIELD_TYPE CHECK (DATA_TYPE IN ('NUMBER', 'ENUM', 'BOOLEAN', 'STRING'))
);
CREATE SEQUENCE RULE_FIELD_CATALOG_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE INDEX IX_RULE_FIELD_FLOW ON RULE_FIELD_CATALOG (FLOW_ID);
