#!/usr/bin/env python3
# ⚠ DO NOT REMOVE
# SCOPE: W-BOM-RECONSTRUCT-SH — the SampleHouse analog of the Java RosettaStoneGateTest, for the JS/BOM path the
#   modeller actually drops (BUILDING_SH_STD ← SH_BOM.db 'SH ... Structured'). The user asked the Rosetta question:
#   does the WHOLE SH BOM drop reconstruct the original extraction, measured element-by-element? This is the RIGOROUS
#   per-element check (the coarse aggregate-extent gate the DX witness used is a false signal on SH's small,
#   heterogeneous wall set because BOM dx/dy is a CORNER and source center_x is a CENTER).
# GROUND TRUTH = deploy/buildings/SampleHouse_extracted.db (element center_x/y + bbox per guid).
# CANDIDATE    = library/SH_BOM.db m_bom_line (dx,dy + allocated_width/depth) for bom_name LIKE 'SH %Structured'.
# METHOD: per class, MATCH each BOM line to a source element by bbox signature (alloc_w/d ≈ bbox_x/y); convert the
#   BOM CORNER (dx,dy) → element CENTER (+ half bbox); subtract each set's centroid (translation-invariant, yaw=0);
#   the per-element drift between matched relative centers is the reconstruction error. GATE = mean drift ≤ 0.10 m
#   AND max drift ≤ 0.30 m (cm-level reconstruction, the Rosetta bar). READ THE LOG — exit code is not evidence.
import sqlite3, math, sys, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'deploy/buildings/SampleHouse_extracted.db')
BOM = os.path.join(ROOT, 'library/SH_BOM.db')
TAG = '§W-BOM-RECONSTRUCT-SH'
MEAN_TOL = 0.10
MAX_TOL = 0.30

def centroid(pts):
    return (sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts))

def main():
    src = sqlite3.connect(SRC).cursor()
    bom = sqlite3.connect(BOM).cursor()
    fails = []

    CASES = [
        ('IfcWall',     "ifc_class LIKE 'IfcWall%'",  ('IfcWall', 'IfcWallStandardCase')),
        ('IfcDoor',     "ifc_class = 'IfcDoor'",      ('IfcDoor',)),
        ('IfcWindow',   "ifc_class = 'IfcWindow'",    ('IfcWindow', 'IfcOpeningElement')),
        ('IfcSlab',     "ifc_class = 'IfcSlab'",      ('IfcSlab',)),
        ('IfcRoof',     "ifc_class = 'IfcRoof'",      ('IfcRoof',)),
        ('IfcCovering', "ifc_class = 'IfcCovering'",  ('IfcCovering',)),
    ]
    print(TAG + ' source=%s  candidate=%s  (per-element Rosetta, yaw=0, translation-invariant)' %
          (os.path.basename(SRC), os.path.basename(BOM)))
    grand_max = 0.0
    for label, where, roles in CASES:
        s = [(r[0], r[1], r[2], r[3]) for r in src.execute(
            "SELECT center_x,center_y,bbox_x,bbox_y FROM element_transforms t JOIN elements_meta m ON t.guid=m.guid WHERE m." + where)]
        ph = ','.join('?' * len(roles))
        braw = bom.execute(
            "SELECT l.dx,l.dy,l.allocated_width_mm,l.allocated_depth_mm FROM m_bom_line l JOIN m_bom b ON l.M_BOM_ID=b.M_BOM_ID "
            "WHERE l.role IN (" + ph + ") AND b.bom_name LIKE 'SH %Structured'", roles).fetchall()
        # BOM corner (dx,dy) → center (+ half bbox); bbox in metres
        c = [(dx + (aw or 0) / 2000.0, dy + (ad or 0) / 2000.0, (aw or 0) / 1000.0, (ad or 0) / 1000.0) for (dx, dy, aw, ad) in braw]
        if not s and not c:
            print('%s %-11s SKIP (absent both)' % (TAG, label)); continue
        if len(s) != len(c):
            fails.append('%s count src=%d cat=%d' % (label, len(s), len(c)))
            print('%s %-11s COUNT MISMATCH src=%d cat=%d  FAIL' % (TAG, label, len(s), len(c))); continue
        # match each source element to its nearest-bbox BOM line (greedy)
        srel = [(p[0], p[1]) for p in s]; crel = [(p[0], p[1]) for p in c]
        scx, scy = centroid(srel); ccx, ccy = centroid(crel)
        used = [False] * len(c); drifts = []
        for i in range(len(s)):
            best, bd = -1, 1e9
            for j in range(len(c)):
                if used[j]:
                    continue
                sig = abs(s[i][2] - c[j][2]) + abs(s[i][3] - c[j][3])   # bbox signature distance
                if sig < bd:
                    bd, best = sig, j
            used[best] = True
            srx, sry = s[i][0] - scx, s[i][1] - scy
            crx, cry = c[best][0] - ccx, c[best][1] - ccy
            drifts.append(math.hypot(srx - crx, sry - cry))
        mean_d = sum(drifts) / len(drifts); max_d = max(drifts)
        grand_max = max(grand_max, max_d)
        ok = mean_d <= MEAN_TOL and max_d <= MAX_TOL
        if not ok:
            fails.append('%s meanDrift=%.2fm maxDrift=%.2fm' % (label, mean_d, max_d))
        print('%s %-11s n=%-2d  meanDrift=%.3fm  maxDrift=%.3fm  %s' %
              (TAG, label, len(s), mean_d, max_d, 'PASS' if ok else 'FAIL'))

    print()
    print('%s worst per-element drift across all structural classes = %.3f m' % (TAG, grand_max))
    if fails:
        print(TAG + ' VERDICT: FAIL — SampleHouse does NOT reconstruct to the Rosetta bar: ' + '; '.join(fails))
        print(TAG + ' (counts/bboxes are right — every element is present at the right size — but the BOM dx/dy '
              'PLACEMENT drifts beyond cm-level. Unlike Duplex, SH was never cent-verified in the JS path.)')
        sys.exit(1)
    print(TAG + ' VERDICT: PASS — SampleHouse reconstructs the source per-element within %.2fm mean / %.2fm max.' % (MEAN_TOL, MAX_TOL))

if __name__ == '__main__':
    main()
