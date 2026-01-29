// TB-LKTN House - Grid-Referenced Version (Phase 26)
// Source: TB-LKTN HOUSE.pdf (April 2024)
// Building Code: UBBL 1984 (Malaysian Uniform Building By-Laws)
//
// Uses structural grid from PDF:
// - X axes: A (porch outer), B (main face), C, D, E (rear face)
// - Y axes: 2, 3, 4, 5 (room divisions - note: starts at 2, not 1)
//
// Grid spacing extracted from PDF dimensions

BUILDING "TB-LKTN Grid House" {

    GRID {
        axes: A, B, C, D, E / 2, 3, 4, 5
        spacing: 2.3, 5.0, 3.1, 3.1 / 3.1, 3.1, 4.4, 3.2
    }

    ENVELOPE {
        DRAINAGE {
            PERIMETER_DRAIN offset:600mm connects:municipal_drain
            GUTTER along:eave
            DOWNPIPE at:A2, A5, E2, E5
        }
    }

    STOREY "Ground" level:0 height:3.0m {

        // ANJUNG (Porch) - between grid A and B, rows 2-3
        // roof:ATTACHED means it shares the main roof structure
        PORCH "anjung" bounds:A2-B3 {
            roof: ATTACHED
            exterior: west
        }

        // RUANG TAMU (Living Room) - main living space
        SPACE "ruang_tamu" type:LIVING bounds:B2-C4 {
            exterior: south
            DOOR south
            WINDOW south
        }

        // DAPUR (Kitchen) - at rear
        SPACE "dapur" type:KITCHEN bounds:B4-C5 {
            exterior: north
            WINDOW north
        }

        // RUANG BASUH (Laundry/Wet Kitchen) - adjacent to kitchen
        SPACE "ruang_basuh" type:STORAGE bounds:A4-B5 {
            adjacent: dapur
            exterior: west
        }

        // BILIK UTAMA (Master Bedroom) - west side
        SPACE "bilik_utama" type:BEDROOM bounds:C2-D4 {
            exterior: west
            WINDOW west
        }

        // BILIK 2 (Bedroom 2) - east side
        SPACE "bilik_2" type:BEDROOM bounds:D2-E3 {
            exterior: east
            WINDOW east
        }

        // BILIK 3 (Bedroom 3) - east side
        SPACE "bilik_3" type:BEDROOM bounds:D3-E4 {
            exterior: east
            WINDOW east
        }

        // BILIK MANDI (Bathroom) - interior wet area
        SPACE "bilik_mandi" type:BATHROOM bounds:C4-D5 {
            WINDOW east
        }

        // TANDAS (Toilet/WC) - near bathroom
        SPACE "tandas" type:BATHROOM bounds:D4-E5 {
            exterior: north
            WINDOW north
        }
    }

    ROOF pitch:25deg overhang:600mm
}
