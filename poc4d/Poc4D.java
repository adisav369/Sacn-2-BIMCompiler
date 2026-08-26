// Poc4D.java — the 4D schedule as a COMPOSITE. Run: java Poc4D.java
//
// ⚠ DO NOT REMOVE — SCOPE. Proves ORDER and STRUCTURE only. Durations here are 1 unit/element on
// purpose: pricing is duration_rule (work content / crews per trade, from LABOR_RATES) and is NOT
// what this POC proves. Read the printed report before any conclusion.
//
// WHY JAVA FIRST (user ruling 2026-08-26): the JS grew a flat element array + an elected support
// relation, and four rounds of patches could not fix what that costs. The Java side already owns
// the patterns this needs — BOMWalker/BOMVisitor and IRelatable.requires(). This is those patterns
// on the 4D problem, small enough to read in one sitting.
//
// THE MODEL — one type, two rules.
//   Node { children[], work }
//   STRUCTURE  an element attaches at the deepest node that fully contains it
//   TIME       siblings run in order; a parent spans its children
// Composite handles the rest: non-uniform depth absorbs the unlevelled and the level-spanning,
// so neither is a special case. A Leaf is a node with NO INTERNAL ORDER — that is the recursion's
// termination rule, not an invented "assembly" taxonomy.
//
// Geometry NEVER authors order here. It is used once, to decide WHERE TO SPLIT a cell into ordered
// layers (a deterministic partition), never to elect which of N candidates wins.

import java.util.*;

public class Poc4D {

  // ── the one type ───────────────────────────────────────────────────────────────────────────
  static abstract class Node {
    final String id;
    Node(String id) { this.id = id; }
    abstract long layout(long t0);   // place me starting no earlier than t0; return my finish
    abstract long start();
    abstract long finish();
    List<Node> children() { return List.of(); }
  }

  static final class Leaf extends Node {
    final Elem e; long s, f;
    Leaf(Elem e) { super(e.guid); this.e = e; }
    long layout(long t0) { s = t0; f = t0 + e.workUnits; return f; }
    long start() { return s; } long finish() { return f; }
  }

  /** Composite: siblings run IN ORDER; the parent spans them. `lanes` = crews working in parallel. */
  static final class Group extends Node {
    final List<Node> kids = new ArrayList<>();
    final int lanes;
    Group(String id) { this(id, 1); }
    Group(String id, int lanes) { super(id); this.lanes = Math.max(1, lanes); }
    List<Node> children() { return kids; }
    Group add(Node n) { kids.add(n); return this; }
    long layout(long t0) {
      long[] lane = new long[lanes];
      Arrays.fill(lane, t0);
      for (Node k : kids) {                       // round-robin onto the earliest-free lane
        int b = 0;
        for (int i = 1; i < lanes; i++) if (lane[i] < lane[b]) b = i;
        lane[b] = k.layout(lane[b]);
      }
      long mx = t0; for (long l : lane) mx = Math.max(mx, l);
      return mx;
    }
    long start()  { return kids.stream().mapToLong(Node::start).min().orElse(0); }
    long finish() { return kids.stream().mapToLong(Node::finish).max().orElse(0); }
  }

  // ── an element ─────────────────────────────────────────────────────────────────────────────
  static final class Elem {
    String guid, cls, name, storey, phase; double cx, cy, cz, bx, by, bz;
    long workUnits = 1;                     // SCOPE: not what this POC proves (see header)
    double z0() { return cz - bz / 2; }
    double z1() { return cz + bz / 2; }
    double x0() { return cx - bx / 2; }  double x1() { return cx + bx / 2; }
    double y0() { return cy - by / 2; }  double y1() { return cy + by / 2; }
  }

  // Shipped constants, not re-typed by feel: schedule_gate.js EPS/GAP.
  static final double EPS = 0.05, GAP = 0.5;
  // PLACEHOLDER — real value is LABOR_RATES[trade].max_crews, per trade. See §POC_CREW_LANES.
  static final int CREW_CAP = 2;

