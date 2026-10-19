-- =============================================================================
--  Rule Engine approval POC - remove the two legacy hand-written COMMON rules
-- =============================================================================
--  Needed after merging v2_extend (library 3.0.2): rules now return their decision through
--  the library's RuleResult, and the POC no longer has BlockedUsers or the context's
--  setValid / setPermissionDenied. These two DRL rules (seeded by the old seed.ps1) call
--  exactly those, so they no longer compile - and one bad active rule stops the rule base
--  (and the service) from starting. The new seed.ps1 does not create them.
--
--  Builder rules need nothing: BuilderDrlUpgrader regenerates them at startup.
--  Touches only RULE_DEFINITION (a POC table from 01_create.sql). Plain SQL.
-- =============================================================================

DELETE FROM RULE_DEFINITION
 WHERE SOURCE_TYPE = 'DRL'
   AND RULE_NAME IN ('Block transaction for a blocked user', 'Block if KYC not verified')
   AND GROUP_ID IN (SELECT ID FROM RULE_GROUP WHERE FLOW_ID IS NULL AND GROUP_CODE = 'COMMON');

COMMIT;
