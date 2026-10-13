-- =====================================================================================
-- Migration for library 2.0.0 — route by REQUEST class, not the context/fact type.
--   REQUEST_FLOW_MAP: fact_type  ->  request_type (the DTO)   [2 columns now]
--   FLOW_GROUP:       + fact_type (the context the flow's rules match)
-- Run ONCE against the ruleengine DB, BEFORE starting the 2.0.0 backend. Idempotent.
-- =====================================================================================
BEGIN;

-- 1) FLOW_GROUP gets FACT_TYPE (the context type), backfilled per flow
ALTER TABLE flow_group ADD COLUMN IF NOT EXISTS fact_type VARCHAR(500);
UPDATE flow_group SET fact_type = 'com.example.droolspoc.model.ValidationContext'        WHERE flow_name = 'TRANSFER_TRANSACTION';
UPDATE flow_group SET fact_type = 'com.example.droolspoc.model.LoanValidationContext'    WHERE flow_name = 'LOAN_APPLICATION';
UPDATE flow_group SET fact_type = 'com.example.droolspoc.model.ClosureValidationContext' WHERE flow_name = 'ACCOUNT_CLOSURE';

-- 2) REQUEST_FLOW_MAP: add REQUEST_TYPE (the request DTO), backfill, drop old FACT_TYPE
ALTER TABLE request_flow_map ADD COLUMN IF NOT EXISTS request_type VARCHAR(500);
UPDATE request_flow_map SET request_type = 'com.example.droolspoc.dto.TransactionRequest'  WHERE flow_name = 'TRANSFER_TRANSACTION';
UPDATE request_flow_map SET request_type = 'com.example.droolspoc.dto.LoanRequest'         WHERE flow_name = 'LOAN_APPLICATION';
UPDATE request_flow_map SET request_type = 'com.example.droolspoc.dto.AccountCloseRequest' WHERE flow_name = 'ACCOUNT_CLOSURE';
ALTER TABLE request_flow_map ALTER COLUMN request_type SET NOT NULL;
ALTER TABLE request_flow_map DROP CONSTRAINT IF EXISTS uk_request_flow_request;
ALTER TABLE request_flow_map ADD  CONSTRAINT uk_request_flow_request UNIQUE (request_type);
-- remove the old context column + its unique constraint (CASCADE drops the dependent unique)
ALTER TABLE request_flow_map DROP COLUMN IF EXISTS fact_type CASCADE;

COMMIT;

-- Sanity:
--   SELECT request_type, flow_name FROM request_flow_map;
--   SELECT DISTINCT flow_name, fact_type FROM flow_group ORDER BY flow_name;