  /** bearing-below. §POC_BEARS_BOUND (found by this sandbox, 2026-08-26): the SHIPPED predicate
   *  (schedule_gate.js / support_sweep.js) is `S.bz < T.bz - EPS && S.tz >= T.bz - GAP` — it bounds
   *  the support's BASE below the target but never bounds its TOP. So a 6.4m riser whose base is at
   *  -0.1 "bears" every element in the building above it, and a tall wall bears the roof two floors
   *  up. Measured here: the riser alone produced 5 of 17 false bearing edges on 20 elements.
   *  A real bearing contact has the support's TOP at the target's BASE — both bounds, not one. */
  static boolean bears(Elem s, Elem t) {
    if (s == t) return false;
    boolean xy = s.x0() <= t.x1() && s.x1() >= t.x0() && s.y0() <= t.y1() && s.y1() >= t.y0();
    return xy && s.z0() < t.z0() - EPS
              && s.z1() >= t.z0() - GAP && s.z1() <= t.z0() + GAP;   // TOP meets BASE
  }

  // ── the programme, read from 4D_template.json's declared order ──────────────────────────────
  static final String[] PHASES = {
    "Substructure", "Superstructure", "Architecture", "MEP Rough-in", "MEP Final", "Finishes" };

  // class -> phase. The shipped classification (viewer/rates/sequence_rules.json) is the real
  // source; this is the minimal subset the sandbox exercises, and it is a LOOKUP, never a rule.
  static String phaseOf(String cls) {
    if (cls.startsWith("IfcFooting") || cls.startsWith("IfcPile")) return "Substructure";
    if (cls.startsWith("IfcColumn") || cls.startsWith("IfcBeam") || cls.startsWith("IfcSlab")) return "Superstructure";
    if (cls.startsWith("IfcWall") || cls.startsWith("IfcDoor") || cls.startsWith("IfcWindow")
        || cls.startsWith("IfcRoof") || cls.startsWith("IfcCurtainWall")) return "Architecture";
    if (cls.equals("IfcFlowSegment") || cls.equals("IfcFlowFitting")) return "MEP Rough-in";
    if (cls.equals("IfcFlowTerminal") || cls.startsWith("IfcLight")) return "MEP Final";
    if (cls.startsWith("IfcCovering") || cls.startsWith("IfcFurni")) return "Finishes";
    return "Architecture";                       // sequence_rules' own _default
  }

  public static void main(String[] args) {
    boolean coherent = args.length > 0 && args[0].equals("coherent");
    List<Elem> els = coherent ? Sandbox.coherent() : Sandbox.rows();
    System.out.println("§POC_FIXTURE " + (coherent ? "COHERENT (expect 0 violations AND 0 defects)"
        : "HELL (expect 0 violations, N named defects)"));
    for (Elem e : els) e.phase = phaseOf(e.cls);

    // ── STRUCTURE: attach at the deepest node that fully contains it ──────────────────────────
    // A level contains an element iff the element's Z sits inside that level's band. An element
    // with no storey, or one whose Z crosses two bands, is contained by NO level — so it attaches
    // one level up, at the phase. Composite's non-uniform depth IS the Null Object here: nothing
    // downstream tests for null.
    Map<String, double[]> bands = levelBands(els);
    List<String> levels = new ArrayList<>(bands.keySet());
    levels.sort(Comparator.comparingDouble(l -> bands.get(l)[0]));

    Group building = new Group("BUILDING");
    Map<String, List<Elem>> report = new LinkedHashMap<>();

    // §POC_TREE_SHAPE (corrected 2026-08-26 by this sandbox's own first run). v1 nested
    // PHASE over LEVEL and produced 17 bearing violations: all-Superstructure-before-all-
    // Architecture puts the L2 slab before the L1 walls it rests on. 4D_template.json already
    // says otherwise — `within_level` chains phases INSIDE a level, `across_levels` ladders each
    // phase to itself one level down. That is LEVEL-major. Levels are siblings in order (the
    // ladder); phases are siblings in order inside each (the chain). One rule, two depths.
    for (String lv : levels) {
      Group level = new Group(lv);
      for (String ph : PHASES) {
        List<Elem> cell = new ArrayList<>();
        for (Elem e : els) if (e.phase.equals(ph) && lv.equals(containingLevel(e, bands, levels))) cell.add(e);
        if (cell.isEmpty()) continue;
        level.add(cellNode(lv + " / " + ph, cell, report));
      }
      if (!level.kids.isEmpty()) building.add(level);
    }
    // attaches-higher: contained by NO level (unlevelled, or spans bands) -> BUILDING scope, and
    // it goes LAST so it can never precede what carries it. Composite's non-uniform depth is the
    // Null Object: no call site below tests for a missing level.
    Group hi = new Group("<building-scope>");
    for (String ph : PHASES) {
      List<Elem> cell = new ArrayList<>();
      for (Elem e : els) if (e.phase.equals(ph) && containingLevel(e, bands, levels) == null) cell.add(e);
      if (!cell.isEmpty()) hi.add(cellNode("<building-scope> / " + ph, cell, report));
    }
    if (!hi.kids.isEmpty()) building.add(hi);

    building.layout(0);
    emit(building, report, els, bands, levels);
  }

