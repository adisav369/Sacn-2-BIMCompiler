/*
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 *
 * WorkflowOracle.java — ORACLE side of W-WF-HARDEN (ERP_EXECUTION_ROADMAP.md §PHASE B B-2,
 * prompts/FABLE5_WORKFLOW_ORACLE.md §W-3). Mirrors scripts/logic_oracle/LogicOracle.java (the B-1
 * technique): drive the REAL compiled iDempiere classes headless from
 * ~/idempiere-dev-setup/idempiere/org.adempiere.base/target/classes.
 *
 * WHAT RUNS REAL-COMPILED:
 *   - org.compiere.process.StateEngine — the workflow state machine itself. BOTH arms:
 *     the legal-transition table (getNewStateOptions/isValidNewState, StateEngine.java:349-396)
 *     AND the actual mutators via setState() (StateEngine.java:382-400 -> start/resume/suspend/
 *     complete/abort/terminate). Probed 2026-06-12: the lazy `log = CLogger.getCLogger(...)` inside
 *     the mutators loads fine headless on this classpath (unlike LogicEvaluator's CLogger STATIC,
 *     which is why B-1 replicated its body) — so NO omission is needed on the STATE arm.
 *   - org.compiere.process.DocAction constants (ACTION_Complete / STATUS_Completed / _WaitingConfirmation /
 *     _WaitingPayment / _Voided / _Closed / _Reversed) for the GATE arm below.
 *
 * NAMED OMISSIONS (the LogicOracle precedent — every static/class we could NOT drive, and why):
 *   - MWFNodeNext.isValidFor(MWFActivity) cannot run compiled headless: it takes a live MWFActivity,
 *     whose construction drags PO/Env/DB (persistence stack). Its std-user-workflow document gate
 *     (MWFNodeNext.java:215-243) is replicated VERBATIM below against the real compiled DocAction
 *     constants — body copied, only the `activity.getPO()` plumbing replaced by the (docStatus,
 *     docAction) inputs the PO would have supplied.
 *   - MWFNextCondition.evaluate (transition conditions) is NOT driven: it also needs activity+PO+DB,
 *     AND the traced corpus never exercises a conditioned transition (the single ad_wf_nextcondition
 *     row rides AD_WF_NodeNext 100 / workflow 115 Process_Requisition, which has no trace) — honest
 *     skip, counted in the witness §HARDEN-SKIPS.
 *
 * I/O (transport-safe, no JSON dep): each stdin line, tab-separated:
 *   id \t STATE \t <curState> \t <newState>   -> id \t <isValidNewState T|F> \t <setState T|F> \t <resultState>
 *   id \t OPTIONS \t <curState>               -> id \t <comma-joined getNewStateOptions>
 *   id \t GATE \t <docStatus> \t <docAction>  -> id \t T|F   (std-user transition document gate)
 */
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.compiere.process.DocAction;
import org.compiere.process.StateEngine;

public class WorkflowOracle {

    /**
     * MWFNodeNext.isValidFor:215-243 std-user-workflow block, VERBATIM (same conditions, same order),
     * minus only the PO fetch — the docStatus/docAction the PO would have returned are the inputs.
     * Returns "F" when the gate REJECTS the transition, "T" when it passes.
     */
    static String gate(String docStatus, String docAction) {
        if (!DocAction.ACTION_Complete.equals(docAction)
            || DocAction.STATUS_Completed.equals(docStatus)
            || DocAction.STATUS_WaitingConfirmation.equals(docStatus)
            || DocAction.STATUS_WaitingPayment.equals(docStatus)
            || DocAction.STATUS_Voided.equals(docStatus)
            || DocAction.STATUS_Closed.equals(docStatus)
            || DocAction.STATUS_Reversed.equals(docStatus)) {
            return "F";
        }
        return "T";
    }

    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            String[] p = line.split("\t", -1);
            String id = p[0], cmd = p[1];
            try {
                if ("STATE".equals(cmd)) {
                    // fresh real StateEngine per probe — param ctor (StateEngine.java:50-54), then the
                    // REAL legal-table check + the REAL mutator dispatch.
                    StateEngine table = new StateEngine(p[2]);
                    boolean valid = table.isValidNewState(p[3]);
                    StateEngine mut = new StateEngine(p[2]);
                    boolean applied = mut.setState(p[3]);
                    out.append(id).append('\t').append(valid ? 'T' : 'F').append('\t')
                       .append(applied ? 'T' : 'F').append('\t').append(mut.getState()).append('\n');
                } else if ("OPTIONS".equals(cmd)) {
                    out.append(id).append('\t').append(String.join(",", new StateEngine(p[2]).getNewStateOptions())).append('\n');
                } else if ("GATE".equals(cmd)) {
                    out.append(id).append('\t').append(gate(p[2], p[3])).append('\n');
                } else {
                    out.append(id).append("\tE:unknown-cmd ").append(cmd).append('\n');
                }
            } catch (Throwable t) {
                out.append(id).append("\tE:").append(t.getClass().getSimpleName()).append(' ')
                   .append(String.valueOf(t.getMessage())).append('\n');
            }
        }
        System.out.print(out);
    }
}
