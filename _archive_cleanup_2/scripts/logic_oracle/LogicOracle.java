/*
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 *
 * LogicOracle.java — ORACLE side of W-LOGIC-HARDEN (ERP_EXECUTION_ROADMAP.md §PHASE B B-1,
 * HARDEN_MATRIX.md §H-3 parked ad_evaluator row).
 *
 * This is NOT a re-implementation: it drives the REAL compiled iDempiere evaluator classes from
 * ~/idempiere-dev-setup/idempiere/org.adempiere.base/target/classes —
 *   SimpleBooleanLexer / SimpleBooleanParser (ANTLR-generated from antlr/SimpleBoolean.g4) +
 *   EvaluationVisitor + ThrowingErrorListener (org.idempiere.expression.logic).
 * The body below replicates LogicEvaluator.evaluateLogic (LogicEvaluator.java:68-86) verbatim MINUS the
 * CLogger static (CLogger drags the whole OSGi/report stack — NoClassDefFoundError outside the server).
 * Parse error -> "E:parse" (LogicEvaluator returns false there; we surface it so a grammar gap is a NAMED
 * finding, never silently false on both sides).
 *
 * Evaluatee = the captured record context (GridField.get_ValueAsString -> Env.getContext returns "" when
 * unset; empty *_ID -> "0" is applied INSIDE EvaluationVisitor.visitContextVariables:260-271, not here).
 *
 * I/O (transport-safe, no JSON dep): each stdin line
 *   id \t base64(expr) \t base64(ctxBlob)      ctxBlob = key \x1F value pairs joined by \x1E
 * -> stdout line:  id \t T|F|E:<msg>
 */
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.compiere.util.Evaluatee;
import org.idempiere.expression.logic.EvaluationVisitor;
import org.idempiere.expression.logic.SimpleBooleanLexer;
import org.idempiere.expression.logic.SimpleBooleanParser;
import org.idempiere.expression.logic.ThrowingErrorListener;

public class LogicOracle {

    static String dec(String b64) {
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    /** LogicEvaluator.evaluateLogic:68-86 minus CLogger (same parser, same visitor, same fallbacks). */
    static String evaluateLogic(Evaluatee source, String logic) {
        SimpleBooleanLexer lexer = new SimpleBooleanLexer(CharStreams.fromString(logic));
        SimpleBooleanParser parser = new SimpleBooleanParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(ThrowingErrorListener.INSTANCE);
        try {
            Object result = new EvaluationVisitor(source).visit(parser.parse());
            if (result instanceof Boolean)
                return ((Boolean) result) ? "T" : "F";
            return "F"; // LogicEvaluator: non-Boolean result -> log severe + return false
        } catch (ParseCancellationException e) {
            return "E:parse " + e.getMessage();
        } catch (Exception e) {
            return "E:" + e.getClass().getSimpleName() + " " + e.getMessage();
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\t");
            String id = parts[0];
            String expr = dec(parts[1]);
            final Map<String, String> ctx = new HashMap<>();
            if (parts.length > 2 && !parts[2].isEmpty()) {
                for (String pair : dec(parts[2]).split("\u001E")) {
                    int p = pair.indexOf('\u001F');
                    if (p >= 0) ctx.put(pair.substring(0, p), pair.substring(p + 1));
                }
            }
            Evaluatee evaluatee = new Evaluatee() {
                @Override
                public String get_ValueAsString(String variableName) {
                    String v = ctx.get(variableName);
                    return v == null ? "" : v;   // Env.getContext: unset -> ""
                }
            };
            out.append(id).append('\t').append(evaluateLogic(evaluatee, expr)).append('\n');
        }
        System.out.print(out);
    }
}
