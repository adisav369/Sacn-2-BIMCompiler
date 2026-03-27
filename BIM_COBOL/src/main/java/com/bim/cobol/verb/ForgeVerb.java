package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.cobol.forge.*;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FORGE &lt;piece_type&gt; [key:value ...]
 *
 * Dispatches to registered ForgeEngine implementations.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §5, §10
 */
public class ForgeVerb implements Verb<ForgeResult> {

    private final Map<String, ForgeEngine> engines = new LinkedHashMap<>();

    public ForgeVerb() {
        register(new SlopeCutForge());
        register(new StairFlightForge());
        register(new PipeBendForge());
        register(new DomeSectionForge());
        register(new BarrelVaultForge());
    }

    private void register(ForgeEngine engine) {
        engines.put(engine.pieceType(), engine);
    }

    @Override
    public String keyword() { return "FORGE"; }

    @Override
    public VerbResult<ForgeResult> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(),
                    "usage: FORGE <piece_type> [key:value ...] — types: "
                    + engines.keySet(), null);

        String pieceType = args[0].toUpperCase();
        ForgeEngine engine = engines.get(pieceType);
        if (engine == null)
            return VerbResult.fail(keyword(),
                    "unknown piece type: " + pieceType
                    + " (registered: " + engines.keySet() + ")", null);

        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String[] kv = args[i].split(":", 2);
            if (kv.length == 2) params.put(kv[0].toLowerCase(), kv[1]);
        }

        ForgeResult result = engine.compute(ctx, params);

        if (result.pass())
            return VerbResult.ok(keyword(), result.summary(), result);
        else
            return VerbResult.fail(keyword(), result.error(), result);
    }
}
