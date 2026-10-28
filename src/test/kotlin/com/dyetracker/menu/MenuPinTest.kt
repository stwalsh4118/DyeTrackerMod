package com.dyetracker.menu

import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.api.ProfileSummary
import com.dyetracker.dyeprogress.DyeProgressWidgetConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit coverage for [pinDecision]: what a click on a menu dye card does (task 67-6). */
class MenuPinTest {

    private val mango = ProfileSummary(id = "p-mango", cuteName = "Mango")
    private val banana = ProfileSummary(id = "p-banana", cuteName = "Banana")

    private val matcha = DyeProgressEntry(dyeId = "matcha", name = "Matcha Dye", color = "#7cb342", category = "slayer")
    private val lava = DyeProgressEntry(dyeId = "lava", name = "Lava Dye", color = "#ff5722", category = "mining")

    private val matchaOnMango = DyeProgressWidgetConfig(
        id = "w1",
        dyeId = "matcha",
        profileName = "Mango",
        profileId = "p-mango",
    )

    private val allSprites: (String) -> Boolean = { true }

    @Test
    fun `a dye without a sprite is not pinnable`() {
        val decision = pinDecision(emptyList(), matcha, mango) { it != "matcha" }

        assertEquals(PinDecision.NotPinnable, decision)
    }

    @Test
    fun `a dye with no sprite is not pinnable even when already pinned`() {
        val decision = pinDecision(listOf(matchaOnMango), matcha, mango) { false }

        assertEquals(PinDecision.NotPinnable, decision)
    }

    @Test
    fun `the same dye on the same profile is already pinned`() {
        val decision = pinDecision(listOf(matchaOnMango), matcha, mango, allSprites)

        assertEquals(PinDecision.AlreadyPinned, decision)
    }

    @Test
    fun `the same dye on another profile is added`() {
        val decision = pinDecision(listOf(matchaOnMango), matcha, banana, allSprites)

        assertEquals(PinDecision.Add("matcha", "p-banana", "Banana"), decision)
    }

    @Test
    fun `another dye is added with the profile's id and cute name`() {
        val decision = pinDecision(listOf(matchaOnMango), lava, mango, allSprites)

        assertEquals(PinDecision.Add(dyeId = "lava", profileId = "p-mango", profileName = "Mango"), decision)
    }

    @Test
    fun `nothing pinned yet adds the dye`() {
        val decision = pinDecision(emptyList(), matcha, mango, allSprites)

        assertEquals(PinDecision.Add("matcha", "p-mango", "Mango"), decision)
    }
}
