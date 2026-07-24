package whl.trending.ai.ui.theme

import whl.trending.ai.data.local.DEFAULT_SEED_ARGB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemePaletteTest {

    @Test
    fun palette_has_six_seeds() {
        assertEquals(6, PRESET_PALETTE.size)
    }

    @Test
    fun palette_ids_are_unique() {
        val ids = PRESET_PALETTE.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate id in palette")
    }

    @Test
    fun palette_argbs_have_full_alpha() {
        PRESET_PALETTE.forEach { seed ->
            val alpha = (seed.argb shr 24) and 0xFFL
            assertEquals(0xFFL, alpha, "seed ${seed.id} has non-opaque alpha: ${alpha.toString(16)}")
        }
    }

    @Test
    fun palette_first_entry_matches_default_seed() {
        assertEquals(DEFAULT_SEED_ARGB, PRESET_PALETTE.first().argb)
    }

    @Test
    fun palette_argbs_are_unique() {
        val argbs = PRESET_PALETTE.map { it.argb }
        assertEquals(argbs.size, argbs.toSet().size, "duplicate argb in palette")
        assertTrue(argbs.all { (it shr 24) and 0xFFL == 0xFFL })
    }
}
