package com.dyetracker.ui.theme

/**
 * Central style tokens for the in-game UI toolkit. Components and the edit-mode screen
 * reference these named tokens instead of inlining raw ARGB / pixel literals, so the
 * toolkit shares one coherent look and values live in a single place (project
 * named-constants + DRY rules).
 *
 * Values are seeded from the GIF overlay edit screen's original constants so adopting
 * these tokens preserves the existing appearance. Tokens are intentionally distinct even
 * when two currently share a value (e.g. several whites) so each can be re-themed
 * independently later.
 */
object UiTheme {

    /** ARGB color tokens (0xAARRGGBB). */
    object Colors {
        /** Fully transparent fill (no paint); used to disable a panel's background. */
        const val TRANSPARENT: Int = 0x00_00_00_00

        /** Dim wash painted behind edit-mode content. */
        const val BACKDROP: Int = 0x80_00_00_00.toInt()

        /** Default panel/background fill. Follows the active theme (PBI 81). */
        val PANEL_BACKGROUND: Int get() = ThemeState.active.panelBackground

        /** Default 1px panel border. Follows the active theme (PBI 81). */
        val PANEL_BORDER: Int get() = ThemeState.active.panelBorder

        /** Border drawn around the focused widget in edit mode. Follows the active theme (PBI 81). */
        val FOCUS_BORDER: Int get() = ThemeState.active.focusBorder

        /** Primary (high-emphasis) text. Follows the active theme (PBI 81). */
        val TEXT_PRIMARY: Int get() = ThemeState.active.textPrimary

        /** Secondary (low-emphasis) text. Follows the active theme (PBI 81). */
        val TEXT_SECONDARY: Int get() = ThemeState.active.textSecondary

        /** Success/neutral status text. Follows the active theme (PBI 81). */
        val STATUS_OK: Int get() = ThemeState.active.statusOk

        /** Error status text. */
        const val STATUS_ERROR: Int = 0xFF_FF_55_55.toInt()

        /** Dye-rotation 2x boost badge (green), seeded from the external dyes browser source. */
        const val BOOST_2X: Int = 0xFF_43_B5_81.toInt()

        /** Dye-rotation 3x boost badge (gold), seeded from the external dyes browser source. */
        const val BOOST_3X: Int = 0xFF_FA_A6_1A.toInt()

        /** Translucent wash over a hidden (visibility-off) widget. */
        const val HIDDEN_DIM: Int = 0xB0_00_00_00.toInt()

        /** Strikethrough bar drawn across a hidden widget. */
        const val STRIKETHROUGH: Int = 0xCC_FF_55_55.toInt()

        /** Progress-bar track (unfilled portion); a subtle translucent white, seeded from the website overlay's `white/5` track. */
        const val PROGRESS_TRACK: Int = 0x33_FF_FF_FF.toInt()

        /** Default progress-bar fill; seeded from the website single-dye overlay's default accent (`#e225f4`). Callers may override per dye color. Follows the active theme (PBI 81). */
        val PROGRESS_FILL: Int get() = ThemeState.active.progressFill

        /** Faint border around an icon box, mirroring the website overlay icon's `border-white/10`. */
        const val ICON_BOX_BORDER: Int = 0x1A_FF_FF_FF.toInt()

        /** Neutral (no-accent) icon-box tint, used when no dye accent color is available. */
        const val ICON_BOX_TINT: Int = 0x14_FF_FF_FF.toInt()

        /** In-game menu backdrop; the website's `--color-background-dark` (`#000000`), slightly see-through. Follows the active theme (PBI 81). */
        val MENU_BACKGROUND: Int get() = ThemeState.active.menuBackground

        /** In-game menu header/surface fill; the website's `--color-surface-dark` (`#0a0a0a`). Follows the active theme (PBI 81). */
        val MENU_SURFACE: Int get() = ThemeState.active.menuSurface

