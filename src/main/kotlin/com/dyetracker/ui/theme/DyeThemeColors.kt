package com.dyetracker.ui.theme

/**
 * The color (`0xRRGGBB`) of each of the 42 catalog dyes, by dye id, for dye themes (PBI 81). Copied
 * from `DYE_DEFINITIONS` in `packages/shared/src/constants/dyes.ts` (retired Fire Sale dyes are not
 * in it, so they are not themes); `DyeThemeColorsDriftTest` fails when the two drift apart.
 */
object DyeThemeColors {

    /** Dye id to its RGB color. */
    val COLORS: Map<String, Int> = mapOf(
        "matcha" to 0x74A12E, "brick_red" to 0xCB4154, "celeste" to 0xB2FFFF, "byzantium" to 0x702963,
        "flame" to 0xE25822, "sangria" to 0xD40808, "livid" to 0xCEB7AA, "necron" to 0xE7413C,
        "jade" to 0x00A86B, "nadeshiko" to 0xF6ADC6, "tentacle" to 0x324D6C, "aquamarine" to 0x7FFFD4,
        "archfiend" to 0xB80036, "bone" to 0xE3DAC9, "carmine" to 0x960018, "celadon" to 0xACE1AF,
        "copper" to 0xB87333, "cyclamen" to 0xF56FA1, "dark_purple" to 0x301934, "dung" to 0x4F2A2A,
        "emerald" to 0x50C878, "fossil" to 0x866F12, "frostbitten" to 0x09D8EB, "holly" to 0x3C6746,
        "iceberg" to 0x71A6D2, "mango" to 0xFDBE02, "midnight" to 0x50216C, "mocha" to 0x967969,
        "mythological" to 0x6F6F0C, "nyanza" to 0xFB877F, "pearlescent" to 0x115555, "pelt" to 0x50414C,
        "periwinkle" to 0xCCCCFF, "secret" to 0x7D7D7D, "treasure" to 0xFCD12A, "wild_strawberry" to 0xFF43A4,
        "bingo_blue" to 0x002FA7, "chocolate" to 0x7B3F00, "pure_black" to 0x000000, "pure_white" to 0xFFFFFF,
        "pure_blue" to 0x0013FF, "pure_yellow" to 0xFFF700,
    )
}
