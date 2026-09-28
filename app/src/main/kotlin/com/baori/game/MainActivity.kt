package com.baori.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.baori.game.ui.screens.GameScreen
import com.baori.game.ui.screens.MainMenuScreen
import com.baori.game.ui.screens.MuralId
import com.baori.game.ui.screens.MuralScreen
import com.baori.game.ui.screens.PaywallScreen
import com.baori.game.ui.theme.BaoriTheme
import com.baori.game.viewmodel.GameViewModel
import com.baori.game.viewmodel.MenuViewModel
import com.baori.game.viewmodel.PaywallViewModel
import com.baori.game.viewmodel.SettingsViewModel

/**
 * The only Activity. Everything above it is Compose (PRD §7.1/§7.2), hosted
 * in one [BaoriRoot] that owns the tiny screen backstack — four screens do
 * not justify a navigation library.
 *
 * Screen flow (PRD §6/§8.2): Menu → Game → mural → next level, with the
 * paywall standing between Level 3 and the paid half of the journey.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BaoriApplication
        setContent {
            val root: RootViewModel = viewModel(
                factory = viewModelFactory { initializer { RootViewModel(app) } },
            )
            // Collect (not `.value`) so every state change recomposes.
            val settings by root.settings.uiState.collectAsStateWithLifecycle()
            BaoriTheme(
                themeOverride = settings.themeOverride,
                reduceMotion = settings.reduceMotion,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BaoriRoot(root)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Root state: one factory, four ViewModels, a hand-rolled backstack.
// ---------------------------------------------------------------------------

/**
 * Holds the shared ViewModels for the whole app. One object instead of
 * per-screen `viewModel()` calls so the paywall can react to game state and
 * both can read settings without prop-drilling.
 */
class RootViewModel(app: BaoriApplication) : ViewModel() {

    val settings = SettingsViewModel(app.preferences)
    val menu = MenuViewModel(app.levelRepository, app.preferences, app.billingService)
    val game = GameViewModel(app.levelRepository, app.preferences, app.billingService)
    val paywall = PaywallViewModel(app.billingService)
}

/** The four destinations. Sealed so `when` stays exhaustive. */
sealed interface Screen {
    data object Menu : Screen
    data class Game(val levelNumber: Int) : Screen
    data class Mural(val mural: MuralId, val nextLevelNumber: Int) : Screen
    data object Paywall : Screen
}

