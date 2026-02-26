# 1-Day IT Literacy Workshop for Petrol Station Operators
# Understanding Your Numbers: SENTINEL, SETEL, E-Invoice, and Why Data Accuracy Matters

**Prepared for:** KOBB (Koperasi Pengusaha Stesen Minyak Bumiputera) Members
**Facilitated by:** Entruss Ventures Sdn. Bhd. (1186369-A)
**Workshop Lead:** Redhuan D. Oon — Subject Matter Expert, FOSS ERP & AI
**Patron:** Prof. Dr. Hj. Wan Mohtar Wan Yusoff — Chairman, KOBB; EVSB Expert
**Classification:** HRDC-Claimable | 1-Day Programme (09:00–17:00)
**Date:** February 2026 (Draft)

---

## Why This Workshop Exists

Since the rollout of Malaysia's targeted fuel subsidy programme — diesel (June 2024) and RON95 BUDI95 (September 2025) — petrol station operators have been caught between **multiple systems that do not talk to each other the way operators expect**.

The result: **daily mismatches in sales figures, subsidy claims, and e-invoice records** that operators cannot explain, cannot reconcile, and cannot resolve without understanding what is actually happening inside these systems.

This workshop **does not teach IT**. It teaches **principles** — in plain language — so that operators can:
1. Understand *why* their numbers don't match
2. Know *what to demand* from vendors and system providers
3. Protect themselves from financial losses caused by data errors they did not create

---

## The Problem in Plain Language

### What Operators Experience Daily

```
Morning:  You open the station. Pumps are running. SENTINEL is recording sales.
          SETEL is processing digital payments. E-invoice is generating tax documents.

Evening:  You close the station. You look at THREE different numbers:
          - Pump meter totals (physical)
          - SENTINEL system totals (subsidy tracking)
          - SETEL transaction totals (digital payment)

          They don't match.

Month-end: LHDN wants e-invoices. Credit notes. Debit notes.
           The numbers STILL don't match.
           You don't know whose number is right.
           You don't know whose number is wrong.
           You don't even know who to call.
```

### What We Have Diagnosed

Through direct engagement with KOBB station operators and meetings with SETEL, Entruss Ventures has identified **three root causes** — none of which are the operator's fault:

| Root Cause | What Happens | Impact |
|-----------|-------------|--------|
| **1. Live System Access Conflict** | SENTINEL and SETEL access the same transaction data but at **different moments in time**. They are two separate systems reading a live database that is constantly changing. They will never see the same snapshot. | Daily totals diverge. Operators cannot reconcile. |
| **2. Vendor Update Practice** | The system vendors update records, prices, and configurations **without coordinating a consistent cutoff point**. Data changes mid-day, mid-transaction, mid-batch. | Yesterday's report doesn't match today's report for the same day. Numbers shift retroactively. |
| **3. Incomplete UUID Disclosure** | Every transaction in SETEL has a **UUID (Unique Universal Identifier)** — a long code that uniquely identifies that specific transaction forever. But operators are **not given the full UUID** in their records. They get truncated or partial references. | Operators cannot trace specific disputed transactions. They cannot prove or disprove mismatches at the individual transaction level. |

---

## Part 1: The Five Principles Every Operator Must Know

### Principle 1: Source of Truth

> **"If two systems disagree, one of them is wrong. You need to decide IN ADVANCE which one is the master."**

In every business — from a nasi lemak stall to Petronas headquarters — there must be **one agreed source of truth** for each type of data.

| Data Type | Who Should Be Source of Truth? | Current Reality |
|-----------|-------------------------------|-----------------|
| Litres dispensed | Physical pump meter | Pump meter is correct (calibrated by KPDN) |
| Subsidy-eligible transactions | SENTINEL | SENTINEL records at time of transaction |
| Digital payment amount | SETEL | SETEL records at time of payment |
| Tax document (e-invoice) | LHDN MyInvois | Generated after the fact — from which source? |

**The problem:** There is **no agreed single source of truth** that SENTINEL, SETEL, and the e-invoice system all read from at the same moment. Each system is its own "truth" — and they disagree.

