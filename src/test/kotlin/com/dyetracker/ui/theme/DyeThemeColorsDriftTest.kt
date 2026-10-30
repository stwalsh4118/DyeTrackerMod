package com.dyetracker.ui.theme

import com.dyetracker.dyeprogress.DyeCatalog
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps [DyeThemeColors.COLORS] in step with the website's dye colors (task 81-2): 42 entries, all
 * catalog dyes, and the same `id -> rgb` as `DYE_DEFINITIONS` in
 * `packages/shared/src/constants/dyes.ts`. The comparison is skipped where `packages/shared` is
 * absent (the public mod mirror).
 */
class DyeThemeColorsDriftTest {

    @Test
    fun `there are 42 dye colors`() {
        assertEquals(42, DyeThemeColors.COLORS.size)
    }

    @Test
    fun `every dye color is a catalog dye`() {
        val unknown = DyeThemeColors.COLORS.keys.filter { DyeCatalog.byId(it) == null }
        assertTrue(unknown.isEmpty(), "Not in DyeCatalog: $unknown")
    }

    @Test
    fun `the dye colors match DYE_DEFINITIONS in dyes ts`() {
        val dyesTs = findDyesTs()
        assumeTrue(dyesTs != null, "packages/shared not present (public mod mirror)")
        val text = dyesTs!!.readText()
        val definitions = text.substring(text.indexOf(DEFINITIONS_START), text.indexOf(DEFINITIONS_END))
        val shared = DEFINITION_REGEX.findAll(definitions)
            .associate { it.groupValues[1] to it.groupValues[2].toInt(HEX_RADIX) }

        val differing = (shared.keys + DyeThemeColors.COLORS.keys)
            .filter { shared[it] != DyeThemeColors.COLORS[it] }
            .sorted()
        assertEquals(shared, DyeThemeColors.COLORS, "Dye colors differ from dyes.ts for: $differing")
    }

    /** `packages/shared/src/constants/dyes.ts` in the first directory up from the working dir that has it. */
    private fun findDyesTs(): File? =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, DYES_TS_PATH) }
            .firstOrNull { it.isFile }

    private companion object {
        const val DYES_TS_PATH = "packages/shared/src/constants/dyes.ts"
        const val DEFINITIONS_START = "DYE_DEFINITIONS"
        const val DEFINITIONS_END = "DYE_BY_ID"
        const val HEX_RADIX = 16
        val DEFINITION_REGEX = Regex(
            """\{\s*id:\s*'([a-z_]+)',.*?color:\s*'#([0-9A-Fa-f]{6})'""",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