  /** A cell splits into ORDERED LAYERS — topological layers of its own bearing relation. Each
   *  layer is a Leaf-group: no two members bear one another, so they may run in parallel. */
  static Node cellNode(String id, List<Elem> cell, Map<String, List<Elem>> report) {
    report.put(id, cell);
    List<List<Elem>> layers = topoLayers(cell);
    Group g = new Group(id);                                 // layers are siblings, IN ORDER
    int i = 0;
    for (List<Elem> layer : layers) {
      // §POC_CREW_LANES (found by this sandbox's second run): v1 set lanes = layer.size(), i.e.
      // INFINITE CREWS — a 4-element layer started 4 elements at one instant, which is HELL B
      // (review session §S14: kernel_ops piles of >=20) reappearing as a "parallel" layer.
      // Lanes are a CAPACITY, not a headcount. CAP is a placeholder: the real number is
      // sequence_rules.json LABOR_RATES[trade].max_crews (present on all 10 trades, range 1-3),
      // resolved per trade. Stated so it is a wiring step, not a rediscovery.
      Group lg = new Group(id + " · layer " + (++i), CREW_CAP);
      for (Elem e : layer) lg.add(new Leaf(e));
      g.add(lg);
    }
    return g;
  }

  static List<List<Elem>> topoLayers(List<Elem> cell) {
    List<List<Elem>> out = new ArrayList<>();
    List<Elem> left = new ArrayList<>(cell);
    while (!left.isEmpty()) {
      List<Elem> free = new ArrayList<>();
      for (Elem t : left) {
        boolean blocked = false;
        for (Elem s : left) if (bears(s, t)) { blocked = true; break; }
        if (!blocked) free.add(t);
      }
      if (free.isEmpty()) { out.add(new ArrayList<>(left)); break; }   // cycle: report, never loop
      out.add(free);
      left.removeAll(free);
    }
    return out;
  }

  /** §POC_LEVEL_IS_A_DATUM (found by this sandbox's second run, and it is the L0 hole made
   *  concrete). v1 took a level's band as the ENVELOPE of its members' Z. One 6.4m riser labelled
   *  L1 then stretched L1 to [-1.0 .. 6.3], overlapping L2 [3.21 .. 6.4] — so EVERY L2 element
   *  "spanned two bands" and fell out to building scope. A band derived from its members is not a
   *  band; it is whatever the members happened to do. The project already records this in the live
   *  log: "zone is a median-Z INFERENCE, not IFC truth" (§ZONE_INDEX, 30.8% noStorey on HHS).
   *  A level is a DATUM: its floor, up to the next level's floor. Bands are disjoint BY
   *  CONSTRUCTION, so "which level contains this element" has exactly one answer. */
  static Map<String, double[]> levelBands(List<Elem> els) {
    Map<String, Double> floor = new LinkedHashMap<>();
    for (Elem e : els) {                                    // datum = the lowest base on the level
      if (e.storey == null) continue;
      floor.merge(e.storey, e.z0(), Math::min);
    }
    List<String> ordered = new ArrayList<>(floor.keySet());
    ordered.sort(Comparator.comparingDouble(floor::get));
    Map<String, double[]> b = new LinkedHashMap<>();
    for (int i = 0; i < ordered.size(); i++) {
      double lo = floor.get(ordered.get(i));
      double hi = (i + 1 < ordered.size()) ? floor.get(ordered.get(i + 1)) : Double.MAX_VALUE;
      b.put(ordered.get(i), new double[]{ lo, hi });
    }
    return b;
  }

