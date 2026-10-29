package com.dyetracker.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JSON-decoding coverage for the achievements endpoint's response (task 68-6), both the live shape
 * without the catalog fields and the shape with them. Mirrors the `ApiClient` Json config
 * (`ignoreUnknownKeys = true`), so the response's `profile` is dropped.
 */
class AchievementsResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes the live shape without the catalog fields`() {
        val body = """
            {"uuid":"e92…","username":"_Writhe","profile":{"id":"p1","cute_name":"Mango"},
             "achievements":[{"achievementId":"drop_matcha","unlocked":false,"unlockedAt":null,"progress":null}],
             "unlockedCount":0,"totalCount":50}
        """.trimIndent()

        val result = json.decodeFromString<AchievementsResponse>(body)

        assertEquals("e92…", result.uuid)
        assertEquals("_Writhe", result.username)
        assertEquals(0, result.unlockedCount)
        assertEquals(50, result.totalCount)
        val entry = result.achievements.single()
        assertEquals("drop_matcha", entry.achievementId)
        assertNull(entry.name)
        assertNull(entry.description)
        assertNull(entry.category)
        assertNull(entry.tier)
        assertFalse(entry.secret)
        assertFalse(entry.unlocked)
        assertNull(entry.unlockedAt)
        assertNull(entry.progress)
    }

    @Test
    fun `decodes the catalog fields and an unlock time`() {
        val body = """
            {"achievements":[{"achievementId":"drop_matcha","name":"Matcha Drop","description":"Drop a Matcha Dye",
             "category":"dye-drop","tier":"gold","secret":false,"unlocked":true,"unlockedAt":1761000000000,"progress":null}],
             "unlockedCount":1,"totalCount":1}
        """.trimIndent()

        val entry = json.decodeFromString<AchievementsResponse>(body).achievements.single()

        assertEquals(
            PlayerAchievementEntry(
                achievementId = "drop_matcha",
                name = "Matcha Drop",
                description = "Drop a Matcha Dye",
                category = "dye-drop",
                tier = "gold",
                secret = false,
                unlocked = true,
                unlockedAt = 1761000000000L,
                progress = null,
            ),
            entry,
        )
    }

    @Test
    fun `decodes a secret entry with progress`() {
        val body = """
            {"achievements":[{"achievementId":"count_five","secret":true,"unlocked":false,"progress":{"current":3,"total":5}}]}
        """.trimIndent()

        val entry = json.decodeFromString<AchievementsResponse>(body).achievements.single()

        assertTrue(entry.secret)
        assertEquals(AchievementProgress(current = 3, total = 5), entry.progress)
    }

    @Test
    fun `an empty body takes the defaults`() {
        val result = json.decodeFromString<AchievementsResponse>("{}")

        assertEquals(AchievementsResponse(), result)
        assertTrue(result.achievements.isEmpty())
    }
}
