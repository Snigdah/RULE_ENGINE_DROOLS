-- =============================================================================
--  Rule Engine library - Oracle schema (reference DDL)
-- =============================================================================
--  The library ships with code-first entities, so with `ddl-auto: update` Hibernate
--  creates these objects automatically on first boot. In a BANKING/production database
--  that usually is not allowed - the DBA runs vetted DDL and the app runs with
--  `ddl-auto: validate`. This script is that vetted DDL: run it once per service schema,
--  then set `spring.jpa.hibernate.ddl-auto: validate`.
--
--  NOT auto-executed by the library (it lives under db/oracle/, not at the classpath root).
-- =============================================================================

-- ---- RULE_FILE : stored DRL files (runtime rule upload) ----------------------
CREATE TABLE RULE_FILE (
    ID          NUMBER(19)      NOT NULL,
    FILE_NAME   VARCHAR2(255)   NOT NULL,
    DRL_TEXT    CLOB            NOT NULL,
    ACTIVE      NUMBER(1)       DEFAULT 1 NOT NULL,
    VERSION_NO  NUMBER(10)      DEFAULT 1 NOT NULL,
    UPDATED_AT  TIMESTAMP       NOT NULL,
    CONSTRAINT PK_RULE_FILE PRIMARY KEY (ID),
    CONSTRAINT UK_RULE_FILE_NAME UNIQUE (FILE_NAME),
    CONSTRAINT CK_RULE_FILE_ACTIVE CHECK (ACTIVE IN (0, 1))
);
CREATE SEQUENCE RULE_FILE_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;

-- ---- FLOW_GROUP : flow -> ordered agenda groups -----------------------------
CREATE TABLE FLOW_GROUP (
    ID           NUMBER(19)     NOT NULL,
    FLOW_NAME    VARCHAR2(255)  NOT NULL,
    AGENDA_GROUP VARCHAR2(255)  NOT NULL,
    ORDER_NO     NUMBER(10)     NOT NULL,
    CONSTRAINT PK_FLOW_GROUP PRIMARY KEY (ID)
);
CREATE SEQUENCE FLOW_GROUP_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE INDEX IX_FLOW_GROUP_FLOW ON FLOW_GROUP (FLOW_NAME);

-- ---- REQUEST_FLOW_MAP : request DTO class -> flow ---------------------------
CREATE TABLE REQUEST_FLOW_MAP (
    ID            NUMBER(19)    NOT NULL,
    REQUEST_CLASS VARCHAR2(500) NOT NULL,
    FLOW_NAME     VARCHAR2(255) NOT NULL,
    CONSTRAINT PK_REQUEST_FLOW_MAP PRIMARY KEY (ID),
    CONSTRAINT UK_REQUEST_FLOW_CLASS UNIQUE (REQUEST_CLASS)
);
CREATE SEQUENCE REQUEST_FLOW_MAP_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;
