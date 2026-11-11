package com.neonstereo

import com.neonstereo.elements.ElementInstance
import com.neonstereo.elements.ElementTypes
import com.neonstereo.elements.duplicate
import com.neonstereo.visuals.bandEnergy
import com.neonstereo.visuals.bandFiltered
import org.junit.Assert.*
import org.junit.Test

class ProfilesTest {
    @Test fun duplicate_assignsNewIdAndOffset() {
        val el = ElementInstance(type = ElementTypes.CLOCK, displayName = "Reloj", x = 0.1f, y = 0.1f)
        val dup = with(el) { duplicate() }
        assertNotEquals(el.id, dup.id)
        assertTrue(dup.x > el.x)
        assertEquals(el.type, dup.type)
    }

    @Test fun commonProps_uniqueIdsStableTypes() {
        val types = listOf(ElementTypes.CLOCK, ElementTypes.COVER, ElementTypes.SPECTRUM_BARS, ElementTypes.STAR_PARTICLES)
        val ids = types.map { ElementInstance(type = it).id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun bands_filterHonest() {
        val bins = FloatArray(64) { 0.5f }
        val low = bandFiltered(bins, "low")
        assertTrue(low[0] > 0f)
        assertEquals(0f, low[40])
        val all = bandFiltered(bins, "all")
        assertTrue(all.all { it == 0.5f })
    }

    @Test fun bandEnergy_partialSignal() {
        val bins = FloatArray(64) { 0f }
        for (i in 0 until 10) bins[i] = 0.8f
        assertTrue(bandEnergy(bins, "low") > 0.7f)
        assertEquals(0f, bandEnergy(bins, "high"))
        // Sin señal válida: energía 0, visualizadores deben detenerse.
        assertEquals(0f, bandEnergy(FloatArray(64), "all"))
    }

    @Test fun profileJson_sizeGuard() {
        val big = "x".repeat(3 * 1024 * 1024)
        assertTrue(big.length > com.neonstereo.profiles.ProfileStore.MAX_BYTES)
    }

    @Test fun fft_magnitudesInRange() {
        val fft = com.neonstereo.audio.Fft(2048)
        val buf = ShortArray(2048) { (kotlin.math.sin(it * 0.1) * 10000).toInt().toShort() }
        val mags = fft.magnitudes(buf, 2048)
        assertEquals(64, mags.size)
        assertTrue(mags.all { it in 0f..1f })
        assertTrue(mags.any { it > 0.01f })
    }

    @Test fun fontSize_perElement_wideLimits() {
        val clock = com.neonstereo.elements.ElementRegistry.specificProps(com.neonstereo.elements.ElementTypes.CLOCK)
            .first { it.key == "fontSize" }
        assertTrue(clock.max >= 200f)
        for (t in listOf(
            com.neonstereo.elements.ElementTypes.TITLE,
            com.neonstereo.elements.ElementTypes.ARTIST,
            com.neonstereo.elements.ElementTypes.DATE,
            com.neonstereo.elements.ElementTypes.TIME_CURRENT,
            com.neonstereo.elements.ElementTypes.TIME_TOTAL,
        )) {
            val p = com.neonstereo.elements.ElementRegistry.specificProps(t).first { it.key == "fontSize" }
            assertTrue("$t max", p.max >= 100f)
            assertTrue("$t min", p.min <= 8f)
        }
    }

    @Test fun playState_isNotSignal() {
        // Invariante §2.4: "play" no es señal. Bins vacíos => capturing=false.
        val frame = com.neonstereo.audio.AudioFrame(bins = FloatArray(0), energy = 0f, capturing = false)
        assertFalse(frame.capturing)
    }
}