        /** In-game menu card fill; the website's `--color-card-dark` (`#111111`). Follows the active theme (PBI 81). */
        val CARD_BACKGROUND: Int get() = ThemeState.active.cardBackground

        /** In-game menu card border; the website Card's `border-white/10`. Follows the active theme (PBI 81). */
        val CARD_BORDER: Int get() = ThemeState.active.cardBorder

        /** Primary accent (title, active tab); the website's `--color-primary` (`#e225f4`). Follows the active theme (PBI 81). */
        val ACCENT_PRIMARY: Int get() = ThemeState.active.accentPrimary

        /** Secondary accent; the website's `--color-secondary` (`#00f0ff`). Follows the active theme (PBI 81). */
        val ACCENT_SECONDARY: Int get() = ThemeState.active.accentSecondary

        /** Subtle text (percentages, status messages); the website's `slate-400` (`#94a3b8`). Follows the active theme (PBI 81). */
        val TEXT_SUBTLE: Int get() = ThemeState.active.textSubtle

        /** Muted text; the website's `slate-500` (`#64748b`). Follows the active theme (PBI 81). */
        val TEXT_MUTED: Int get() = ThemeState.active.textMuted

        /** Menu card `MOD` (verified) badge text; the website Badge `verified` variant's `text-primary` (`#e225f4`). Follows the active theme (PBI 81). */
        val BADGE_MOD_TEXT: Int get() = ThemeState.active.badgeModText

        /** Menu card `MOD` badge fill; the website Badge `verified` variant's `bg-primary/10`. Follows the active theme (PBI 81). */
        val BADGE_MOD_BG: Int get() = ThemeState.active.badgeModBg

        /** Menu card `MOD` badge border; the website Badge `verified` variant's `border-primary/30`. Follows the active theme (PBI 81). */
        val BADGE_MOD_BORDER: Int get() = ThemeState.active.badgeModBorder

        /** Menu card `ESTIMATED` badge text; the website Badge `estimated` variant's `text-amber-400` (`#fbbf24`). */
        const val BADGE_ESTIMATED_TEXT: Int = 0xFF_FB_BF_24.toInt()

        /** Menu card `ESTIMATED` badge fill; the website Badge `estimated` variant's `bg-amber-500/10` (`#f59e0b`). */
        const val BADGE_ESTIMATED_BG: Int = 0x1A_F5_9E_0B

        /** Menu card `ESTIMATED` badge border; the website Badge `estimated` variant's `border-amber-500/20` (`#f59e0b`). */
        const val BADGE_ESTIMATED_BORDER: Int = 0x33_F5_9E_0B

        /** Menu card `MOD ONLY` / `NOT TRACKABLE` badge text; the website Badge `muted` variant's `text-slate-500` (`#64748b`). Follows the active theme (PBI 81). */
        val BADGE_MUTED_TEXT: Int get() = ThemeState.active.badgeMutedText

        /** Menu card muted badge fill; the website Badge `muted` variant's `bg-white/5`. */
        const val BADGE_MUTED_BG: Int = 0x0D_FF_FF_FF

        /** Menu card muted badge border; the website Badge `muted` variant's `border-white/5`. */
        const val BADGE_MUTED_BORDER: Int = 0x0D_FF_FF_FF

        /** Menu card icon box fill; the website DyeCard icon box's `bg-black` (`#000000`). */
        const val ICON_BOX_BACKGROUND: Int = 0xFF_00_00_00.toInt()

        /** Selected menu pill fill and border; the website primary Button's `bg-white`. Follows the active theme (PBI 81). */
        val PILL_SELECTED_BG: Int get() = ThemeState.active.pillSelectedBg

        /** Selected menu pill label; the website primary Button's `text-black`. */
        const val PILL_SELECTED_TEXT: Int = 0xFF_00_00_00.toInt()

        /** Hovered unselected menu pill border; the website secondary Button's `hover:border-white/30`. */
        const val PILL_HOVER_BORDER: Int = 0x4D_FF_FF_FF

