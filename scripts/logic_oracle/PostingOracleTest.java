/*
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 *
 * PostingOracleTest.java — ORACLE side of W-POST-B3 §W-2 (prompts/FABLE5_B3_POSTING_ORACLE.md).
 * Mirrors scripts/logic_oracle/{LogicOracle,WorkflowOracle,ConfirmOracle}.java — drive the REAL
 * compiled iDempiere classes; unlike those, this one runs INSIDE the vendor's own OSGi test harness
 * (org.idempiere.test, tycho-surefire) because POS_GAP_CLOSE.md §G-3 proved bare
 * Adempiere.startup(false) cannot run headless (SecureEngine needs a live OSGi BundleContext).
 * The vendor's FixedAssetsTest (IDEMPIERE-5474) is the direct precedent: with
 * ad_sysconfig CLIENT_ACCOUNTING='I', MWorkflow.runDocumentActionWorkflow(doc, CO) completes AND
 * posts synchronously — the fact_acct rows written are the REAL compiled Doc_<Class> output.
 *
 * TARGET DB: the SCRATCH clone idempiere_b3 (createdb -T idempiere_test), selected via
 * -Didempiere.home=<scratch home> whose idempiere.properties points DBname=idempiere_b3.
 * This test COMMITS (AbstractTestCase default is rollback) — into the scratch clone ONLY, which
 * scripts/generate_post_oracle.sh creates before and DROPS after the fact_acct capture.
 *
 * SEED (GardenWorld-model, honestly labelled — USER RULING 2026-07-17: preparing seed INPUT
 * documents is sanctioned; hand-authoring the expected fact_acct stays banned; nothing here writes
 * a fact row — only the real posting engine does):
 *   0. Vendor recipe verbatim: UseLifeMonths=18 on A_Asset_Group_Acct of group EQUIPMENT/50007
 *      (both schemas 101+200000 — GardenWorld ships uselife 0, div-by-zero otherwise).
 *      Seed C_DocType DocBaseType='FDP' for client 11 (none exists; MDepreciationEntry.prepareIt
 *      needs one for testPeriodOpen).
 *   1. Asset A + MAssetAddition Manual/Capital 12000, salvage 2000 → CO (posts A_Asset_Addition).
 *   2. MDepreciationEntry schema 101, DateAcct = A's first unprocessed depexp month → CO
 *      (posts A_Depreciation_Entry; schema-200000 side left unentered on purpose — exercises the
 *      other-schema ∅ branch of Doc_DepreciationEntry).
 *   3. Seed A_Reval_Cost_Offset_Acct on asset A's a_asset_acct rows (:= the row's own
 *      A_Disposal_Revenue_Acct — GardenWorld has no reval account; config INPUT, read identically
 *      by poster and engine); MAssetReval cost+1000 / accum+100, dated in the last-depreciated
 *      month → CO (posts A_Asset_Reval).
 *   4. Asset B + addition → CO; MAssetTransfer B (schema 101) old accts = B's own a_asset_acct row
 *      (copied, not invented), new asset/accumdep accts = group 50000's (Furniture) combos, dated
 *      addition+1 day so the validfrom time-slice created by completeIt stays derivable → CO
 *      (posts A_Asset_Transfer).
 *   5. Asset C + addition → CO; MAssetDisposed C method=Simple → CO (posts A_Asset_Disposed via
 *      the A_Asset_Change row createDisposal writes).
 *   6. MProjectIssue project 101 (category N → PJ_WIP side), locator 101 / product 137 Mulch /
 *      qty 1 → CO (posts C_ProjectIssue at MCost current cost).
 *
 * Every id above is a real GardenWorld row verified live 2026-07-17 (§W-1 triage + facts session).
 * Output: §ORACLE key=value lines — the generate script greps them; the capture itself is external
 * (psql over the committed scratch DB, the extract_fact_acct.sh shape).
 */
package org.idempiere.test.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;