**What operators should demand:** A clear written answer from Petronas/vendors: **"Which system is the master record for daily sales reconciliation?"**

### Principle 2: GIGO — Garbage In, Garbage Out

> **"A computer does not know if the data is wrong. It will process garbage perfectly and give you a perfect-looking garbage report."**

The vendors' systems are not broken. They process data exactly as programmed. But if the **input data is inconsistent** — because of timing differences, retroactive updates, or configuration changes — the output will be consistently wrong.

**Examples operators will recognise:**

| Scenario | What Went In (Garbage) | What Came Out (Garbage) |
|----------|----------------------|------------------------|
| Price updated at 11:47am, but pump recorded sale at 11:46am | One system uses old price, another uses new price | RM difference on one transaction — multiplied by hundreds of transactions per day |
| SETEL batch closes at 11:59pm, SENTINEL closes at 12:00am | Transactions near midnight appear in different days | Daily totals don't match. Neither system is "wrong" — they just cut at different moments. |
| Credit note issued for subsidy adjustment, but dated 3 days later | E-invoice shows original amount on Day 1, credit note on Day 4 | Monthly reconciliation is chaos — operator's books show a discrepancy for 3 days |

**What operators should demand:** All systems must use the **same cutoff time** (e.g., 00:00:00 midnight) and the **same price table version** when processing the same transactions.

### Principle 3: EODA Backup — Your Snapshot Source of Truth

> **"EODA = End Of Day Archive. Take a backup snapshot every night. That frozen snapshot is YOUR truth."**

This is the most important practical takeaway from this workshop.

**What is EODA?**

```
Every day at closing time (e.g., 11:55 PM):

1. EXPORT your pump meter readings (physical count)
2. EXPORT your SENTINEL daily summary (if available)
3. EXPORT your SETEL daily transaction list
4. SAVE all three into one folder: EODA_2026-02-18/

That folder is now your FROZEN SNAPSHOT for that date.
No one can change it. No vendor update can alter it.
It is YOUR record.
```

**Why this matters:**

When SETEL or SENTINEL's live system is updated retroactively (price corrections, late credit notes, subsidy adjustments), the **live reports will change**. But your EODA snapshot will not. You can now compare:
- What the system said on the night of February 18
- What the system says today about February 18

**If they differ, you have proof that the vendor changed the data after the fact.** Without EODA, you have no proof. You are at the mercy of whatever the live system shows today.

**Recommendation for SETEL/SENTINEL vendors:**

> The subsidy reconciliation system should read from an **EODA snapshot** (frozen daily archive) rather than querying the live transactional database. Two separate systems accessing a live database at different times will NEVER produce identical numbers. An EODA snapshot taken at a fixed time becomes the single source of truth both systems can agree on.

### Principle 4: UUID — Your Transaction Fingerprint

> **"Every digital transaction has a UUID — a unique code that identifies it forever. If you don't have it, you cannot trace it. If you cannot trace it, you cannot dispute it."**

**What is a UUID?**

```
Example: 550e8400-e29b-41d4-a716-446655440000

This is a 36-character code that uniquely identifies ONE transaction.
No two transactions in the world will ever have the same UUID.
It is the transaction's "IC number".
```

**The problem we uncovered:**

In our meeting with SETEL, we discovered that **station operators are not given the full UUID** for their transactions. They receive truncated references, partial codes, or internal batch numbers that cannot be traced back to the specific transaction in SETEL's system.

**Why this is unacceptable:**

| Scenario | With Full UUID | Without Full UUID |
|----------|---------------|-------------------|
| Customer disputes a charge | Operator shows UUID → SETEL confirms exact transaction | Operator has nothing to show. SETEL says "which transaction?" |
| Subsidy mismatch on one sale | Operator traces UUID in SENTINEL and SETEL → finds which system recorded it differently | Operator sees a discrepancy but cannot identify which transaction caused it |
| LHDN audit on e-invoice | Operator maps UUID to e-invoice line item → complete trail | Operator cannot prove which sale corresponds to which invoice |
| Credit note issued late | UUID links original transaction → credit note → revised amount | No linkage. Operator's books have unexplained gaps. |

