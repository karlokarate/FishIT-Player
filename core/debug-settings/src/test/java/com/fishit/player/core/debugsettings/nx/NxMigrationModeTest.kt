package com.fishit.player.core.debugsettings.nx

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for NX_ migration mode enums.
 *
 * **Reference:** docs/v2/NX_SSOT_CONTRACT.md, docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
 */
class NxMigrationModeTest {
    @Test
    fun `CatalogReadMode has exactly 3 values`() {
        val values = CatalogReadMode.entries
        assertEquals(3, values.size)
        assertEquals(
            listOf(
                CatalogReadMode.LEGACY_ONLY,
                CatalogReadMode.NX_ONLY,
                CatalogReadMode.SHADOW,
            ),
            values,
        )
    }

    @Test
    fun `CatalogWriteMode has exactly 3 values`() {
        val values = CatalogWriteMode.entries
        assertEquals(3, values.size)
        assertEquals(
            listOf(
                CatalogWriteMode.LEGACY_ONLY,
                CatalogWriteMode.NX_ONLY,
                CatalogWriteMode.DUAL,
            ),
            values,
        )
    }

    @Test
    fun `MigrationMode has exactly 3 values`() {
        val values = MigrationMode.entries
        assertEquals(3, values.size)
        assertEquals(
            listOf(
                MigrationMode.OFF,
                MigrationMode.INCREMENTAL,
                MigrationMode.FULL_REBUILD,
            ),
            values,
        )
    }

    @Test
    fun `NxUiVisibility has exactly 3 values`() {
        val values = NxUiVisibility.entries
        assertEquals(3, values.size)
        assertEquals(
            listOf(
                NxUiVisibility.HIDDEN,
                NxUiVisibility.DEBUG_ONLY,
                NxUiVisibility.FULL,
            ),
            values,
        )
    }

    @Test
    fun `enum names match expected string values`() {
        // Ensures serialization to DataStore is stable
        assertEquals("LEGACY_ONLY", CatalogReadMode.LEGACY_ONLY.name)
        assertEquals("NX_ONLY", CatalogReadMode.NX_ONLY.name)
        assertEquals("SHADOW", CatalogReadMode.SHADOW.name)

        assertEquals("LEGACY_ONLY", CatalogWriteMode.LEGACY_ONLY.name)
        assertEquals("NX_ONLY", CatalogWriteMode.NX_ONLY.name)
        assertEquals("DUAL", CatalogWriteMode.DUAL.name)

        assertEquals("OFF", MigrationMode.OFF.name)
        assertEquals("INCREMENTAL", MigrationMode.INCREMENTAL.name)
        assertEquals("FULL_REBUILD", MigrationMode.FULL_REBUILD.name)

        assertEquals("HIDDEN", NxUiVisibility.HIDDEN.name)
        assertEquals("DEBUG_ONLY", NxUiVisibility.DEBUG_ONLY.name)
        assertEquals("FULL", NxUiVisibility.FULL.name)
    }

    @Test
    fun `enum values can be parsed from string names`() {
        // Ensures deserialization from DataStore works
        assertEquals(CatalogReadMode.SHADOW, enumValueOf<CatalogReadMode>("SHADOW"))
        assertEquals(CatalogWriteMode.DUAL, enumValueOf<CatalogWriteMode>("DUAL"))
        assertEquals(MigrationMode.INCREMENTAL, enumValueOf<MigrationMode>("INCREMENTAL"))
        assertEquals(NxUiVisibility.DEBUG_ONLY, enumValueOf<NxUiVisibility>("DEBUG_ONLY"))
    }
}
