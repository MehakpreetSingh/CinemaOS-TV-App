package com.nuvio.tv.ui.screens.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsCatalogTest {
    @Test fun `CinemaOS keeps its account and downloads entries without upstream account screens`() {
        assertEquals(listOf(
            SettingsCategory.CINEMAOS_ACCOUNT,
            SettingsCategory.APPEARANCE,
            SettingsCategory.LAYOUT,
            SettingsCategory.CONTENT_DISCOVERY,
            SettingsCategory.PLAYBACK,
            SettingsCategory.DOWNLOADS,
            SettingsCategory.INTEGRATION,
            SettingsCategory.TRACKING,
            SettingsCategory.ADVANCED,
            SettingsCategory.DEBUG,
        ), visibleSettingsCategories(true, false, true))
    }
    @Test fun `downloads remain available in every experience and profile`() {
        allCombinations().forEach { categories ->
            assertTrue(SettingsCategory.DOWNLOADS in categories)
            assertEquals(SettingsCategory.CINEMAOS_ACCOUNT, categories.first())
            assertFalse(SettingsCategory.ACCOUNT in categories)
            assertFalse(SettingsCategory.PROFILES in categories)
            assertFalse(SettingsCategory.ABOUT in categories)
            assertFalse(SettingsCategory.WATCH_PARTY in categories)
            assertFalse(SettingsCategory.EXPERIENCE in categories)
        }
    }
    @Test fun `debug is hidden in release builds and essential mode`() {
        assertFalse(SettingsCategory.DEBUG in visibleSettingsCategories(true, false, false))
        assertFalse(SettingsCategory.DEBUG in visibleSettingsCategories(true, true, true))
        assertTrue(SettingsCategory.DEBUG in visibleSettingsCategories(true, false, true))
    }
    @Test fun `groups remain contiguous and dividers match their boundaries`() {
        allCombinations().forEach { categories ->
            val groups = categories.map { it.group }.fold(mutableListOf<SettingsRailGroup>()) { runs, group ->
                if (runs.lastOrNull() != group) runs.add(group)
                runs
            }
            assertEquals(groups.distinct(), groups)
            assertFalse(categories.startsNewGroup(0))
            assertEquals(listOf(SettingsCategory.APPEARANCE, SettingsCategory.CONTENT_DISCOVERY,
                SettingsCategory.INTEGRATION, SettingsCategory.ADVANCED),
                categories.indices.filter { categories.startsNewGroup(it) }.map { categories[it] })
        }
    }
    private fun allCombinations() = listOf(true, false).flatMap { primary ->
        listOf(true, false).flatMap { essential ->
            listOf(true, false).flatMap { debug ->
                listOf(true, false).map { party -> visibleSettingsCategories(primary, essential, debug, party) }
            }
        }
    }
}
