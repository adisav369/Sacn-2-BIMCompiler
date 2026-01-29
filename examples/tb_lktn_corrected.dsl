// TB-LKTN House - CORRECTED Layout (From PDF Verification)
// Source: TB-LKTN HOUSE.pdf (April 2024) + Layout Verification
// Building Code: UBBL 1984 (Malaysian Uniform Building By-Laws)
//
// KEY CORRECTIONS from verification:
// 1. No dedicated corridor - RUANG TAMU serves as circulation hub
// 2. BILIK 2 and BILIK 3 are stacked vertically (not side by side)
// 3. Wet areas (BILIK MANDI, TANDAS) in back-left corner
// 4. RUANG MAKAN (dining) between living and kitchen
// 5. ANJUNG (porch) only extends across living room, not full width

BUILDING "TB-LKTN House Corrected" {

    // Grid from PDF analysis (millimeters converted to meters)
    // X: A=0, B=1.3, C=4.4, D=8.1, E=11.2
    // Y: Row1=0, Row2=2.3, Row3=5.4, Row4=7.0, Row5=8.5
    GRID {
        axes: A, B, C, D, E / 1, 2, 3, 4, 5
        spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.6, 1.5
    }

    ENVELOPE {
        DRAINAGE {
            PERIMETER_DRAIN offset:600mm connects:municipal_drain
            DOWNPIPE at:A5, E5, A2, E2
        }
    }

    STOREY "Ground" level:0 height:3.0m {

        // === FRONT ZONE (Row 1-2) ===

        // ANJUNG (Porch) - front entry, only spans C-D
        PORCH "anjung" bounds:C1-D2 {
            roof: ATTACHED
            exterior: south
            DOOR south  // Main entry D1
        }

        // === MAIN LIVING ZONE (Row 2-3) ===

        // BILIK UTAMA (Master Bedroom) - front left, large
        // Spans A-C (two grid zones)
        SPACE "bilik_utama" type:BEDROOM bounds:A2-C3 {
            exterior: west
            exterior: south
            adjacent: ruang_tamu
            WINDOW west
            WINDOW south
        }

        // RUANG TAMU (Living Room) - front center, circulation hub
        SPACE "ruang_tamu" type:LIVING bounds:C2-D3 {
            exterior: south
            adjacent: anjung
            adjacent: bilik_utama
            adjacent: bilik_2
            adjacent: ruang_makan
            DOOR south to:anjung
            WINDOW south
        }

        // BILIK 2 (Bedroom 2) - front right
        SPACE "bilik_2" type:BEDROOM bounds:D2-E3 {
            exterior: east
            exterior: south
            adjacent: ruang_tamu
            adjacent: bilik_3
            WINDOW east
        }

        // === MIDDLE ZONE (Row 3-4) ===

        // BILIK MANDI (Bathroom) - left side, behind master
        SPACE "bilik_mandi" type:BATHROOM bounds:A3-B4 {
            adjacent: bilik_utama
            adjacent: tandas
            WINDOW east  // Small window for ventilation
        }

        // RUANG MAKAN (Dining Room) - center, connects living to kitchen
        SPACE "ruang_makan" type:LIVING bounds:C3-D4 {
            adjacent: ruang_tamu
            adjacent: dapur
        }

        // BILIK 3 (Bedroom 3) - right side, spans Row 3-5 (tall)
        // Note: This room is larger vertically
        SPACE "bilik_3" type:BEDROOM bounds:D3-E5 {
            exterior: east
            exterior: north
            adjacent: bilik_2
            WINDOW east
        }

        // === BACK ZONE (Row 4-5) ===

        // TANDAS (Toilet) - back left corner
        SPACE "tandas" type:BATHROOM bounds:A4-B5 {
            exterior: west
            exterior: north
            adjacent: bilik_mandi
            adjacent: ruang_basuh
            WINDOW north
        }

        // RUANG BASUH (Laundry/Wet Kitchen) - behind bathroom
        SPACE "ruang_basuh" type:STORAGE bounds:B4-C5 {
            exterior: north
            adjacent: tandas
            adjacent: dapur
            WINDOW north
        }

        // DAPUR (Kitchen) - back center
        SPACE "dapur" type:KITCHEN bounds:C4-D5 {
            exterior: north
            adjacent: ruang_makan
            adjacent: ruang_basuh
            WINDOW north
        }
    }

    ROOF pitch:25deg overhang:600mm
}
