package com.neonstereo.visuals

/** Ganancia por banda seleccionada low/mid/high/all. bins: 64 lineales ~0-22kHz. */
fun bandFiltered(bins: FloatArray, band: String): FloatArray {
    if (bins.isEmpty()) return bins
    val out = bins.copyOf()
    val (from, to) = when (band) {
        "low" -> 0 to 10
        "mid" -> 10 to 32
        "high" -> 32 to 64
        else -> 0 to 64
    }
    for (i in out.indices) if (i < from || i >= to) out[i] = 0f
    return out
}

fun bandEnergy(bins: FloatArray, band: String): Float {
    if (bins.isEmpty()) return 0f
    val (from, to) = when (band) {
        "low" -> 0 to 10
        "mid" -> 10 to 32
        "high" -> 32 to 64
        else -> 0 to 64
    }
    var s = 0f; var n = 0
    for (i in from until to.coerceAtMost(bins.size)) { s += bins[i]; n++ }
    return if (n == 0) 0f else s / n
}