**What operators should demand:** Full UUID for every transaction, printed on receipts AND available in daily export files. This is not extra work for SETEL — the UUID already exists. It just needs to be **disclosed**.

### Principle 5: Reconciliation Must Be Tri-Party

> **"You cannot reconcile two systems by looking at only one of them."**

Current practice expects operators to reconcile SENTINEL and SETEL separately. But subsidy transactions involve **three parties**:

```
          PUMP (Physical)
              |
    +---------+---------+
    |                   |
SENTINEL            SETEL
(Subsidy tracking)  (Payment processing)
    |                   |
    +---------+---------+
              |
         E-INVOICE
         (LHDN tax document)
```

**All four numbers must agree for a given day:**
1. Pump meters: X litres dispensed
2. SENTINEL: X litres recorded as subsidised / non-subsidised
3. SETEL: RM Y collected via digital payment
4. E-Invoice: RM Y invoiced to LHDN

**If ANY of these numbers disagree, there is a data integrity problem — and the operator should NOT sign off until it is resolved.**

---

## Part 2: The E-Invoice Nightmare

### What Operators Face

Since August 2024 (Phase 1) and continuing into 2025-2026, petrol station operators must comply with LHDN's mandatory e-invoicing requirements. The petroleum sector is explicitly covered under LHDN's industry-specific guidelines.

**The specific pain points for station operators:**

| Issue | What Happens | Impact |
|-------|-------------|--------|
| **Late credit notes** | Subsidy adjustments generate credit notes days after the original sale | Operator's daily e-invoice total doesn't match daily cash/payment total. Gap persists for days until credit note arrives. |
| **Debit notes for underclaims** | When the subsidy calculation is corrected upward, a debit note is issued | Operator's monthly reconciliation shows unexplained increases. LHDN sees the debit note but operator may not have it in their system. |
| **Timing mismatch** | E-invoice is generated at a different time than the SENTINEL/SETEL transaction record | The same sale appears on different dates in different systems. Operator cannot explain the discrepancy to auditors. |
| **Batch vs real-time** | E-invoices are generated in batches (e.g., end of day), but transactions are real-time | Individual transaction amounts may be aggregated differently depending on when the batch runs. |

### The Root Cause

The e-invoice system was designed for **simple buy-sell transactions**: one seller, one buyer, one invoice, one payment. The petrol subsidy system introduces a **third party** (the government subsidy), which means:

- The **pump price** (what the customer sees): RM2.60/litre (RON95)
- The **subsidised price** (what the customer pays): RM1.99/litre
- The **subsidy amount** (what the government pays): RM0.61/litre

**Three amounts for one transaction. One e-invoice. How do you reconcile?**

This is not an operator problem. This is a **system design problem** that LHDN, KPDN, and the fuel companies need to resolve. But operators bear the consequences when the numbers don't add up during audits.

### What Operators Should Do Now

1. **Keep EODA snapshots** — your daily frozen record (Principle 3)
2. **Demand full UUIDs** — so you can trace any disputed transaction (Principle 4)
3. **Log every credit/debit note** with the date it was issued AND the date of the original transaction it refers to
4. **Do not sign off on monthly reconciliations** where the gap between your EODA snapshot and the vendor's live report exceeds a threshold you are comfortable with
5. **Report discrepancies formally** to KOBB — aggregate data from multiple stations strengthens the cooperative's negotiating position with vendors

---

## Part 3: What KOBB Can Do as a Cooperative

### Individual Operators Are Weak. The Cooperative Is Strong.

A single petrol station operator asking SETEL for full UUIDs will be ignored. **KOBB representing all its member stations** asking for full UUIDs is a formal demand from a registered cooperative with institutional backing.

### KOBB's Collective Leverage

