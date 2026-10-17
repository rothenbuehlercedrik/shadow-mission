package com.shadowmission.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.input.GestureDetector.GestureAdapter
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.shadowmission.level.Level

/**
 * Two-finger pinch zooms, drag pans. On desktop the mouse wheel zooms too.
 * The camera is kept over the building so the player never loses it.
 */
class CameraController(
    private val camera: OrthographicCamera,
    private val level: Level,
) : GestureAdapter() {

    private var zoomAtPinchStart = 1f

    val scrollInput = object : InputAdapter() {
        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            setZoom(camera.zoom * (1f + amountY * 0.1f))
            return true
        }
    }

    override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
        val unitsPerPixel = camera.zoom * camera.viewportWidth / screenWidth()
        camera.position.add(-deltaX * unitsPerPixel, deltaY * unitsPerPixel, 0f)
        clamp()
        return true
    }

    override fun zoom(initialDistance: Float, distance: Float): Boolean {
        setZoom(zoomAtPinchStart * initialDistance / distance)
        return true
    }

    override fun pinch(initialPointer1: Vector2, initialPointer2: Vector2, pointer1: Vector2, pointer2: Vector2): Boolean = false

    override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        zoomAtPinchStart = camera.zoom
        return false
    }

    override fun pinchStop() {
        zoomAtPinchStart = camera.zoom
    }

    fun centreOn(x: Float, y: Float) {
        camera.position.set(x, y, 0f)
        clamp()
    }

    private fun setZoom(zoom: Float) {
        camera.zoom = MathUtils.clamp(zoom, MIN_ZOOM, MAX_ZOOM)
        clamp()
    }

    /** Keeps the view centre inside the building plus a small margin. */
    fun clamp() {
        val halfW = camera.viewportWidth * camera.zoom / 2f
        val halfH = camera.viewportHeight * camera.zoom / 2f
        camera.position.x = clampAxis(camera.position.x, level.left - MARGIN, level.right + MARGIN, halfW)
        camera.position.y = clampAxis(camera.position.y, -MARGIN, level.height + MARGIN, halfH)
        camera.update()
    }

    private fun clampAxis(value: Float, min: Float, max: Float, half: Float): Float =
        if (max - min <= half * 2f) (min + max) / 2f else MathUtils.clamp(value, min + half, max - half)

    private fun screenWidth(): Float = Gdx.graphics.width.toFloat()

    companion object {
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 2f
        const val MARGIN = 2f
    }
}
