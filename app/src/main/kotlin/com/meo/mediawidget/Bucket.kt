package com.meo.mediawidget

import android.util.SizeF

enum class Bucket {
    MICRO_BAR,  // eine Zeile, schmal: Cover, Text, Play
    BAR,        // eine Zeile: dazu Zurück/Weiter
    MID_CARD,   // zwei Zeilen: Cover links, Text und Knöpfe rechts
    WIDE,       // hoch oder quadratisch: Cover oben
    MEGA;       // gross im Querformat: wie Karte, mit mehr Aktionen

    companion object {

        /** Wählt die Layout-Stufe aus der Grösse, die der Launcher meldet (dp). */
        fun pickForSize(sizeDp: SizeF): Bucket {
            val w = sizeDp.width
            val h = sizeDp.height
            return when {
                // Eine Zeile. Launcher melden dafür 70–90 dp, zwei Zeilen ab etwa 140 dp.
                h < 110f -> if (w < 250f) MICRO_BAR else BAR
                // Zwei Zeilen: Karte mit Cover links; ist sie dafür zu schmal, Cover oben.
                h < 200f -> if (w < 230f) WIDE else MID_CARD
                // Ab drei Zeilen entscheidet die Form: klares Querformat → Cover links, sonst Cover oben.
                w >= h * 1.3f -> MEGA
                else -> WIDE
            }
        }
    }
}