| Action | Individual Operator | KOBB Cooperative |
|--------|-------------------|-----------------|
| Demand full UUID disclosure | Ignored | Formal request backed by SKM/ANGKASA |
| Demand EODA-based reconciliation | "That's not how the system works" | Negotiate as standard practice for all member stations |
| Report persistent mismatches | One complaint among thousands | Aggregated data showing systemic pattern |
| Engage LHDN on e-invoice timing issues | Complex and intimidating | KOBB submits formal industry feedback |
| Demand vendor SLA for credit/debit note timing | No leverage | Contractual requirement for all member stations |
| Training and capacity building | Self-funded, ad hoc | HRDC-claimable, structured, ongoing |

### Prof. Wan Mohtar's Role

As Chairman of KOBB and board member of KOPSYA with strong ANGKASA connections, Prof. Dr. Hj. Wan Mohtar Wan Yusoff provides the institutional bridge between:
- **Operators on the ground** (who experience the problems daily)
- **ANGKASA** (which represents the cooperative movement nationally)
- **Regulators** (KPDN, LHDN, MOF) who set the rules
- **Vendors** (Petronas, SETEL) who build and operate the systems

This workshop is the first step in **converting individual frustration into collective action**.

---

## Part 4: Workshop Agenda

### Morning — Understanding Your Systems

