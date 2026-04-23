package com.bim.compiler.drawing2d.pipeline;

import com.bim.compiler.drawing2d.dao.ScopeDao;
import com.bim.compiler.drawing2d.model.DrawingScope;
import com.bim.compiler.drawing2d.model.PageEntry;
import com.bim.compiler.drawing2d.model.PageManifest;

/**
 * Stage 1: Detect building scope and generate page manifest.
 *
 * Queries the building DB for habitable storeys, MEP disciplines,
 * and mesh availability. Generates the page manifest that drives
 * all subsequent stages.
 *
 * Replaces the hand-crafted {PREFIX}_2D.json files.
 *
 * Log contract:
 *   §SCOPE storey=... floor_z=... code=...   (per storey)
 *   §SCOPE mep_class=... → ...               (per MEP class found)
 *   §SCOPE storeys=N disciplines={...}        (summary)
 *   §SCOPE manifest: N pages [...]            (page list)
 */
public class ScopeStage implements DrawingStage {

    @Override
    public String name() { return "SCOPE"; }

    @Override
    public void execute(DrawingContext ctx) throws Exception {
        ScopeDao dao = new ScopeDao(ctx.dbPath());

        // Detect scope — DAO logs each discovery
        DrawingScope scope = dao.detect(msg -> ctx.log(name(), msg));
        ctx.setScope(scope);

        // Generate manifest from scope
        PageManifest manifest = PageManifest.generate(scope);
        ctx.setManifest(manifest);

        // Log manifest summary
        StringBuilder sb = new StringBuilder();
        sb.append("manifest: ").append(manifest.size()).append(" pages [");
        for (int i = 0; i < manifest.pages().size(); i++) {
            if (i > 0) sb.append(", ");
            PageEntry pe = manifest.pages().get(i);
            sb.append(pe.fileKey());
        }
        sb.append("]");
        ctx.log(name(), sb.toString());
    }
}
