/*
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 *
 * ConfirmOracle.java — ORACLE side of W-WH-CONFIRM-ORACLE (POS_GAP_CLOSE.md §G-3): drive a REAL
 * doctype-148 ("MM Shipment with Pick", IsPickQAConfirm=Y) cycle through the REAL compiled iDempiere
 * model layer (org.adempiere.base/target/classes + the p2 plugins closure — the LogicOracle /
 * WorkflowOracle technique, now at the DOCUMENT level) against the live Docker Postgres
 * `idempiere_test` (GardenWorld, client 11).
 *
 * The cycle (everything inside ONE transaction, ROLLED BACK at the end — the database is left
 * byte-identical; only native DocumentNo sequences may tick, a named side effect):
 *   1. create MInOut doctype 148, SO-side (C-), warehouse 103, BP 112, MovementDate 2006-12-15
 *      (Dec-06 = an OPEN 'MMS' period in the GardenWorld seed), ONE line: product 137 Mulch,
 *      locator 101, Qty 2 (stock 50 — real storage).
 *   2. processIt(CO) #1 → EXPECT prepareIt spawns the PC confirm and completeIt GATES: DocStatus IP.
 *   3. short-pick: ConfirmedQty 2→1 on the confirm line, complete the M_InOutConfirm → EXPECT
 *      ship-line MovementQty reduced to 1 (PickQA processLine), DifferenceQty 1, NO difference doc
 *      on the SO side, on-hand still UNMOVED.
 *   4. processIt(CO) #2 → EXPECT DocStatus CO and on-hand 50→49 (moves at confirm-complete+re-complete,
 *      by the PICKED qty).
 * Capture: §ORACLE key=value lines per stage (status walk, confirm shape, qty spine, on-hand ladder).
 * The JS witness diffs inout_confirm.js's fold against THESE rows — engine == real iDempiere.
 *
 * NON-INVENT: every id above is a real GardenWorld row read from idempiere_test before the run.
 */
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Properties;

import org.adempiere.util.ServerContext;
import org.compiere.Adempiere;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MClientInfo;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutConfirm;
import org.compiere.model.MInOutLine;
import org.compiere.model.MInOutLineConfirm;
import org.compiere.model.MRole;
import org.compiere.process.DocAction;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Language;
import org.compiere.util.Trx;

public class ConfirmOracle {

    static final int CLIENT = 11, ORG = 11, USER = 101, ROLE = 102, WAREHOUSE = 103;
    static final int DOCTYPE_MMS_PICK = 148, BP = 112, BP_LOC = 108, PRODUCT = 137, LOCATOR = 101;
    static final Timestamp MOVE_DATE = Timestamp.valueOf("2006-12-15 00:00:00"); // Dec-06: OPEN MMS period

    static BigDecimal onhand(String trxName) {
        return DB.getSQLValueBDEx(trxName,
            "SELECT COALESCE(SUM(QtyOnHand),0) FROM M_StorageOnHand WHERE M_Product_ID=? AND M_Locator_ID=?",
            PRODUCT, LOCATOR);
    }

