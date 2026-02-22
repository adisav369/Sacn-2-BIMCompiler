package com.bim.compiler.topologymaker;

/** Document lifecycle status — mirrors C_Order/AD_Process pattern in iDempiere. */
public enum DocStatus {
    /** Draft — order created, not yet processed. */
    DR,
    /** In Progress — processing underway, may have validation warnings. */
    IP,
    /** Completed — room boundaries and BOMs written, ready for compiler. */
    CO,
    /** Voided — cancelled, rows written during IP/CO are no longer active. */
    VO
}
