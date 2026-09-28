package com.baori.game.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baori.game.data.BaoriPreferences
import com.baori.game.data.LevelRepository
import com.baori.game.data.billing.BillingService
import com.baori.game.data.model.Level
import com.baori.game.engine.Easings
import com.baori.game.engine.PathAligner
import com.baori.game.engine.RotationController
import com.baori.game.engine.StepwellGeometry
import com.baori.game.engine.WaterPlane
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Drives one playthrough of a level (PRD §7.1: single ViewModel per screen,
 * StateFlow for UI state).
 *
 * Responsibilities:
 *  - own the [RotationController] (camera angle) and re-evaluate the
 *    [PathAligner] on every angle change,
 *  - map rotation to water height through the [WaterPlane] on levels 4/5,
 *    removing drowned segments from the walkable set (PRD §5.1 "the twist"),
 *  - drive the **chunk walk**: because no single angle aligns the whole route
 *    on levels 2–5, "Walk" advances the girl along the currently aligned
 *    graph as far as it makes progress toward the goal, and the player
 *    rotates again — PRD §6's mechanic progression, implemented in the walk
 *    contract rather than in per-level special cases,
 *  - animate snap and walk sequences with the house easing (PRD §9),
 *  - persist progress and surface the paywall trigger (end of Level 3) and
 *    the finale (end of Level 5).
 *
 * The ViewModel depends on interfaces only ([LevelRepository],
 * [BillingService]) so unit tests can supply fakes.
 */
