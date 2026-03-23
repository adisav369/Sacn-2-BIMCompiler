-- W004: Add proposal_status to C_OrderLine for rule-driven discipline lines.
--
-- Session A (ProjectOrderBlueprint.md §14.3): addDiscipline() creates
-- C_OrderLines with proposal_status='PROPOSED'. Architect reviews and
-- accepts (→'ACCEPTED') or rejects (DELETE).
--
-- iDempiere parallel: DocAction line status — Draft lines exist but are
-- not committed. Same concept: proposed MEP lines await architect approval.
--
-- Implementing ProjectOrderBlueprint.md §14.3 Session A — Witness: W-DM-TC5-1

ALTER TABLE C_OrderLine ADD COLUMN proposal_status TEXT DEFAULT 'ACCEPTED';

CREATE INDEX IF NOT EXISTS idx_orderline_proposal ON C_OrderLine(proposal_status);