| Time | Module | Content | Method |
|------|--------|---------|--------|
| 09:00 | **Opening** | Welcome by Prof. Wan Mohtar. Workshop objectives. Why this is a cooperative matter, not just a technology matter. | Address |
| 09:15 | **Module 1: Source of Truth** | What it means. Why your three systems disagree. Which system should be the master? Interactive poll: "Have you had a mismatch this month?" | Lecture + poll |
| 09:45 | **Module 2: GIGO in Action** | Live walkthrough of how a single price update at the wrong moment creates a cascade of mismatched numbers. Using real (anonymised) KOBB station data. | Live demo |
| 10:15 | **Break** | |
| 10:30 | **Module 3: SENTINEL vs SETEL** | What each system does. When each system reads data. Why they see different numbers for the same day. The live-system access problem explained with a clock diagram. | Diagram + Q&A |
| 11:15 | **Module 4: The UUID Problem** | What a UUID is (your transaction's IC number). Why you need the full one. What we found in our SETEL meeting. How to demand it. | Lecture + examples |
| 12:00 | **Module 5: EODA Backup** | Hands-on: how to take an EODA snapshot. What to save. Where to save it. How to compare it against vendor reports. Participants practice on their own data. | Hands-on exercise |
| 12:45 | **Lunch** | |

### Afternoon — Protecting Yourself

| Time | Module | Content | Method |
|------|--------|---------|--------|
| 13:45 | **Module 6: E-Invoice for Operators** | What LHDN requires. What a credit note is. What a debit note is. Why they arrive late. How the subsidy creates a three-party transaction that e-invoice wasn't designed for. | Lecture + real examples |
| 14:30 | **Module 7: Reconciliation Workshop** | Hands-on: reconcile one week of data using pump readings + SENTINEL + SETEL + e-invoices. Identify the gaps. Classify each gap by root cause (timing, price update, missing UUID, late credit note). | Group exercise (tables of 4-5) |
| 15:30 | **Break** | |
| 15:45 | **Module 8: Your Rights as a Cooperative Member** | What KOBB can demand collectively. How to report discrepancies through KOBB channels. The path from KOBB → ANGKASA → regulators. Template: formal discrepancy report form. | Discussion + template |
| 16:30 | **Module 9: Action Plan** | Each participant drafts a personal action plan: (1) Start EODA tonight, (2) Demand full UUID in writing, (3) Log all credit/debit notes, (4) Report first month's findings to KOBB. | Individual exercise |
| 17:00 | **Closing** | Summary of five principles. Distribution of reference card (laminated, for the cash register). Feedback forms. Certificate of attendance. | Address |

---

## Part 5: Deliverables to Participants

Each participant receives:

| Item | Description |
|------|------------|
| **Laminated Reference Card** | The 5 Principles on a single card — kept at the cash register for daily reference |
| **EODA Template** | A simple folder structure and checklist for nightly backup snapshots |
| **Discrepancy Report Template** | Standard form for reporting mismatches to KOBB — includes fields for date, pump reading, SENTINEL total, SETEL total, e-invoice total, gap amount, suspected root cause |
| **UUID Demand Letter Template** | Draft letter from KOBB to SETEL requesting full UUID disclosure for all station transactions |
| **Certificate of Attendance** | HRDC-claimable, issued by Entruss Ventures Sdn. Bhd. |

---

## Part 6: Outcome and Next Steps

### For KOBB

| Action | Timeline | Lead |
|--------|----------|------|
| Circulate UUID demand letter to SETEL (via KOBB board) | Within 2 weeks of workshop | Prof. Wan Mohtar |
| Collect first month of EODA snapshots from 10 pilot stations | 30 days | Workshop participants |
| Aggregate discrepancy data and present to ANGKASA | 60 days | KOBB IT committee |
| Submit formal industry feedback to LHDN on e-invoice timing for subsidised fuel | 90 days | KOBB + ANGKASA |
| Plan follow-up workshop: "Advanced Reconciliation and Audit Preparation" | Q3 2026 | Entruss Ventures |

### For Entruss Ventures

This workshop establishes Entruss Ventures as a **credible HRDC-certified training provider** in the cooperative sector, specifically at the intersection of:
- **IT literacy for non-technical operators** — practical, not academic
- **Data governance and reconciliation** — principles that apply to any industry
- **Cooperative collective action** — translating individual problems into systemic solutions

The KOBB workshop is a replicable model that can be adapted for:
- Other petrol station cooperatives (non-Petronas brands)
- Agricultural cooperatives (similar subsidy tracking issues)
- Any cooperative sector facing multi-vendor system reconciliation

---

## Appendix A: Glossary for Operators

| Term | Malay | Plain English |
|------|-------|--------------|
| **Source of Truth** | Sumber Kebenaran | The ONE system everyone agrees is the master record |
| **GIGO** | Sampah Masuk, Sampah Keluar | Bad input data = bad output reports, no matter how good the computer is |
| **EODA** | Arkib Akhir Hari | A frozen copy of your daily data, saved every night before anyone can change it |
| **UUID** | Nombor Pengenalan Transaksi | A unique code for every transaction — like an IC number for a sale |
| **Reconciliation** | Penyesuaian | Comparing two or more sets of numbers to make sure they match |
| **Credit Note** | Nota Kredit | A document that REDUCES the amount you owe (e.g., subsidy adjustment in your favour) |
| **Debit Note** | Nota Debit | A document that INCREASES the amount you owe (e.g., underclaim correction) |
| **E-Invoice** | E-Invois | Electronic invoice submitted to LHDN — mandatory for all businesses |
| **Snapshot** | Tangkap Layar Data | A frozen picture of data at one specific moment in time |
| **Cutoff Time** | Masa Potong | The exact time when one day's data ends and the next day's begins |

## Appendix B: The Clock Diagram — Why Systems Disagree

```
         23:55         00:00          00:05
           |             |              |
    PUMP:  |===SALE===|  |              |     ← Pump records sale at 23:57
           |             |              |
SENTINEL:  |    ......|BATCH CLOSE|    |     ← SENTINEL closes batch at 00:00
           |             |              |
   SETEL:  |         |BATCH CLOSE|..   |     ← SETEL closes batch at 00:02
           |             |              |
           |             |              |
   RESULT: Pump says sale is on Feb 18
           SENTINEL says sale is on Feb 18 (closed before midnight)
           SETEL says sale is on Feb 19 (closed after midnight)

           THREE systems. THREE dates. ONE sale.
           NOBODY is wrong. The DESIGN is wrong.

   FIX:    All systems must use the SAME cutoff time.
           Or: all systems must read from the SAME EODA snapshot.
```

---

**Entruss Ventures Sdn. Bhd.** (1186369-A)
No. 15, Jalan 1/3c, Seksyen 1, 43650 Bandar Baru Bangi, Selangor D.E.
Tel: 03-8925 8301 | Email: info@entruss.net

**Workshop Enquiries:** red1org@gmail.com
**KOBB Cooperative:** Prof. Dr. Hj. Wan Mohtar Wan Yusoff, Chairman
