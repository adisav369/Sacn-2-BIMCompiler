// TB-LKTN House - Malaysian Affordable Housing (RUMAH RAKYAT)
// Source: TB-LKTN HOUSE.pdf (April 2024)
// Building Code: UBBL 1984 (Malaysian Uniform Building By-Laws)
//
// Tests outlier handling framework with Malaysian room vocabulary.
// Expected: RUANG_TAMU, BILIK_UTAMA, DAPUR, etc. → mapped to standard types

BUILDING "TB-LKTN House" {

    STOREY "Ground" level:0 height:3.0m {

        // RUANG TAMU (Living Room) - main living space
        RUANG_TAMU "ruang_tamu" size:5.0x4.6m {
            exterior: south
            DOOR south
            WINDOW south
        }

        // ANJUNG (Porch) - covered entry
        ANJUNG "anjung" size:3.2x2.3m {
            adjacent: ruang_tamu
            exterior: west
        }

        // DAPUR (Kitchen)
        DAPUR "dapur" size:3.1x3.1m {
            exterior: north
            WINDOW north
        }

        // RUANG BASUH (Laundry/Wet Kitchen)
        RUANG_BASUH "ruang_basuh" size:3.1x1.6m {
            adjacent: dapur
            exterior: west
        }

        // BILIK UTAMA (Master Bedroom)
        BILIK_UTAMA "bilik_utama" size:4.4x3.1m {
            exterior: west
            WINDOW west
        }

        // BILIK 2 (Bedroom 2)
        BILIK_2 "bilik_2" size:3.1x3.1m {
            exterior: east
            WINDOW east
        }

        // BILIK 3 (Bedroom 3)
        BILIK_3 "bilik_3" size:3.1x3.1m {
            exterior: east
            WINDOW east
        }

        // BILIK MANDI (Bathroom)
        BILIK_MANDI "bilik_mandi" size:2.0x2.5m {
            WINDOW east
        }

        // TANDAS (Toilet/WC)
        TANDAS "tandas" size:1.5x1.6m {
            exterior: north
            WINDOW north
        }
    }

    ROOF pitch:25deg
}