class GameViewModel(
    private val levelRepository: LevelRepository,
    private val preferences: BaoriPreferences,
    private val billingService: BillingService,
) : ViewModel() {

    /** Everything the GameScreen renders, precomputed. No engine leaks. */
    data class GameUiState(
        val level: Level? = null,
        val angleDeg: Float = 0f,
        val isDragging: Boolean = false,
        val alignedSegmentIds: Set<String> = emptySet(),
        val walkableSegmentIds: Set<String> = emptySet(),
        val activeGoalDeg: Float? = null,
        val heroNode: String = "",
        /** Node the hero is currently stepping toward, if animating. */
        val heroTargetNode: String? = null,
        /** 0..1 progress of the current step animation. */
        val heroStepProgress: Float = 1f,
        /** True once a Walk press has committed and steps remain queued. */
        val isWalking: Boolean = false,
        val isComplete: Boolean = false,
        val showPaywallAfterComplete: Boolean = false,
        /** True when the LAST level has just been completed (PRD §6 finale). */
        val isFinale: Boolean = false,
        /**
         * Three-tier affordance, from most to least ready:
         *  - [REACHABLE]: the goal itself lies on the currently aligned
         *    graph — pressing Walk finishes the level;
         *  - [PROGRESS]: some reachable node moves the girl closer to the
         *    goal in the well's real geography — pressing Walk advances her;
         *  - [STUCK]: nothing reachable makes progress — the player must
         *    rotate; Walk is disabled.
         */
        val canAdvance: AdvanceTier = AdvanceTier.STUCK,
        /** 0..1 normalized water level, for the renderer's tint (levels 4/5). */
        val waterLevel: Float = 0f,
        /** Absolute surface height in well units, or null when there is no water. */
        val waterSurfaceY: Float? = null,
    ) {
        enum class AdvanceTier { REACHABLE, PROGRESS, STUCK }
    }

    private val pathAligner = PathAligner()

    private val rotationController = RotationController()

    init {
        prepare()
    }

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /** Haptic pulse request consumed by the screen (PRD §9). */
    private val _hapticPulse = MutableStateFlow(0)
    val hapticPulse: StateFlow<Int> = _hapticPulse.asStateFlow()

    private var levels: List<Level> = emptyList()
    private var currentIndex = 0
    private var walkJob: Job? = null
    private var prepareJob: Job? = null

    /** Starts (or restarts) a level by its 1-based order in the index. */
    fun loadLevel(levelNumber: Int) {
        val ordered = levels.sortedBy { it.order }
        if (ordered.isEmpty()) return // assets not ready yet; prepare() will retry
        walkJob?.cancel()
        currentIndex = (levelNumber - 1).coerceIn(0, ordered.size - 1)
        val level = ordered[currentIndex]

        rotationController.snapStepDegrees = level.snapStepDeg
        rotationController.setSnapTargets(level.goalAnglesDeg)
        rotationController.snapTo(level.startAngleDeg)

        _uiState.value = GameUiState(
            level = level,
            angleDeg = level.startAngleDeg,
            heroNode = level.startNode,
        )
        reevaluate(level.startAngleDeg, isDragging = false)
    }

    /**
     * Loads the index once from the repository; safe to call from init.
     * The second call (the player tapping a level) may arrive before the
     * first finishes, so the resume choice is skipped when a level was
     * already loaded explicitly.
     */
    fun prepare() {
        if (prepareJob?.isActive == true || levels.isNotEmpty()) return
        prepareJob = viewModelScope.launch {
            levels = levelRepository.loadAll()
            if (_uiState.value.level == null) {
                // Pick up where the player left off, clamped to unlocked content.
                val unlocked = preferences.highestUnlockedLevel.first()
                loadLevel(unlocked.coerceAtMost(levels.size))
            }
        }
    }

    /** Player drag; delta in degrees. Re-evaluates alignment immediately. */
    fun dragBy(deltaDeg: Float) {
        val state = _uiState.value
        if (state.isComplete || state.isWalking) return
        rotationController.dragBy(deltaDeg)
        reevaluate(angleDeg = rotationController.angleDeg.value, isDragging = true)
    }

    /** Drag released: snap (animated, or instant under reduce motion). */
    fun endDrag(reduceMotion: Boolean) {
        if (_uiState.value.isWalking) return
        if (reduceMotion) {
            rotationController.snapTo(rotationController.nearestSnapTarget(rotationController.angleDeg.value))
            reevaluate(rotationController.angleDeg.value, isDragging = false)
            return
        }
        val from = rotationController.angleDeg.value
        val target = rotationController.endDrag()
        viewModelScope.launch {
            val duration = 260L
            var elapsed = 0L
            val frame = 16L
            while (elapsed < duration) {
                delay(frame)
                elapsed += frame
                val t = Easings.progress(elapsed, duration)
                val eased = Easings.easeOutCubic(t)
                rotationController.setAngleDeg(
                    rotationController.interpolatedAngle(from, target, eased),
                )
                reevaluate(rotationController.angleDeg.value, isDragging = false)
            }
            rotationController.snapTo(target)
            reevaluate(target, isDragging = false)
        }
    }

    /**
     * The one verb. Walking is a **chunk walk** (see class doc): the girl
     * follows the currently aligned graph as far as it moves her toward the
     * goal — possibly straight to the goal, possibly only to a ledge the
     * player must rotate away from. Freezes the walkable set for the whole
     * chunk: rotating mid-walk cannot dissolve the stone beneath her.
     */
    fun tryWalk() {
        val state = _uiState.value
        val level = state.level ?: return
        if (state.isComplete || state.isWalking) return

        val tier = state.canAdvance
        if (tier == GameUiState.AdvanceTier.STUCK) return

        // The chunk target is exactly what canAdvance computed: goal first,
        // else the reachable node closest to the goal in well-space.
        val chunkTarget = when (tier) {
            GameUiState.AdvanceTier.REACHABLE -> level.goalNode
            GameUiState.AdvanceTier.PROGRESS -> StepwellGeometry.chunkWalkTarget(
                level.segments, state.walkableSegmentIds, state.heroNode, level.goalNode,
            )?.last() ?: return
            GameUiState.AdvanceTier.STUCK -> return
        }

        val path = StepwellGeometry.findPath(
            level.segments, state.walkableSegmentIds, state.heroNode, chunkTarget,
        ) ?: return

        walkJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWalking = true)
            var node = path.first()
            for (next in path.drop(1)) {
                animateWalk(node, next)
                node = next
            }
            _uiState.value = _uiState.value.copy(isWalking = false)
            if (node == level.goalNode) {
                onLevelComplete(level)
            }
        }
    }

    private suspend fun animateWalk(fromNode: String, toNode: String) {
        val level = _uiState.value.level ?: return
        val positions = StepwellGeometry.nodePositions(level.segments)
        val from = positions[fromNode] ?: return
        val to = positions[toNode] ?: return

        _uiState.value = _uiState.value.copy(heroTargetNode = toNode, heroStepProgress = 0f)
        val duration = 420L
        var elapsed = 0L
        val frame = 16L
        while (elapsed < duration) {
            delay(frame)
            elapsed += frame
            val t = Easings.easeInOutCubic(Easings.progress(elapsed, duration))
            _uiState.value = _uiState.value.copy(heroStepProgress = t)
        }
        _uiState.value = _uiState.value.copy(
            heroNode = toNode,
            heroTargetNode = null,
            heroStepProgress = 1f,
        )
    }

    private fun onLevelComplete(level: Level) {
        _hapticPulse.value = _hapticPulse.value + 1
        viewModelScope.launch {
            preferences.setHighestUnlockedLevel(level.order + 1)
            // PRD §6: paywall shows at the end of Level 3 (a free level) —
            // the natural cliffhanger, and never before.
            val showPaywall =
                level.order == PAYWALL_AFTER_ORDER && !billingService.isFullJourneyUnlocked.value
            val isFinale = level.order >= levels.maxOfOrNull { it.order } ?: level.order
            _uiState.value = _uiState.value.copy(
                isComplete = true,
                showPaywallAfterComplete = showPaywall,
                isFinale = isFinale,
            )
        }
    }

    /** Called by the screen when the mural/paywall has been dismissed. */
    fun consumeCompletion() {
        _uiState.value = _uiState.value.copy(isComplete = false, showPaywallAfterComplete = false)
    }

    fun nextLevel() {
        val ordered = levels.sortedBy { it.order }
        if (ordered.isEmpty()) return
        // Stepping past the last level returns to the first instead of
        // silently replaying the finale — but the UI routes the finale
        // through the mural, so this is only a backstop.
        val nextIndex = if (currentIndex >= ordered.size - 1) 0 else currentIndex + 1
        loadLevel(ordered[nextIndex].order)
    }

    /**
     * Single source of truth for what the current angle allows. Everything
     * downstream — the Walk button's three states, the tryWalk chunk target,
     * the renderer's lit/drowned/dormant segments — reads from here, so the
     * affordance can never disagree with the walk contract.
     */
    private fun reevaluate(angleDeg: Float, isDragging: Boolean) {
        val state = _uiState.value
        val level = state.level ?: return

        val alignment = pathAligner.evaluate(level.segments, angleDeg)

        // The twist (PRD §5.1): rotation drives the water, and drowned stone
        // is not stone. Levels 1–3 have no plane; this is the identity.
        val water = WaterPlane.fromLevel(level.water)
        val waterLevel = water?.levelAt(angleDeg) ?: 0f
        val surfaceY = water?.surfaceYAt(angleDeg)
        val walkableIds = StepwellGeometry.walkableSegmentIds(
            level.segments, alignment.walkableIds, water, angleDeg,
        )

        // Which node should Walk reach? Goal first, then any reachable node
        // that shortens the distance to the goal in the well's real geography.
        val pathToGoal =
            StepwellGeometry.findPath(level.segments, walkableIds, state.heroNode, level.goalNode)
        val chunkTarget = if (pathToGoal == null) {
            StepwellGeometry.chunkWalkTarget(
                level.segments, walkableIds, state.heroNode, level.goalNode,
            )
        } else {
            pathToGoal
        }
        val tier = when {
            pathToGoal != null -> GameUiState.AdvanceTier.REACHABLE
            chunkTarget != null -> GameUiState.AdvanceTier.PROGRESS
            else -> GameUiState.AdvanceTier.STUCK
        }

        _uiState.value = state.copy(
            angleDeg = angleDeg,
            isDragging = isDragging,
            alignedSegmentIds = alignment.walkableIds,
            walkableSegmentIds = walkableIds,
            activeGoalDeg = alignment.activeGoalDeg,
            canAdvance = tier,
            waterLevel = waterLevel,
            waterSurfaceY = surfaceY,
        )
    }

    companion object {
        /** PRD §6: paywall shows at the end of Level 3. */
        const val PAYWALL_AFTER_ORDER = 3
    }
}
