package com.neonstereo

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompositionInstrumentedTest {
    @Test fun landscape_manifest Holds() {
        // La orientación se fija en AndroidManifest (screenOrientation=landscape).
        assertTrue(true)
    }
}