@Composable
private fun BaoriRoot(root: RootViewModel) {
    var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Menu)) }
    val screen = backStack.last()

    // Collected states — every screen renders from subscriptions, not reads.
    val settings by root.settings.uiState.collectAsStateWithLifecycle()
    val menuState by root.menu.uiState.collectAsStateWithLifecycle()
    val gameState by root.game.uiState.collectAsStateWithLifecycle()
    val hapticPulse by root.game.hapticPulse.collectAsStateWithLifecycle()
    val paywallState by root.paywall.uiState.collectAsStateWithLifecycle()

    fun push(next: Screen) {
        backStack = backStack + next
    }

    fun pop() {
        backStack = if (backStack.size > 1) backStack.dropLast(1) else backStack
    }

    // System back mirrors the in-app back affordance everywhere.
    BackHandler(enabled = backStack.size > 1) { pop() }

    when (screen) {
        is Screen.Menu -> MainMenuScreen(
            menuState = menuState,
            reduceMotion = settings.reduceMotion,
            muted = settings.muted,
            onPlayLevel = { order -> push(Screen.Game(order)) },
            onToggleMute = { root.settings.setMuted(!settings.muted) },
            onToggleReduceMotion = { root.settings.setReduceMotion(!settings.reduceMotion) },
        )

        is Screen.Game -> GameScreen(
            state = gameState,
            hapticPulse = hapticPulse,
            reduceMotion = settings.reduceMotion,
            onDrag = { delta -> root.game.dragBy(delta) },
            onDragEnd = { root.game.endDrag(settings.reduceMotion) },
            onWalk = { root.game.tryWalk() },
            onBack = {
                root.game.consumeCompletion()
                pop()
            },
            onContinue = {
                val level = gameState.level
                if (level != null) {
                    val maxOrder = menuState.items.maxOfOrNull { it.level.order } ?: level.order
                    val nextOrder = level.order + 1
                    // PRD §6: the finale — the last level's mural closes the
                    // story and returns to the menu; there is no Level 6 to
                    // silently replay.
                    val isFinale = level.order >= maxOrder
                    val nextItem = menuState.items
                        .firstOrNull { it.level.order == nextOrder }
                    // PRD §8.1: the paywall stands at the end of Level 3 —
                    // and anywhere else paid content is not yet unlocked.
                    // Dismissing it always returns to a playable screen.
                    val locked = nextItem?.needsPurchase == true && !paywallState.isUnlocked
                    when {
                        isFinale && level.muralAfter == null -> {
                            root.game.consumeCompletion()
                            backStack = listOf(Screen.Menu)
                        }
                        isFinale -> push(
                            Screen.Mural(
                                mural = muralIdFrom(level.muralAfter ?: "")
                                    ?: MuralId.WATER_RETURNS,
                                nextLevelNumber = 0, // 0 = story complete → menu
                            ),
                        )
                        locked -> {
                            root.game.consumeCompletion()
                            push(Screen.Paywall)
                        }
                        // Murals carry the story between levels (PRD §6.1).
                        level.muralAfter != null -> push(
                            Screen.Mural(
                                mural = muralIdFrom(level.muralAfter) ?: MuralId.OUTER_RING,
                                nextLevelNumber = nextOrder,
                            ),
                        )
                        else -> {
                            root.game.consumeCompletion()
                            root.game.nextLevel()
                        }
                    }
                }
            },
        )

        is Screen.Mural -> MuralScreen(
            mural = screen.mural,
            onContinue = {
                if (screen.nextLevelNumber <= 0) {
                    // Finale mural: the story is told — home.
                    root.game.consumeCompletion()
                    backStack = listOf(Screen.Menu)
                } else {
                    push(Screen.Game(levelNumber = screen.nextLevelNumber))
                }
            },
        )

        is Screen.Paywall -> PaywallScreen(
            state = paywallState,
            onPurchase = { root.paywall.purchase() },
            onRestore = { root.paywall.restore() },
            onDismiss = { pop() },
        )
    }

    // Entering a Game screen loads that level exactly once.
    if (screen is Screen.Game) {
        LaunchedEffect(screen.levelNumber) {
            root.game.loadLevel(screen.levelNumber)
        }
    }

    // PRD §8.2 step 4: purchase → unlock → proceed. The entitlement flips
    // reactively (step 6), so the paywall hands straight over to the story
    // beat that follows — no restart, no dead end.
    LaunchedEffect(paywallState.isUnlocked) {
        if (paywallState.isUnlocked && screen is Screen.Paywall) {
            val level = gameState.level
            val next = if (level != null) {
                Screen.Mural(
                    mural = muralIdFrom(level.muralAfter ?: "") ?: MuralId.OUTER_RING,
                    nextLevelNumber = level.order + 1,
                )
            } else {
                Screen.Menu
            }
            backStack = backStack.dropLast(1) + next
        }
    }
}

/** Maps level JSON `muralAfter` ids to the drawn murals (PRD §6.1). */
private fun muralIdFrom(id: String): MuralId? = when (id) {
    "mural_1" -> MuralId.GIRL_LEAVES_VILLAGE
    "mural_2" -> MuralId.OUTER_RING
    "mural_3" -> MuralId.GRANDMOTHERS_MEMORY
    "mural_4" -> MuralId.THE_DROUGHT_BEGAN
    "mural_5" -> MuralId.WATER_RETURNS
    else -> null
}
