package com.bim.designer.api;

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
                    CompileResponse cnResp = api.createNew(cnReq);
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
                default -> JsonProtocol.toJson(
                        StatusMessage.error("Unknown action: " + action));
            };
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Dispatch error", e);
            return JsonProtocol.toJson(StatusMessage.error(e.getMessage()));
        }
    }
}
