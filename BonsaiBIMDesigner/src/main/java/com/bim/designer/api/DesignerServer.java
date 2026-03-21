package com.bim.designer.api;

import com.bim.backoffice.model.DesignBBox;
import com.bim.designer.compile.ChangeSet;
import com.bim.designer.protocol.JsonProtocol;
import com.bim.designer.protocol.StatusMessage;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TCP socket server exposing {@link DesignerAPI} over newline-delimited JSON.
 *
 * <p>Protocol: one JSON object per line (ndjson). No HTTP framework dependency.
 * Default port: 9876.
 *
 * <p>Request format:
 * <pre>
 * {"action":"compile","buildingId":"Ifc4_SampleHouse","bomDbPath":"library/_SH_compile.db"}
 * {"action":"verb","buildingId":"...","verbLine":"CHECK BOM BUILDING_SH"}
 * {"action":"listBuildings"}
 * {"action":"listCategories","docSubType":"SH"}
 * </pre>
 *
 * <p>Async push (after ArtifactWatcher triggers auto-recompile):
 * <pre>
 * {"type":"COMPILE_COMPLETE","buildingId":"...","outputDbPath":"...","elementCount":55}
 * </pre>
 *
 * <p>Python addon receives via {@code threading.Thread}, dispatches to
 * Blender via {@code bpy.app.timers}.
 */
