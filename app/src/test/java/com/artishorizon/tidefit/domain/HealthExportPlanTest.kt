package com.artishorizon.tidefit.domain

import com.artishorizon.tidefit.domain.calc.HealthExportPlan
import org.junit.Assert.assertEquals
import org.junit.Test


class HealthExportPlanTest {
    @Test fun firstSyncUpsertsEverything() {
        val p = HealthExportPlan.diff(mapOf("a" to "1", "b" to "2"), emptyMap())
        assertEquals(setOf("a", "b"), p.upsert); assertEquals(emptySet<String>(), p.delete)
    }

    @Test fun unchangedItemsAreSkipped() {
        val p = HealthExportPlan.diff(mapOf("a" to "1"), mapOf("a" to "1"))
        assertEquals(emptySet<String>(), p.upsert); assertEquals(emptySet<String>(), p.delete)
    }

    @Test fun changedItemsAreRewrittenAndMissingOnesDeleted() {
        val p = HealthExportPlan.diff(mapOf("a" to "2", "c" to "1"), mapOf("a" to "1", "b" to "1"))
        assertEquals(setOf("a", "c"), p.upsert); assertEquals(setOf("b"), p.delete)
    }
}
