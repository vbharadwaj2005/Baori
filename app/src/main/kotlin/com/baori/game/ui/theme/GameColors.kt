package com.baori.game.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens (design doc §2 — every color has a *role*, never a
 * raw hex at the usage site; design doc §6 "No Hardcoded Hues").
 *
 * The palette itself comes from the PRD's world (§5.1): sun-baked sandstone,
 * terracotta walls, marigold, and the teal-green of remembered water. Two
 * schemes ship on Day 1:
 *
 *  - **Sun-baked** (light): a village at noon, drought in the air.
 *  - **The well remembers** (dark): night at the well, moonlit stone and
 *    deep water — where the story is headed.
 *
 * Game-specific tokens (path/water/goal) are semantic too: they describe
 * *meaning in the puzzle*, and both schemes define them so colorblind
 * support (PRD §9 — shape + color, never color alone) can hook in later
 * without touching rendering code.
 */
@Immutable
data class GameColors(
    // Surfaces and contexts (design doc §2)
    val background: Color,
    val onBackground: Color,
    val card: Color,
    val onCard: Color,
    val popover: Color,
    val onPopover: Color,

    // Interaction and emphasis
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val muted: Color,
    val onMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val destructive: Color,
    val onDestructive: Color,

    // Structural boundaries
    val border: Color,
    val input: Color,
    val ring: Color,

    // Baori world tokens (PRD §5/§6 vocabulary, role-named)
    /** A walkable, currently-aligned path — sunlit stone. */
    val pathLit: Color,
    /** A path that exists in the level but is not aligned — dormant stone. */
    val pathDormant: Color,
    /** Geometry that is part of the well but never walkable — deep walls. */
    val pathStructure: Color,
    /** The remembered water plane (levels 4/5). */
    val water: Color,
    /** The goal node marker — marigold, the color of offering flowers. */
    val goal: Color,
    /** The girl's sari — the one warm constant in every scene. */
    val protagonist: Color,
    /** Mural illustration base pigment. */
    val mural: Color,
    /** Mural line work. */
    val muralInk: Color,

    // Radius (design doc §2 — centralized corner-curvature scaling)
    val radius: RadiusTokens,
) {
    @Immutable
    data class RadiusTokens(
        val small: androidx.compose.ui.unit.Dp,
        val medium: androidx.compose.ui.unit.Dp,
        val large: androidx.compose.ui.unit.Dp,
    )
}

/** Day palette — "Sun-baked". */
val SunBakedColors: GameColors = GameColors(
    background = Color(0xFFF4EBDC),      // sandstone in noon light
    onBackground = Color(0xFF33261C),    // burnt umber ink
    card = Color(0xFFFCF6EB),            // lime-washed plaster
    onCard = Color(0xFF33261C),
    popover = Color(0xFFFCF6EB),
    onPopover = Color(0xFF33261C),

    primary = Color(0xFFB4532A),         // terracotta
    onPrimary = Color(0xFFFDF8EF),
    secondary = Color(0xFF8A6A45),       // weathered wood
    onSecondary = Color(0xFFFDF8EF),
    muted = Color(0xFFE9DCC6),           // pale dust
    onMuted = Color(0xFF5C4A38),
    accent = Color(0xFFD98E32),          // marigold
    onAccent = Color(0xFF33261C),
    destructive = Color(0xFF9C3B2E),     // fired clay red
    onDestructive = Color(0xFFFDF8EF),

    border = Color(0xFFD9C6A5),
    input = Color(0xFFEFE3CE),
    ring = Color(0xFFB4532A),

    pathLit = Color(0xFFC97B3D),         // sunlit step edge
    pathDormant = Color(0xFFA98F6C),     // stone in shadow
    pathStructure = Color(0xFF7C6244),   // deep well wall
    water = Color(0xFF2E7D74),           // remembered water, shallow
    goal = Color(0xFFD98E32),            // marigold offering
    protagonist = Color(0xFFB03A5B),     // the girl's sari
    mural = Color(0xFFE7CFA3),           // mural plaster ground
    muralInk = Color(0xFF4A3826),        // mural pigment line work

    radius = GameColors.RadiusTokens(
        small = GameSpacing.Dp4,
        medium = GameSpacing.Dp8,
        large = GameSpacing.Dp16,
    ),
)

/** Night palette — "the well remembers". */
val WellRemembersColors: GameColors = GameColors(
    background = Color(0xFF12181F),      // night sky over the village
    onBackground = Color(0xFFE8DDCB),    // moonlit sand
    card = Color(0xFF1B232D),            // stone at night
    onCard = Color(0xFFE8DDCB),
    popover = Color(0xFF1B232D),
    onPopover = Color(0xFFE8DDCB),

    primary = Color(0xFFE0A458),         // lamplight marigold
    onPrimary = Color(0xFF1B232D),
    secondary = Color(0xFF8FA3AD),       // wet stone
    onSecondary = Color(0xFF12181F),
    muted = Color(0xFF232E39),           // shaded alcove
    onMuted = Color(0xFFB9AC97),
    accent = Color(0xFF63B3A4),          // water catching moonlight
    onAccent = Color(0xFF0E141A),
    destructive = Color(0xFFC05B4A),     // dying embers
    onDestructive = Color(0xFFF4EBDC),

    border = Color(0xFF33414E),
    input = Color(0xFF232E39),
    ring = Color(0xFF63B3A4),

    pathLit = Color(0xFF63B3A4),         // aligned paths glow like water
    pathDormant = Color(0xFF4A5A66),     // unaligned stone
    pathStructure = Color(0xFF2C3945),   // the deep
    water = Color(0xFF2E6E74),
    goal = Color(0xFFE0A458),
    protagonist = Color(0xFFD4688A),     // sari under moonlight
    mural = Color(0xFF22303B),           // night mural ground
    muralInk = Color(0xFFE8DDCB),

    radius = GameColors.RadiusTokens(
        small = GameSpacing.Dp4,
        medium = GameSpacing.Dp8,
        large = GameSpacing.Dp16,
    ),
)

/** Internal spacing values shared by token definitions (kept tiny on purpose). */
private object GameSpacing {
    val Dp4 = androidx.compose.ui.unit.Dp(4f)
    val Dp8 = androidx.compose.ui.unit.Dp(8f)
    val Dp16 = androidx.compose.ui.unit.Dp(16f)
}