  /** deepest containing level, or null -> attach one level up (phase scope). */
  static String containingLevel(Elem e, Map<String, double[]> bands, List<String> levels) {
    if (e.storey == null) return null;                       // HOLE 1 -> attaches higher
    String byBase = null, byTop = null;
    for (String l : levels) {
      double[] bd = bands.get(l);
      if (e.z0() >= bd[0] - EPS && e.z0() < bd[1] - EPS) byBase = l;
      if (e.z1() >  bd[0] + EPS && e.z1() <= bd[1] + EPS) byTop = l;
    }
    if (byBase == null) return null;
    if (byTop != null && !byTop.equals(byBase)) return null;  // HOLE 2 spans bands -> attaches higher
    return byBase;
  }

  // ── output ─────────────────────────────────────────────────────────────────────────────────
  static void emit(Group building, Map<String, List<Elem>> report, List<Elem> els,
                   Map<String, double[]> bands, List<String> levels) {
    StringBuilder j = new StringBuilder();
    j.append("{\n  \"model\": \"composite\",\n  \"unit\": \"work-units (see header: not priced)\",\n");
    j.append("  \"span\": [").append(building.start()).append(", ").append(building.finish()).append("],\n");
    j.append("  \"tasks\": [\n");
    List<String> rows = new ArrayList<>();
    walk(building, 0, rows);
    j.append(String.join(",\n", rows)).append("\n  ]\n}");
    System.out.println(j);

    System.out.println("\n§POC_LEVELS " + levels + " bands=" +
        levels.stream().map(l -> l + "[" + bands.get(l)[0] + ".." + bands.get(l)[1] + "]").toList());

    // The two hells, checked on the emitted times.
    List<Leaf> leaves = new ArrayList<>(); collect(building, leaves);
    Map<Long, Integer> hist = new TreeMap<>();
    for (Leaf l : leaves) hist.merge(l.s, 1, Integer::sum);
    int maxPile = hist.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    System.out.println("§POC_STACKING maxSimultaneousStarts=" + maxPile +
        " (HELL B — every element in a cell sharing one instant)");

    Map<String, Leaf> byGuid = new HashMap<>();
    for (Leaf l : leaves) byGuid.put(l.e.guid, l);
    Map<String,String> cellOf = new HashMap<>();
    for (Map.Entry<String,List<Elem>> en : report.entrySet())
      for (Elem e : en.getValue()) cellOf.put(e.guid, en.getKey());

    // §5.1 (4D_BAR_MODEL) — "cycles are data defects, name them, never schedule around them".
    // TWO POPULATIONS, and only one of them is the model's fault:
    //   SCHEDULING VIOLATION — both elements sit in the SAME cell, so the cell's own ordered
    //     layers were supposed to separate them. Must be 0. This is the model being wrong.
    //   DATA DEFECT — the bearing element is classified into a LATER phase/level than the thing
    //     it carries. No ordering of a correct programme can fix that; the classification is
    //     wrong. Reported by name, never scheduled around.
    int viol = 0; List<String> defects = new ArrayList<>();
    for (Elem t : els) for (Elem s2 : els) {
      if (!bears(s2, t)) continue;
      Leaf ls = byGuid.get(s2.guid), lt = byGuid.get(t.guid);
      if (ls == null || lt == null || lt.s >= ls.f) continue;
      String cs = cellOf.get(s2.guid), ct = cellOf.get(t.guid);
      if (cs != null && cs.equals(ct)) { viol++;
        System.out.println("   §POC_VIOLATION " + t.name + " before " + s2.name + " in " + cs);
      } else {
        defects.add(s2.name + " (" + cs + ") bears " + t.name + " (" + ct + ")");
      }
    }
    System.out.println("§POC_VIOLATIONS total=" + viol + " (same-cell — the model must prevent these; MUST BE 0)");
    System.out.println("§POC_DATA_DEFECTS total=" + defects.size() + " (bearing element classified AFTER what it carries — §5.1: named, never scheduled around)");
    for (String d : defects) System.out.println("   §POC_DEFECT " + d);
    System.out.println("§POC_ATTACH_HIGHER " + report.keySet().stream()
        .filter(k -> k.contains("<phase-scope>")).toList() + " (HOLE 1+2 absorbed by non-uniform depth)");
    System.out.println("§POC_LEAVES " + leaves.size() + "/" + els.size());
  }