import org.compiere.acct.DocManager;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MAsset;
import org.compiere.model.MAssetAcct;
import org.compiere.model.MAssetAddition;
import org.compiere.model.MAssetDisposed;
import org.compiere.model.MAssetGroupAcct;
import org.compiere.model.MAssetReval;
import org.compiere.model.MAssetTransfer;
import org.compiere.model.MDepreciationEntry;
import org.compiere.model.MDepreciationWorkfile;
import org.compiere.model.MDocType;
import org.compiere.model.MProject;
import org.compiere.model.MProjectIssue;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.wf.MWorkflow;
import org.idempiere.test.AbstractTestCase;
import org.junit.jupiter.api.Test;

public class PostingOracleTest extends AbstractTestCase {

	static final int GROUP_EQUIPMENT = 50007;       // has a_asset_group_acct rows in schemas 101 + 200000
	static final int GROUP_FURNITURE = 50000;       // transfer target accts (asset 200010 / accumdep 200011)
	static final int PRODUCT_ASSET_VEHICLE = 200001;  // "Asset Vehicle", producttype A (this seed's id — 200000 does not exist here)
	static final int PRODUCT_MULCH = 137;           // stocked, costed (the ConfirmOracle product)
	static final int LOCATOR_HQ = 101;
	static final int PROJECT_LANDSCAPE = 101;       // projectcategory N → PJ_WIP side

	private void say(String s) { System.out.println("§ORACLE " + s); }

	private void driveCO(PO po, String what) {
		// complete via the REAL DocumentEngine (DocAction.processIt), then post via the REAL posting
		// entrypoint (DocManager.postDocument — what postImmediate/AcctProcessor call). Direct engine
		// instead of MWorkflow.runDocumentActionWorkflow: the wf-engine's own AD_WF_Process bookkeeping
		// hits an optimistic-lock failure on the 2nd in-trx run (observed run 5) — the POSTING path
		// under test is identical either way.
		DocAction doc = (DocAction) po;
		boolean ok;
		try { ok = doc.processIt(DocAction.ACTION_Complete); }
		catch (Exception e) { throw new IllegalStateException(what + " processIt threw: " + e.getMessage(), e); }
		assertTrue(ok, what + " processIt(CO) failed: " + doc.getProcessMsg());
		// DocumentEngine.processIt already ran saveEx + postIt (immediate accounting) itself
		// (DocumentEngine.java:363-364); re-read the row and only REPOST (surfacing the real error)
		// if the internal post did not land.
		po.saveEx();
		po.load(getTrxName());
		Object rawPosted = po.get_Value("Posted");
		boolean posted = "Y".equals(rawPosted) || Boolean.TRUE.equals(rawPosted);
		String postErr = null;
		if (!posted) {
			MAcctSchema[] ass = MAcctSchema.getClientAcctSchema(Env.getCtx(), getAD_Client_ID());
			postErr = DocManager.postDocument(ass, po.get_Table_ID(), po.get_ID(), true, true, getTrxName());
			po.load(getTrxName());
			rawPosted = po.get_Value("Posted");
			posted = "Y".equals(rawPosted) || Boolean.TRUE.equals(rawPosted);
		}
		String docStatus = (String) po.get_Value("DocStatus");
		say("class=" + what + " table=" + po.get_TableName() + " record_id=" + po.get_ID()
			+ " docstatus=" + docStatus + " posted=" + (posted ? "Y" : "N") + " rawPosted=" + rawPosted
			+ (postErr != null ? " postErr=" + postErr : ""));
		assertEquals(DocAction.STATUS_Completed, docStatus, what + " not completed");
		assertTrue(posted, what + " not posted" + (postErr != null ? " — " + postErr : ""));
		commit();   // scratch clone: null-trx readers (Doc_AssetReval:105, Doc_AssetDisposed:96, caches) must see this
	}

	private MAsset newAsset(Properties ctx, String key) {
		MAsset a = new MAsset(ctx, 0, getTrxName());
		a.setValue(key);
		a.setName(key);
		a.setA_Asset_Group_ID(GROUP_EQUIPMENT);
		a.setM_Product_ID(PRODUCT_ASSET_VEHICLE);
		a.setIsOwned(true);
		a.setIsDepreciated(true);
		a.saveEx();
		return a;
	}