        /** Menu toggle pill fill when on; the website Badge `success` variant's `bg-secondary/10`. Follows the active theme (PBI 81). */
        val TOGGLE_ON_BG: Int get() = ThemeState.active.toggleOnBg

        /** Menu toggle pill border when on; the website Badge `success` variant's `border-secondary/20`. Follows the active theme (PBI 81). */
        val TOGGLE_ON_BORDER: Int get() = ThemeState.active.toggleOnBorder

        /** Menu Collection tab's summary bar fill; the website Collection page's `bg-emerald-500` (`#10b981`). */
        const val COLLECTION_ACCENT: Int = 0xFF_10_B9_81.toInt()

        /** Menu Collection owned-card badge text; the website's `text-emerald-400` (`#34d399`). */
        const val OWNED_TEXT: Int = 0xFF_34_D3_99.toInt()

        /** Menu Collection owned-card badge fill; the website's `bg-emerald-500/10` (`#10b981`). */
        const val OWNED_BG: Int = 0x1A_10_B9_81

        /** Menu Collection owned-card border and badge border; the website's `border-emerald-500/30` (`#10b981`). */
        const val OWNED_BORDER: Int = 0x4D_10_B9_81

        /** Wash over a not-owned Collection card; stands in for the website's `opacity-50` + `grayscale`. */
        const val UNOWNED_DIM: Int = 0x99_00_00_00.toInt()

        /** Menu achievement `bronze` tier; the website `AchievementTierBadge`'s `#cd7f32`. */
        const val TIER_BRONZE: Int = 0xFF_CD_7F_32.toInt()

        /** Menu achievement `silver` tier; the website `AchievementTierBadge`'s `#c0c0c0`. */
        const val TIER_SILVER: Int = 0xFF_C0_C0_C0.toInt()

        /** Menu achievement `gold` tier; the website `AchievementTierBadge`'s `#ffd700`. */
        const val TIER_GOLD: Int = 0xFF_FF_D7_00.toInt()

        /** Menu achievement `platinum` tier; the website `AchievementTierBadge`'s `#00f0ff`. */
        const val TIER_PLATINUM: Int = 0xFF_00_F0_FF.toInt()

        /** Menu achievement `legendary` tier; the website `AchievementTierBadge`'s `#e225f4`. */
        const val TIER_LEGENDARY: Int = 0xFF_E2_25_F4.toInt()

        /** A locked achievement's tier pill; the website `AchievementTierBadge`'s muted `#4b5563`. */
        const val TIER_LOCKED: Int = 0xFF_4B_55_63.toInt()

        /** Spotify now-playing panel; Spotify's fallback background (`#191414`) at the panel alpha. */
        const val SPOTIFY_FALLBACK_BACKGROUND: Int = 0xC8_19_14_14.toInt()

        /** Spotify's brand green (`#1ED760`), the now-playing progress fill. */
        const val SPOTIFY_GREEN: Int = 0xFF_1E_D7_60.toInt()
    }

    /** Spacing tokens in GUI pixels. */
    object Spacing {
        /** Standard inner padding for panels/controls. */
        const val PADDING: Int = 8

        /** Gap between stacked inner elements. */
        const val INNER_GAP: Int = 6

        /** Outer margin from screen edges. */
        const val MARGIN: Int = 6
    }

    /** Sizing tokens. */
    object Sizing {
        /** Default border thickness in GUI pixels. */
        const val BORDER_WIDTH: Int = 1

        /** Default font scale multiplier (1.0 = vanilla size). */
        const val FONT_SCALE: Float = 1.0f

        /** Standard interactive control (button/field) height in GUI pixels. */
        const val CONTROL_HEIGHT: Int = 20

        /** Default progress-bar track/fill height in GUI pixels (pre-scale). */
        const val PROGRESS_BAR_HEIGHT: Int = 4
    }
}