  static void collect(Node n, List<Leaf> out) {
    if (n instanceof Leaf l) { out.add(l); return; }
    for (Node k : n.children()) collect(k, out);
  }

  static void walk(Node n, int depth, List<String> rows) {
    String pad = "    ";
    if (n instanceof Group g && !g.kids.isEmpty()) {
      rows.add(pad + "{\"id\": \"" + n.id + "\", \"depth\": " + depth +
          ", \"start\": " + n.start() + ", \"finish\": " + n.finish() +
          ", \"children\": " + g.kids.size() + "}");
      for (Node k : g.kids) walk(k, depth + 1, rows);
    } else if (n instanceof Leaf l) {
      rows.add(pad + "{\"id\": \"" + l.e.guid + "\", \"depth\": " + depth +
          ", \"start\": " + l.s + ", \"finish\": " + l.f +
          ", \"cls\": \"" + l.e.cls + "\", \"name\": \"" + l.e.name + "\"}");
    }
  }

  // ── the sandbox (mirrors witness_kit/generators/sandbox_model.js row-for-row) ───────────────
  static final class Sandbox {
    static Elem e(String cls, String name, String storey, double cx, double cy, double cz,
                  double bx, double by, double bz) {
      Elem x = new Elem(); x.cls = cls; x.name = name; x.storey = storey;
      x.cx = cx; x.cy = cy; x.cz = cz; x.bx = bx; x.by = by; x.bz = bz;
      return x;
    }
    /** COHERENT variant — the same building modelled correctly. Two changes, both real building
     *  logic, neither a fudge to make a test pass:
     *    (1) a floor slab sits at its level's DATUM (walls stand ON it); v1 put it at the ceiling,
     *        so it was really the level above's floor wearing this level's label — the exact
     *        storey-labelling defect the project logs as §GANTT_STOREY_Z reassigned=2120.
     *    (2) the L2 floor slab is L2's, so the L1 walls beneath it carry nothing.
     *  Proves the model reaches 0 VIOLATIONS **and** 0 DEFECTS on coherent data — without which
     *  "0 violations" would only mean the failures had been renamed. */
    static List<Elem> coherent() {
      List<Elem> r = new ArrayList<>(List.of(
        e("IfcFooting","Footing L1","L1", 0,0,-0.50, 4.0,4.0,1.00),
        e("IfcSlab","Floor slab L1","L1", 0,0,0.10, 4.0,4.0,0.20),
        e("IfcColumn","Column L1","L1", -1.8,0,1.90, 0.4,0.4,3.40),
        e("IfcWallStandardCase","Wall lower L1","L1", 0,-1.9,0.70, 4.0,0.2,1.00),
        e("IfcWallStandardCase","Wall upper L1","L1", 0,-1.9,2.40, 4.0,0.2,2.40),
        e("IfcWallStandardCase","Wall N L1","L1", 0,1.9,1.90, 4.0,0.2,3.40),
        e("IfcWallStandardCase","Wall E L1","L1", 1.9,0,1.90, 0.2,4.0,3.40),
        e("IfcDoor","Door L1","L1", 0,-1.9,1.25, 0.9,0.2,2.10),
        e("IfcFlowSegment","Duct L1","L1", 0,0,3.30, 3.0,0.3,0.30),
        e("IfcFlowTerminal","Light L1","L1", 0.8,0.8,3.52, 0.6,0.6,0.06),
        e("IfcCovering","Floor fin L1","L1", 0,0,0.22, 4.0,4.0,0.04),
        e("IfcSlab","Floor slab L2","L2", 0,0,3.70, 4.0,4.0,0.20),
        e("IfcColumn","Column L2","L2", -1.8,0,5.50, 0.4,0.4,3.40),
        e("IfcWallStandardCase","Wall S L2","L2", 0,-1.9,5.50, 4.0,0.2,3.40),
        e("IfcFlowSegment","Duct L2","L2", 0,0,6.90, 3.0,0.3,0.30),
        e("IfcCovering","Floor fin L2","L2", 0,0,3.82, 4.0,4.0,0.04),
        e("IfcBuildingElementProxy","Unlevelled proxy",null, 1.5,1.5,2.00, 0.3,0.3,1.00),
        e("IfcFlowSegment","Riser L1-L2","L1", 1.7,1.7,3.60, 0.2,0.2,7.00),
        e("IfcBuildingElementProxy","Orphan","L2", 3.6,3.6,5.00, 0.2,0.2,0.40)
      ));
      int i = 0; for (Elem x : r) x.guid = String.format("COH%03d", i++);
      return r;
    }
    static List<Elem> rows() {
      List<Elem> r = new ArrayList<>(List.of(
        e("IfcFooting","Footing L1","L1", 0,0,-0.50, 4.0,4.0,1.00),
        e("IfcColumn","Column L1","L1", -1.8,0,1.50, 0.4,0.4,3.00),
        e("IfcSlab","Slab L1","L1", 0,0,3.10, 4.0,4.0,0.20),
        e("IfcWallStandardCase","Wall lower L1","L1", 0,-1.9,-0.50, 4.0,0.2,1.00),  // HELL A
        e("IfcWallStandardCase","Wall upper L1","L1", 0,-1.9,1.50, 4.0,0.2,3.00),   // HELL A
        e("IfcWallStandardCase","Wall N L1","L1", 0,1.9,1.50, 4.0,0.2,3.00),        // HELL B
        e("IfcWallStandardCase","Wall E L1","L1", 1.9,0,1.50, 0.2,4.0,3.00),        // HELL B
        e("IfcWallStandardCase","Wall W L1","L1", -1.9,0,1.50, 0.2,4.0,3.00),       // HELL B
        e("IfcDoor","Door L1","L1", 0,-1.9,1.05, 0.9,0.2,2.10),
        e("IfcFlowSegment","Duct L1","L1", 0,0,2.75, 3.0,0.3,0.30),                 // HELL C
        e("IfcFlowTerminal","Light L1","L1", 0.8,0.8,2.92, 0.6,0.6,0.06),           // HELL C
        e("IfcCovering","Floor fin L1","L1", 0,0,0.02, 4.0,4.0,0.02),
        e("IfcColumn","Column L2","L2", -1.8,0,4.80, 0.4,0.4,3.00),
        e("IfcSlab","Slab L2","L2", 0,0,6.30, 4.0,4.0,0.20),
        e("IfcWallStandardCase","Wall S L2","L2", 0,-1.9,4.80, 4.0,0.2,3.00),
        e("IfcFlowSegment","Duct L2","L2", 0,0,5.95, 3.0,0.3,0.30),
        e("IfcCovering","Floor fin L2","L2", 0,0,3.22, 4.0,4.0,0.02),
        e("IfcBuildingElementProxy","Unlevelled proxy",null, 1.5,1.5,2.00, 0.3,0.3,1.00), // HOLE 1
        e("IfcFlowSegment","Riser L1-L2","L1", 1.7,1.7,3.10, 0.2,0.2,6.40),               // HOLE 2
        e("IfcBuildingElementProxy","Orphan","L2", 3.6,3.6,5.00, 0.2,0.2,0.40)
      ));
      int i = 0; for (Elem x : r) x.guid = String.format("SBX%03d", i++);
      return r;
    }
  }
}
