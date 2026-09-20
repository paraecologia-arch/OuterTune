package com.dd3boh.outertune.viewmodels

import com.zionhuang.innertube.pages.HomePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestRequestGuardTest {
    @Test
    fun filteredResponseReplacesOldSectionsAndPreservesBaseChips() {
        val chip = HomePage.Chip("A", endpoint = null, deselectEndPoint = null)
        val oldSection = HomePage.Section("old", null, null, null, emptyList())
        val newSection = HomePage.Section("new", null, null, null, emptyList())
        val base = HomePage(chips = listOf(chip), sections = listOf(oldSection))
        val clearedCurrent = base.copy(sections = emptyList(), continuation = null)
        val response = HomePage(chips = null, sections = listOf(newSection), continuation = "next")

        val result = filteredHomePage(clearedCurrent, base, response)

        assertEquals(listOf(newSection), result.sections)
        assertEquals(listOf(chip), result.chips)
        assertEquals("next", result.continuation)
    }

    @Test
    fun latestSelectionOwnsLoadingAndOlderResultCannotPublish() {
        val tracker = ChipRequestTracker<String>()
        val first = tracker.select("A")
        val second = tracker.select("B")

        assertFalse(tracker.succeeded(first, isEmpty = false))
        assertTrue(tracker.state.isLoading)
        assertTrue(tracker.succeeded(second, isEmpty = false))
        assertFalse(tracker.state.isLoading)
        assertTrue(tracker.state.selected == "B")
    }

    @Test
    fun emptyResultClearsLoadingWithoutLosingSelection() {
        val tracker = ChipRequestTracker<String>()
        val request = tracker.select("Podcasts")

        assertTrue(tracker.succeeded(request, isEmpty = true))

        assertTrue(tracker.state.isEmpty)
        assertFalse(tracker.state.isLoading)
        assertTrue(tracker.state.selected == "Podcasts")
    }

    @Test
    fun failureClearsLoadingAndKeepsSelectionRecoverable() {
        val tracker = ChipRequestTracker<String>()
        val request = tracker.select("Foco")

        assertTrue(tracker.failed(request))

        assertTrue(tracker.state.hasError)
        assertFalse(tracker.state.isLoading)
        assertTrue(tracker.state.selected == "Foco")
    }

    @Test
    fun deselectionRestoresNeutralStateAndInvalidatesRequestInFlight() {
        val tracker = ChipRequestTracker<String>()
        val request = tracker.select("A")

        tracker.deselect()

        assertFalse(tracker.succeeded(request, isEmpty = false))
        assertTrue(tracker.state == ChipRequestState<String>())
    }
}
