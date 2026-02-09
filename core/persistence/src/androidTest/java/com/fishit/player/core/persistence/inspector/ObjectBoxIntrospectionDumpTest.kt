package com.fishit.player.core.persistence.inspector

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fishit.player.core.persistence.obx.ObxStore
import io.objectbox.BoxStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for ObjectBox runtime introspection dump feature.
 *
 * Verifies that schema extraction works correctly and produces valid JSON output.
 */
@RunWith(AndroidJUnit4::class)
class ObjectBoxIntrospectionDumpTest {
    private lateinit var context: Context
    private lateinit var boxStore: BoxStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        boxStore = ObxStore.get(context)
    }

    @After
    fun tearDown() {
        // BoxStore is singleton, don't close it
    }

    @Test
    fun testGenerateDump_producesValidSchema() =
        runBlocking {
            // When: Generate schema dump
            val dump = ObjectBoxIntrospectionDump.generateDump(boxStore)

            // Then: Dump should contain expected data
            assertNotNull("Dump should not be null", dump)
            assertTrue("Timestamp should not be empty", dump.timestamp.isNotEmpty())
            assertTrue("Should have entities", dump.entityCount > 0)
            assertTrue("Entities list should match count", dump.entities.size == dump.entityCount)
        }

    @Test
    fun testGenerateDump_containsExpectedEntities() =
        runBlocking {
            // When: Generate schema dump
            val dump = ObjectBoxIntrospectionDump.generateDump(boxStore)

            // Then: Should contain known entities
            val entityNames = dump.entities.map { it.name }
            assertTrue("Should contain NX_Work", entityNames.contains("NX_Work"))
            assertTrue("Should contain NX_WorkSourceRef", entityNames.contains("NX_WorkSourceRef"))
            assertTrue("Should contain NX_WorkVariant", entityNames.contains("NX_WorkVariant"))
            assertTrue("Should contain NX_EpgEntry", entityNames.contains("NX_EpgEntry"))
            assertTrue("Should contain ObxCanonicalMedia", entityNames.contains("ObxCanonicalMedia"))
        }

    @Test
    fun testGenerateDump_extractsProperties() =
        runBlocking {
            // When: Generate schema dump
            val dump = ObjectBoxIntrospectionDump.generateDump(boxStore)

            // Then: Entities should have properties
            val workEntity = dump.entities.find { it.name == "NX_Work" }
            assertNotNull("NX_Work entity should exist", workEntity)

            workEntity?.let {
                assertTrue("NX_Work should have properties", it.properties.isNotEmpty())

                // Should have id property
                val idProp = it.properties.find { prop -> prop.name == "id" }
                assertNotNull("Should have id property", idProp)
                assertTrue("id should be marked as Id", idProp?.isId == true)

                // Should have canonicalKey property
                val keyProp = it.properties.find { prop -> prop.name == "canonicalKey" }
                assertNotNull("Should have canonicalKey property", keyProp)
                assertTrue("canonicalKey should be indexed", keyProp?.isIndexed == true)
            }
        }

    @Test
    fun testDumpToFile_createsValidFile() =
        runBlocking {
            // When: Generate dump and write to file
            val dump = ObjectBoxIntrospectionDump.generateDump(boxStore)
            val file = ObjectBoxIntrospectionDump.dumpToFile(context, dump)

            // Then: File should exist and contain JSON
            assertTrue("File should exist", file.exists())
            assertTrue("File should not be empty", file.length() > 0)
            assertTrue("File name should match pattern", file.name.startsWith("obx_dump_"))
            assertTrue("File should be JSON", file.name.endsWith(".json"))

            // Verify JSON is parseable
            val content = file.readText()
            assertTrue("Content should contain timestamp", content.contains("timestamp"))
            assertTrue("Content should contain entities", content.contains("entities"))

            // Cleanup
            file.delete()
        }

    @Test
    fun testDumpToLogcat_doesNotCrash() =
        runBlocking {
            // When: Generate dump and write to logcat
            val dump = ObjectBoxIntrospectionDump.generateDump(boxStore)

            // Then: Should not throw exception
            ObjectBoxIntrospectionDump.dumpToLogcat(dump)

            // No exception = success
        }

    @Test
    fun testEntityDump_containsDisplayName() =
        runBlocking {
            // When: Generate schema dump
            val dump = ObjectBoxIntrospectionDump.generateDump(boxStore)

            // Then: All entities should have display names
            dump.entities.forEach { entity ->
                assertTrue(
                    "Entity ${entity.name} should have display name",
                    entity.displayName.isNotEmpty(),
                )
            }
        }

    @Test
    fun testPropertyDump_containsAnnotations() =
        runBlocking {
            // When: Generate schema dump
            val dump = ObjectBoxIntrospectionDump.generateDump(boxStore)

            // Then: Properties with annotations should list them
            val workEntity = dump.entities.find { it.name == "NX_Work" }
            workEntity?.let {
                val indexedProps = it.properties.filter { prop -> prop.isIndexed }
                indexedProps.forEach { prop ->
                    assertTrue(
                        "Indexed property ${prop.name} should have Index annotation",
                        prop.annotations.contains("Index"),
                    )
                }
            }
        }
}
