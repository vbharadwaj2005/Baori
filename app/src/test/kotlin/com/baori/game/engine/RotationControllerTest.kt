package com.baori.game.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Day 1 engine tests: snap behavior is the tactile heart of the rotation
 * mechanic — release must land on a goal when one captures the gesture, and
 * on the free grid otherwise (PRD §5.1, §7.3).
 */
class RotationControllerTest {

    @Test
    fun `angle normalizes on construction and drag`() {
        val controller = RotationController(initialAngleDeg = -30f)
        assertEquals(330f, controller.angleDeg.value, 1e-5f)

        controller.dragBy(45f)
        assertEquals(15f, controller.angleDeg.value, 1e-5f)

        controller.dragBy(400f)
        assertEquals(55f, controller.angleDeg.value, 1e-5f)
    }

    @Test
    fun `dragging sets and endDrag clears the dragging flag`() {
        val controller = RotationController()
        controller.dragBy(5f)
        assertEquals(true, controller.isDragging.value)
        controller.endDrag()
        assertEquals(false, controller.isDragging.value)
    }

    @Test
    fun `endDrag with no goals rounds to the snap step`() {
        val controller = RotationController(snapStepDegrees = 15f)
        controller.dragBy(19f) // 19° is nearer 15° than 30°
        assertEquals(15f, controller.endDrag(), 1e-5f)
    }

    @Test
    fun `endDrag captures a nearby goal over the plain grid`() {
        val controller = RotationController(snapStepDegrees = 15f)
        controller.setSnapTargets(listOf(90f))
        controller.dragBy(87f) // 3° from the goal, well inside the 10° capture
        assertEquals(90f, controller.endDrag(), 1e-5f)
    }

    @Test
    fun `endDrag ignores goals beyond the capture range`() {
        val controller = RotationController(snapStepDegrees = 15f)
        controller.setSnapTargets(listOf(90f))
        controller.dragBy(20f) // 20° is 70° from the goal — grid wins
        assertEquals(15f, controller.endDrag(), 1e-5f)
    }

    @Test
    fun `nearest goal wins when several are listed`() {
        val controller = RotationController()
        controller.setSnapTargets(listOf(90f, 270f))
        controller.dragBy(265f)
        assertEquals(270f, controller.endDrag(), 1e-5f)
    }

    @Test
    fun `snap targets wrap across the 360 boundary`() {
        val controller = RotationController()
        controller.setSnapTargets(listOf(350f))
        controller.dragBy(354f) // 4° from 350°, crossing zero
        assertEquals(350f, controller.endDrag(), 1e-5f)
    }

    @Test
    fun `interpolatedAngle travels the shortest arc`() {
        val controller = RotationController()
        // 350° to 10° must go through 0°, not backwards through 180°.
        assertEquals(
            0f,
            controller.interpolatedAngle(fromDeg = 350f, target = 10f, t = 0.5f),
            1e-5f,
        )
        // t=0 and t=1 are exactly the endpoints.
        assertEquals(
            350f,
            controller.interpolatedAngle(fromDeg = 350f, target = 10f, t = 0f),
            1e-5f,
        )
        assertEquals(
            10f,
            controller.interpolatedAngle(fromDeg = 350f, target = 10f, t = 1f),
            1e-5f,
        )
    }

    @Test
    fun `snapTo forces an exact angle`() {
        val controller = RotationController()
        controller.snapTo(271.4f)
        assertEquals(271.4f, controller.angleDeg.value, 1e-5f)
    }
}