    public static void main(String[] args) throws Exception {
        Adempiere.startup(false);
        ServerContext.setCurrentInstance(new Properties());
        Properties ctx = Env.getCtx();
        Env.setContext(ctx, Env.AD_CLIENT_ID, CLIENT);
        Env.setContext(ctx, Env.AD_ORG_ID, ORG);
        Env.setContext(ctx, Env.AD_USER_ID, USER);
        Env.setContext(ctx, Env.AD_ROLE_ID, ROLE);
        Env.setContext(ctx, Env.M_WAREHOUSE_ID, WAREHOUSE);
        Env.setContext(ctx, Env.LANGUAGE_NAME, Language.getLanguage("en_US").getName());
        Env.setContext(ctx, Env.DATE, MOVE_DATE);
        Env.verifyLanguage(ctx, Language.getLanguage("en_US"));
        Env.setContext(ctx, Env.LANGUAGE, Language.getLanguage("en_US").getAD_Language());
        Env.setContext(ctx, Env.RUNNING_UNIT_TESTING_TEST_CASE, true);
        MRole.getDefault(ctx, false);
        MAcctSchema primary = MAcctSchema.get(ctx, MClientInfo.get(ctx, CLIENT).getC_AcctSchema1_ID());
        Env.setContext(ctx, "$C_AcctSchema_ID", primary.getC_AcctSchema_ID());
        Env.setContext(ctx, "$C_Currency_ID", primary.getC_Currency_ID());

        Trx trx = Trx.get(Trx.createTrxName("ConfirmOracle_"), true);
        trx.start();
        String trxName = trx.getTrxName();
        try {
            System.out.println("§ORACLE stage=onhand-0 qty=" + onhand(trxName).toPlainString());

            // 1. the shipment — doctype 148, one real line
            MInOut io = new MInOut(ctx, 0, trxName);
            io.setAD_Org_ID(ORG);
            io.setC_DocType_ID(DOCTYPE_MMS_PICK);
            io.setIsSOTrx(true);
            io.setMovementType(MInOut.MOVEMENTTYPE_CustomerShipment);
            io.setM_Warehouse_ID(WAREHOUSE);
            io.setC_BPartner_ID(BP);
            io.setC_BPartner_Location_ID(BP_LOC);
            io.setMovementDate(MOVE_DATE);
            io.setDateAcct(MOVE_DATE);
            io.saveEx();
            MInOutLine line = new MInOutLine(io);
            line.setM_Product_ID(PRODUCT);
            line.setM_Locator_ID(LOCATOR);
            line.setQty(new BigDecimal("2"));
            line.saveEx();
            System.out.println("§ORACLE stage=created m_inout_id=" + io.get_ID() + " docstatus=" + io.getDocStatus()
                + " line_qty=" + line.getMovementQty().toPlainString());

            // 2. Complete #1 — prepareIt spawns the PC confirm; completeIt gates → IP
            io.setDocAction(DocAction.ACTION_Complete);
            boolean ok1 = io.processIt(DocAction.ACTION_Complete);
            io.load(trxName);
            MInOutConfirm[] confirms = io.getConfirmations(true);
            System.out.println("§ORACLE stage=complete1 ok=" + ok1 + " docstatus=" + io.getDocStatus()
                + " confirms=" + confirms.length
                + (confirms.length > 0 ? " confirmtype=" + confirms[0].getConfirmType()
                    + " confirm_docstatus=" + confirms[0].getDocStatus()
                    + " confirm_processed=" + (confirms[0].isProcessed() ? "Y" : "N") : ""));
            System.out.println("§ORACLE stage=onhand-after-gate qty=" + onhand(trxName).toPlainString());
            if (confirms.length != 1) { System.out.println("§ORACLE FATAL no-confirm-spawned"); trx.rollback(); trx.close(); System.exit(2); }

            MInOutConfirm pc = confirms[0];
            MInOutLineConfirm[] clines = pc.getLines(true);
            for (MInOutLineConfirm cl : clines)
                System.out.println("§ORACLE stage=confirm-line m_inoutlineconfirm_id=" + cl.get_ID()
                    + " target=" + cl.getTargetQty().toPlainString()
                    + " confirmed=" + cl.getConfirmedQty().toPlainString()
                    + " difference=" + cl.getDifferenceQty().toPlainString()
                    + " scrapped=" + cl.getScrappedQty().toPlainString());

            // 3. short-pick 2→1 and complete the confirmation
            clines[0].setConfirmedQty(new BigDecimal("1"));
            clines[0].saveEx();
            pc.setDocAction(DocAction.ACTION_Complete);
            boolean ok2 = pc.processIt(DocAction.ACTION_Complete);
            pc.load(trxName);
            clines = pc.getLines(true);
            line.load(trxName);
            System.out.println("§ORACLE stage=confirm-complete ok=" + ok2 + " confirm_docstatus=" + pc.getDocStatus()
                + " confirm_processed=" + (pc.isProcessed() ? "Y" : "N")
                + " line_target=" + clines[0].getTargetQty().toPlainString()
                + " line_confirmed=" + clines[0].getConfirmedQty().toPlainString()
                + " line_difference=" + clines[0].getDifferenceQty().toPlainString()
                + " ship_movementqty=" + line.getMovementQty().toPlainString()
                + " ship_pickedqty=" + line.getPickedQty().toPlainString());
            // SO-side difference must spawn NO document (createDifferenceDoc SO branch): count linked docs
            int cmCount = DB.getSQLValueEx(trxName, "SELECT COUNT(*) FROM C_InvoiceLine WHERE M_InOutLine_ID=?", line.get_ID());
            int invCount = DB.getSQLValueEx(trxName, "SELECT COUNT(*) FROM M_InventoryLine WHERE M_InOutLine_ID=?", line.get_ID());
            System.out.println("§ORACLE stage=difference-docs creditmemo_lines=" + cmCount + " inventory_lines=" + invCount);
            System.out.println("§ORACLE stage=onhand-after-confirm qty=" + onhand(trxName).toPlainString());

            // 4. Complete #2 — the gate is clear; on-hand moves by the PICKED qty
            boolean ok3 = io.processIt(DocAction.ACTION_Complete);
            io.load(trxName);
            line.load(trxName);
            System.out.println("§ORACLE stage=complete2 ok=" + ok3 + " docstatus=" + io.getDocStatus()
                + " ship_movementqty=" + line.getMovementQty().toPlainString());
            System.out.println("§ORACLE stage=onhand-final qty=" + onhand(trxName).toPlainString());
            System.out.println("§ORACLE stage=done rollback=Y");
        } catch (Exception e) {
            System.out.println("§ORACLE FATAL " + e.getClass().getSimpleName() + " " + String.valueOf(e.getMessage()).replace('\n', ' '));
            e.printStackTrace();
            trx.rollback(); trx.close();
            System.exit(2);
        }
        trx.rollback();   // leave idempiere_test byte-identical (named: native sequences may have ticked)
        trx.close();
        System.exit(0);
    }
}