public class DesignerServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(DesignerServer.class.getName());
    private static final int DEFAULT_PORT = 9876;

    private final DesignerAPI api;
    private final int port;
    private final ExecutorService pool;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public DesignerServer(DesignerAPI api) {
        this(api, DEFAULT_PORT);
    }

    public DesignerServer(DesignerAPI api, int port) {
        this.api = api;
        this.port = port;
        this.pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "designer-client");
            t.setDaemon(true);
            return t;
        });
    }

    /** Starts the server. Blocks until {@link #stop()} is called. */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        LOG.info("DesignerServer listening on port " + port);

        while (running) {
            try {
                Socket client = serverSocket.accept();
                pool.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (running) LOG.log(Level.WARNING, "Accept failed", e);
            }
        }
    }

    /** Starts the server on a background thread. Returns immediately. */
    public void startAsync() {
        Thread t = new Thread(() -> {
            try {
                start();
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Server start failed", e);
            }
        }, "designer-server");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error closing server socket", e);
        }
        pool.shutdownNow();
    }

    @Override
    public void close() {
        stop();
    }

    public int getPort() {
        return port;
    }

    private void handleClient(Socket client) {
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(new BufferedOutputStream(client.getOutputStream()), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                String response = dispatch(line);
                out.println(response);
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Client disconnected", e);
        }
    }

    private String dispatch(String jsonLine) {
        try {
            var request = JsonProtocol.parseRequest(jsonLine);
            String action = request.action();

            return switch (action) {
                case "compile" -> {
                    CompileRequest req = new CompileRequest(
                            request.stringField("buildingId"),
                            request.stringField("bomDbPath"),
                            request.stringField("libraryPath"),
                            request.stringField("outputDir")
                    );
                    CompileResponse resp = api.compile(req);
                    yield JsonProtocol.toJson(resp);
                }
                case "compileIncremental" -> {
                    CompileRequest req = new CompileRequest(
                            request.stringField("buildingId"),
                            request.stringField("bomDbPath"),
                            request.stringField("libraryPath"),
                            request.stringField("outputDir")
                    );
                    ChangeSet changes = JsonProtocol.parseChangeSet(request.objectField("changes"));
                    CompileResponse resp = api.compileIncremental(req, changes);
                    yield JsonProtocol.toJson(resp);
                }
                case "verb" -> {
                    var resp = api.executeVerb(
                            request.stringField("buildingId"),
                            request.stringField("verbLine")
                    );
                    yield JsonProtocol.toJson(resp);
                }
                case "createNew" -> {
                    CreateNewRequest cnReq = new CreateNewRequest(
                            request.stringField("buildingName"),
                            request.stringField("buildingType"),
                            request.stringField("jurisdiction"),
                            request.doubleField("siteWidthMm", 0),
                            request.doubleField("siteDepthMm", 0),
                            request.intField("numBedrooms", 0),
                            request.intField("numBathrooms", 0),
                            request.intField("storeys", 1)
                    );
                    CreateNewResponse cnResp = api.createNew(cnReq);
                    yield JsonProtocol.toJson(cnResp);
                }
                case "listBuildings" -> {
                    List<DesignerAPI.BuildingTypeInfo> types = api.listBuildingTypes();
                    yield JsonProtocol.toJson(types);
                }
                case "listCategories" -> {
                    List<DesignerAPI.CategoryInfo> cats = api.listCategories(
                            request.stringField("docSubType"));
                    yield JsonProtocol.toJson(cats);
                }
                case "snap" -> {
                    var bboxes = JsonProtocol.parseDesignBBoxes(request.field("bboxes"));
                    var opts = new DesignerAPI.SnapOptions(
                            request.stringField("jurisdiction"),
                            request.intField("gridMm", 250),
                            request.stringField("fixRule"),
                            request.stringField("fixBomId"),
                            request.stringField("facilityType"));
                    var resp = api.snap(bboxes, opts);
                    yield JsonProtocol.toJson(resp);
                }
                case "setJurisdiction" -> {
                    var bboxes = JsonProtocol.parseDesignBBoxes(request.field("bboxes"));
                    var resp = api.setJurisdiction(
                            request.stringField("jurisdiction"), bboxes,
                            request.stringField("facilityType"));
                    yield JsonProtocol.toJson(resp);
                }
                case "save" -> {
                    var bboxes = JsonProtocol.parseDesignBBoxes(request.field("bboxes"));
                    var resp = api.save(
                            request.stringField("buildingId"),
                            bboxes,
                            request.stringField("variantLabel"));
                    yield JsonProtocol.toJson(resp);
                }
                case "recall" -> {
                    var resp = api.recall(
                            request.stringField("buildingId"),
                            request.stringField("variantId"));
                    yield JsonProtocol.toJson(resp);
                }
                case "listVariants" -> {
                    var variants = api.listVariants(
                            request.stringField("buildingId"));
                    yield JsonProtocol.toJson(variants);
                }
                case "browseItems" -> {
                    var resp = api.browseItems(new DesignerAPI.BrowseItemsRequest(
                            request.stringField("search"),
                            request.stringField("category"),
                            request.stringField("buildingType"),
                            request.doubleField("containerWidthMm", 0),
                            request.doubleField("containerDepthMm", 0),
                            request.doubleField("containerHeightMm", 0),
                            request.intField("offset", 0),
                            request.intField("limit", 20)));
                    yield JsonProtocol.toJson(resp);
                }
                case "findSimilar" -> {
                    var resp = api.findSimilar(new DesignerAPI.FindSimilarRequest(
                            request.stringField("productId"),
                            request.doubleField("containerWidthMm", 0),
                            request.doubleField("containerDepthMm", 0),
                            request.doubleField("containerHeightMm", 0),
                            request.intField("limit", 10)));
                    yield JsonProtocol.toJson(resp);
                }
                case "promote" -> {
                    var bboxes = JsonProtocol.parseDesignBBoxes(request.field("bboxes"));
                    var resp = api.promote(new DesignerAPI.PromoteRequest(
                            request.stringField("buildingId"),
                            request.stringField("owner"),
                            request.stringField("complianceRef"),
                            request.stringField("provenance"),
                            bboxes));
                    yield JsonProtocol.toJson(resp);
                }
                case "placeItem" -> {
                    var bboxes = JsonProtocol.parseDesignBBoxes(request.field("currentBboxes"));
                    var resp = api.placeItem(new DesignerAPI.PlaceItemRequest(
                            request.stringField("buildingId"),
                            request.stringField("roomBomId"),
                            request.stringField("productId"),
                            request.doubleField("offsetXMm", 0),
                            request.doubleField("offsetYMm", 0),
                            request.doubleField("offsetZMm", 0),
                            bboxes));
                    yield JsonProtocol.toJson(resp);
                }
                case "clickToPlace" -> {
                    var bboxes = JsonProtocol.parseDesignBBoxes(request.field("currentBboxes"));
                    var resp = api.clickToPlace(new DesignerAPI.ClickToPlaceRequest(
                            request.stringField("buildingId"),
                            request.doubleField("clickXMm", 0),
                            request.doubleField("clickYMm", 0),
                            request.doubleField("clickZMm", 0),
                            request.stringField("discipline"),
                            bboxes));
                    yield JsonProtocol.toJson(resp);
                }
                // ── G-9 ORDER View + BOM Outliner (§17.11, §17.19) ──
                case "listOrderLines" -> {
                    var resp = api.listOrderLines(
                            request.stringField("buildingId"));
                    yield JsonProtocol.toJson(resp);
                }
                case "updateOrderLine" -> {
                    var resp = api.updateOrderLine(
                            request.intField("orderLineId", 0),
                            request.stringField("buildingId"),
                            request.stringField("field"),
                            request.stringField("value"));
                    yield JsonProtocol.toJson(resp);
                }
                case "getBomTree" -> {
                    var resp = api.getBomTree(
                            request.stringField("buildingId"));
                    yield JsonProtocol.toJson(resp);
                }
                case "addRoom" -> {
                    var resp = api.addRoom(
                            request.stringField("buildingId"),
                            request.stringField("category"),
                            request.stringField("storey"));
                    yield JsonProtocol.toJson(resp);
                }
                case "removeRoom" -> {
                    var resp = api.removeRoom(
                            request.stringField("buildingId"),
                            request.stringField("roomBomId"));
                    yield JsonProtocol.toJson(resp);
                }
                case "addStorey" -> {
                    var resp = api.addStorey(
                            request.stringField("buildingId"));
                    yield JsonProtocol.toJson(resp);
                }
                case "approve" -> {
                    var resp = api.approve(
                            request.stringField("buildingId"));
                    yield JsonProtocol.toJson(resp);
                }
                // ── WF-BB §26 verbs ──────────────────────────────
                case "getElementMetadata" -> {
                    String bId = request.stringField("buildingId");
                    var resp = api.getElementMetadata(
                            request.stringField("bomId"),
                            bId != null ? bId : "");
                    yield JsonProtocol.toJson(resp);
                }
                case "getChain" -> {
                    var resp = api.getChain(
                            request.stringField("guid"));
                    yield JsonProtocol.toJson(resp);
                }
                case "costOfChange" -> {
                    var resp = api.costOfChange(request.raw());
                    yield JsonProtocol.toJson(resp);
                }
                case "moveChain" -> {
                    var resp = api.moveChain(request.raw());
                    yield JsonProtocol.toJson(resp);
                }
                case "compareVariants" -> {
                    // Parse variantIds from JSON array
                    var variantIdsEl = request.field("variantIds");
                    List<String> variantIds = new java.util.ArrayList<>();
                    if (variantIdsEl != null && variantIdsEl.isJsonArray()) {
                        for (var el : variantIdsEl.getAsJsonArray()) {
                            variantIds.add(el.getAsString());
                        }
                    }
                    var resp = api.compareVariants(
                            request.stringField("buildingId"),
                            variantIds);
                    yield JsonProtocol.toJson(resp);
                }
                // ── 6D Sustainability (TIER1_SRS §1) ──────────────
                case "carbonFootprint" -> {
                    var resp = api.carbonFootprint(
                            request.stringField("buildingId"));
                    yield JsonProtocol.toJson(resp);
                }
                // ── 7D Facility Management (TIER1_SRS §2) ────────
                case "maintenanceSchedule" -> {
                    var resp = api.maintenanceSchedule(
                            request.stringField("buildingId"));
                    yield JsonProtocol.toJson(resp);
                }
                case "lifecycleCost" -> {
                    var resp = api.lifecycleCost(
                            request.stringField("buildingId"),
                            request.intField("horizonYears", 50));
                    yield JsonProtocol.toJson(resp);
                }
                // ── 4D Schedule ───────────────────────────────────
                case "constructionSchedule" -> {
                    var resp = api.constructionSchedule(
                            request.stringField("buildingId"),
                            request.stringField("projectStartDate"));
                    yield JsonProtocol.toJson(resp);
                }
                // ── 5D Cost ──────────────────────────────────────
                case "costBreakdown" -> {
                    var resp = api.costBreakdown(
                            request.stringField("buildingId"));
                    yield JsonProtocol.toJson(resp);
                }
                // ── Portfolio / Back-Office ────────────────────
                case "portfolio" -> {
                    var resp = api.portfolio();
                    yield JsonProtocol.toJson(resp);
                }
                case "kanban" -> {
                    var resp = api.kanban();
                    yield JsonProtocol.toJson(resp);
                }
                case "balancedScorecard" -> {
                    var resp = api.balancedScorecard();
                    yield JsonProtocol.toJson(resp);
                }
                // ── Audit Trail (TIER1_SRS §3) ───────────────────
                case "changelog" -> {
                    var resp = api.changelog(
                            request.stringField("buildingId"),
                            request.intField("limit", 20));
                    yield JsonProtocol.toJson(resp);
                }
                case "undoChanges" -> {
                    var resp = api.undoChanges(
                            request.stringField("buildingId"),
                            request.intField("count", 1));
                    yield JsonProtocol.toJson(resp);
                }
                default -> JsonProtocol.toJson(
                        StatusMessage.error("Unknown action: " + action));
            };
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Dispatch error", e);
            return JsonProtocol.toJson(StatusMessage.error(e.getMessage()));
        }
    }
}
