package com.dyetracker.client

import com.dyetracker.DyeTrackerMod
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier

/**
 * The `dyetracker:main` key category every DyeTracker keybind shares, so vanilla's Controls
 * screen groups them under "DyeTracker". Registered once, on first use: registering the same
 * category twice must not happen.
 */
object DyeTrackerKeys {

    private const val CATEGORY_PATH = "main"

    val CATEGORY: KeyMapping.Category by lazy {
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(DyeTrackerMod.MOD_ID, CATEGORY_PATH))
    }
}