	private MAssetAddition newAddition(Properties ctx, MAsset a, String amt) {
		MAssetAddition aa = new MAssetAddition(ctx, 0, getTrxName());
		aa.setA_Asset_ID(a.getA_Asset_ID());
		aa.setDateDoc(new Timestamp(System.currentTimeMillis()));
		aa.setA_SourceType(MAssetAddition.A_SOURCETYPE_Manual);
		aa.setAssetAmtEntered(new BigDecimal(amt));
		aa.setAssetSourceAmt(aa.getAssetAmtEntered());
		aa.setA_Salvage_Value(new BigDecimal("2000.0"));
		aa.saveEx();
		return aa;
	}

	@Test
	public void b3PostingOracle() {
		Properties ctx = Env.getCtx();
		String trxName = getTrxName();

		// guard: this must run on the scratch clone, never the shared idempiere_test
		String dbName = DB.getSQLValueStringEx(trxName, "SELECT current_database()");
		say("db=" + dbName);
		assertEquals("idempiere_b3", dbName, "PostingOracleTest must run on the scratch clone idempiere_b3");

		// ── 0. vendor recipe: uselife 18 on the EQUIPMENT group acct rows (both schemas) ──
		List<MAssetGroupAcct> gas = new Query(ctx, MAssetGroupAcct.Table_Name,
				"A_Asset_Group_ID=?", trxName).setParameters(GROUP_EQUIPMENT).list();
		for (MAssetGroupAcct aga : gas) {
			aga.setUseLifeMonths(18);
			aga.setUseLifeYears(Env.ZERO);
			aga.setUseLifeMonths_F(18);
			aga.setUseLifeYears_F(Env.ZERO);
			// this seed's group-acct rows ship method/convention NULL and a_asset_acct marks them
			// mandatory (probe 7): reference the SYSTEM rows — MDI (Month-Current, 50000) +
			// FMCON (Full Month, 50000) — config INPUT, both real dictionary rows
			aga.setA_Depreciation_Method_ID(50000);
			aga.setA_Depreciation_Conv_ID(50000);
			aga.setA_Depreciation_Method_F_ID(50000);
			aga.setA_Depreciation_Conv_F_ID(50000);
			aga.saveEx();
		}
		say("seed=uselife18+MDI/FMCON group=" + GROUP_EQUIPMENT + " rows=" + gas.size());

		// FDP doctype for client 11 (none in GardenWorld; MDocType picks the default GL category)
		MDocType fdp = new MDocType(ctx, "FDP", "Depreciation Entry (B3 seed)", trxName);
		fdp.saveEx();
		say("seed=doctype-FDP c_doctype_id=" + fdp.get_ID());
		commit();   // config seed must be visible to null-trx cache loads (MDocType.get, group-acct copies)

		// ── 1. Asset A + addition (the vendor FixedAssetsTest recipe verbatim) ──
		MAsset assetA = newAsset(ctx, "b3_asset_a");
		MAssetAddition addA = newAddition(ctx, assetA, "12000.0");
		driveCO(addA, "A_Asset_Addition_A");
		say("asset_a=" + assetA.getA_Asset_ID() + " addition_a=" + addA.get_ID());

		// ── 2. depreciation entry, schema 101, first unprocessed depexp month of asset A ──
		Timestamp firstExp = DB.getSQLValueTSEx(trxName,
			"SELECT MIN(DateAcct) FROM A_Depreciation_Exp WHERE A_Asset_ID=? AND C_AcctSchema_ID=101 AND Processed='N'",
			assetA.getA_Asset_ID());
		assertTrue(firstExp != null, "addition A built no depexp rows (schema 101)");
		say("depexp_first_month=" + firstExp);
		MDepreciationEntry de = new MDepreciationEntry(ctx, 0, trxName);
		de.setC_DocType_ID(fdp.get_ID());
		de.setDateAcct(firstExp);
		de.setDateDoc(firstExp);        // NOT NULL in this seed's schema
		de.setIsApproved(false);        // NOT NULL, no default in this seed's schema
		de.saveEx();      // afterSave auto-selects that month's unassigned schema-101 depexp lines
		int selected = DB.getSQLValueEx(trxName,
			"SELECT COUNT(*) FROM A_Depreciation_Exp WHERE A_Depreciation_Entry_ID=?", de.get_ID());
		say("depentry=" + de.get_ID() + " selected_lines=" + selected);
		assertTrue(selected > 0, "depreciation entry selected no depexp lines");
		driveCO(de, "A_Depreciation_Entry");

		// ── 3. reval on asset A (needs a processed month; both cost AND accum must change) ──
		//     seed the missing reval offset acct := the row's own disposal-revenue combo (config INPUT)
		List<MAssetAcct> aaccts = new Query(ctx, MAssetAcct.Table_Name,
				"A_Asset_ID=?", trxName).setParameters(assetA.getA_Asset_ID()).list();
		for (MAssetAcct acct : aaccts) {
			acct.setA_Reval_Cost_Offset_Acct(acct.getA_Disposal_Revenue_Acct());
			acct.saveEx();
		}
		say("seed=reval-offset asset_a_acct_rows=" + aaccts.size());
		commit();   // Doc_AssetReval reads a_asset_acct with a NULL trx (Doc_AssetReval.java:105)
		MDepreciationWorkfile wkA = MDepreciationWorkfile.get(ctx, assetA.getA_Asset_ID(), "A", trxName);
		MAssetReval rv = new MAssetReval(ctx, 0, trxName);
		rv.setA_Asset_ID(assetA.getA_Asset_ID());
		rv.setPostingType("A");
		rv.setDateDoc(firstExp);
		rv.setDateAcct(firstExp);
		rv.setA_Asset_Cost(wkA.getA_Asset_Cost());
		rv.setA_Accumulated_Depr(wkA.getA_Accumulated_Depr());
		rv.setA_Asset_Cost_Change(wkA.getA_Asset_Cost().add(new BigDecimal("1000.0")));
		rv.setA_Change_Acumulated_Depr(wkA.getA_Accumulated_Depr().add(new BigDecimal("100.0")));
		rv.saveEx();
		driveCO(rv, "A_Asset_Reval");

		// ── 4. Asset B + addition, then transfer (dated +1 day so the completeIt-created
		//       a_asset_acct time-slice row stays derivable from validfrom) ──
		MAsset assetB = newAsset(ctx, "b3_asset_b");
		MAssetAddition addB = newAddition(ctx, assetB, "6000.0");
		driveCO(addB, "A_Asset_Addition_B");
		// arm-Z witness: an EXPENSE-gated second addition — the REAL poster completes it and posts
		// ZERO fact rows (Doc_AssetAddition.java:67-72), the oracle side of the §B3-FALSIFIER gate-flip
		MAssetAddition addD = new MAssetAddition(ctx, 0, trxName);
		addD.setA_Asset_ID(assetB.getA_Asset_ID());
		addD.setDateDoc(new Timestamp(System.currentTimeMillis()));
		addD.setA_SourceType(MAssetAddition.A_SOURCETYPE_Manual);
		addD.setAssetAmtEntered(new BigDecimal("500.0"));
		addD.setAssetSourceAmt(addD.getAssetAmtEntered());
		addD.setA_CapvsExp(MAssetAddition.A_CAPVSEXP_Expense);
		addD.saveEx();
		driveCO(addD, "A_Asset_Addition_D_ExpGate");
		int addDFacts = DB.getSQLValueEx(trxName,
			"SELECT COUNT(*) FROM Fact_Acct WHERE AD_Table_ID=53137 AND Record_ID=?", addD.get_ID());
		say("expgate_addition=" + addD.get_ID() + " fact_rows=" + addDFacts + " (gate ⇒ 0 expected)");
		MAssetAcct acctB = MAssetAcct.forA_Asset_ID(ctx, 101, assetB.getA_Asset_ID(), "A",
			new Timestamp(System.currentTimeMillis()), trxName);
		int furnAsset = DB.getSQLValueEx(trxName,
			"SELECT A_Asset_Acct FROM A_Asset_Group_Acct WHERE A_Asset_Group_ID=? AND C_AcctSchema_ID=101", GROUP_FURNITURE);
		int furnAccum = DB.getSQLValueEx(trxName,
			"SELECT A_Accumdepreciation_Acct FROM A_Asset_Group_Acct WHERE A_Asset_Group_ID=? AND C_AcctSchema_ID=101", GROUP_FURNITURE);
		MAssetTransfer tr = new MAssetTransfer(ctx, 0, trxName);
		tr.setA_Asset_ID(assetB.getA_Asset_ID());
		tr.setPostingType("A");
		tr.setC_AcctSchema_ID(101);
		Timestamp plus1 = new Timestamp(System.currentTimeMillis() + 24L * 3600 * 1000);
		tr.setDateDoc(plus1);
		tr.setDateAcct(plus1);
		// old accounts: copied from B's own acct row (prepareIt verifies all five match)
		tr.setA_Asset_Acct(acctB.getA_Asset_Acct());
		tr.setA_Accumdepreciation_Acct(acctB.getA_Accumdepreciation_Acct());
		tr.setA_Depreciation_Acct(acctB.getA_Depreciation_Acct());
		tr.setA_Disposal_Revenue_Acct(acctB.getA_Disposal_Revenue_Acct());
		tr.setA_Disposal_Loss_Acct(acctB.getA_Disposal_Loss_Acct());
		// new: asset+accumdep move to the Furniture combos; the rest stay (at-least-one-change gate)
		tr.setA_Asset_New_Acct(furnAsset);
		tr.setA_Accumdepreciation_New_Acct(furnAccum);
		tr.setA_Depreciation_New_Acct(acctB.getA_Depreciation_Acct());
		tr.setA_Disposal_Revenue_New_Acct(acctB.getA_Disposal_Revenue_Acct());
		tr.setA_Disposal_Loss_New_Acct(acctB.getA_Disposal_Loss_Acct());
		// NOT NULL, no default, and unread by prepare/complete/createFacts: the asset's life span + balance flag
		tr.setA_Period_Start(1);
		tr.setA_Period_End(18);
		tr.setA_Transfer_Balance_IS(true);
		tr.saveEx();
		driveCO(tr, "A_Asset_Transfer");
		say("asset_b=" + assetB.getA_Asset_ID() + " transfer_old_asset_acct=" + acctB.getA_Asset_Acct()
			+ " new_asset_acct=" + furnAsset);

		// ── 5. Asset C + addition, then simple disposal ──
		MAsset assetC = newAsset(ctx, "b3_asset_c");
		MAssetAddition addC = newAddition(ctx, assetC, "9000.0");
		driveCO(addC, "A_Asset_Addition_C");
		MAssetDisposed dp = new MAssetDisposed(ctx, 0, trxName);
		dp.setA_Asset_ID(assetC.getA_Asset_ID());
		dp.setPostingType("A");
		dp.setDateDoc(new Timestamp(System.currentTimeMillis()));
		dp.setDateAcct(dp.getDateDoc());
		dp.setA_Disposed_Method(MAssetDisposed.A_DISPOSED_METHOD_Simple);
		dp.saveEx();
		driveCO(dp, "A_Asset_Disposed");
		say("asset_c=" + assetC.getA_Asset_ID() + " disposal=" + dp.get_ID());

		// ── 6. project issue (project 101, category N → PJ_WIP; Mulch from HQ locator) ──
		MProject prj = new MProject(ctx, PROJECT_LANDSCAPE, trxName);
		MProjectIssue pi = new MProjectIssue(prj);
		pi.setMandatory(LOCATOR_HQ, PRODUCT_MULCH, BigDecimal.ONE);
		pi.saveEx();
		driveCO(pi, "C_ProjectIssue");
		say("projectissue=" + pi.get_ID() + " project=" + PROJECT_LANDSCAPE + " product=" + PRODUCT_MULCH);

		// ── fact tallies from inside the trx (the external capture re-reads them post-commit) ──
		for (Object[] t : new Object[][] {
				{"A_Asset_Addition", 53137}, {"A_Depreciation_Entry", 53121}, {"A_Asset_Reval", 53275},
				{"A_Asset_Transfer", 53128}, {"A_Asset_Disposed", 53127}, {"C_ProjectIssue", 623}}) {
			int n = DB.getSQLValueEx(trxName,
				"SELECT COUNT(*) FROM Fact_Acct WHERE AD_Table_ID=?", (Integer) t[1]);
			say("fact_count table=" + t[0] + " ad_table_id=" + t[1] + " rows=" + n);
		}

		// COMMIT into the scratch clone — the external psql capture needs the rows visible
		commit();
		say("committed=Y db=" + dbName);
	}
}
