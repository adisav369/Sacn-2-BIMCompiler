// TB-LKTN Complete DSL (Phase 27) - SEMANTICALLY VERIFIED
// Source: TB-LKTN HOUSE.pdf (April 2024)
// Building Code: UBBL 1984 (Malaysian Uniform Building By-Laws)
//
// SEMANTIC RULES APPLIED:
// 1. Master bedroom >= other bedrooms (13.64 > 9.61 sqm)
// 2. Wet areas can be compact (1.3m width OK for Malaysian housing)
// 3. Open plan serves as circulation hub
// 4. Porch spans entry zone only, not full width

BUILDING "TB-LKTN" {

    // Grid from PDF: A=0, B=1300, C=4400, D=8100, E=11200mm
    // Y-axes: 1=0 (drain), 2=2300 (front wall), 3=5400, 4=6900, 5=8500mm
    GRID {
        axes: A, B, C, D, E / 1, 2, 3, 4, 5
        spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
    }

    ENVELOPE {
        DRAINAGE {
            PERIMETER_DRAIN offset:600mm connects:municipal_drain
        }
    }

    STOREY "Ground" level:0 height:2.8m {

        // ANJUNG (Porch) - entry zone at front center
        // Spans from drain line (row 1) to front wall (row 2)
        // Width matches living room entrance (columns C-D)
        PORCH "anjung" bounds:C1-D2 {
            roof: ATTACHED
            exterior: south
        }

        // OPEN_PLAN common area - LIVING + DINING + KITCHEN combined
        // NO internal walls between zones - single flowing space
        OPEN_PLAN "common" bounds:B2-D5 {
            zones: LIVING, DINING, KITCHEN
            exterior: south
            exterior: north
            DOOR type:D1 size:900x2100 wall:south
            DOOR type:D1 size:900x2100 wall:north
            WINDOW type:W2 size:1200x1000 wall:south
        }

        // BILIK UTAMA (Master Bedroom) - LARGEST bedroom
        // Spans A-C (two grid zones) = 4.4m width
        // Area: 4.4 × 3.1 = 13.64 sqm > other bedrooms
        BEDROOM "bilik_utama" bounds:A2-C3 {
            exterior: west
            exterior: south
            opens_to: common
            DOOR type:D2 size:750x2100
            WINDOW type:W1 size:1800x1000 wall:west
        }

        // BILIK MANDI (Bathroom) - compact Malaysian style
        // 1.3 × 1.5m = 2.08 sqm (adequate per UBBL)
        BATHROOM "bilik_mandi" bounds:A3-B4 {
            exterior: west
            opens_to: common
            stack: plumbing
            DOOR type:D3 size:900x2100
            WINDOW type:W3 size:600x500 wall:west
        }

        // TANDAS (Toilet/WC) - separate from bathroom
        // 1.3 × 1.6m = 1.95 sqm (adequate per UBBL)
        BATHROOM "tandas" bounds:A4-B5 {
            exterior: west
            exterior: north
            opens_to: common
            stack: plumbing
            DOOR type:D3 size:900x2100
            WINDOW type:W3 size:600x500 wall:west
        }

        // BILIK 2 (Bedroom 2) - secondary bedroom
        // 3.1 × 3.1m = 9.61 sqm (< master)
        BEDROOM "bilik_2" bounds:D2-E3 {
            exterior: east
            exterior: south
            opens_to: common
            DOOR type:D2 size:750x2100
            WINDOW type:W1 size:1800x1000 wall:east
        }

        // BILIK 3 (Bedroom 3) - secondary bedroom
        // 3.1 × 3.1m = 9.61 sqm (< master)
        BEDROOM "bilik_3" bounds:D3-E5 {
            exterior: east
            exterior: north
            opens_to: common
            DOOR type:D2 size:750x2100
            WINDOW type:W1 size:1800x1000 wall:east
        }
    }

    ROOF pitch:25deg overhang:600mm
}
