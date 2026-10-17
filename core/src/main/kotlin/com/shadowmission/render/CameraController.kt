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

    /** Called for taps that are not part of a pan or pinch; `count` is 2 for a double tap. */
    var onTap: (screenX: Float, screenY: Float, count: Int) -> Unit = { _, _, _ -> }

    /** While true the camera glides after the assassin; panning by hand turns it off until the next tap. */
    var following = true

    override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
        onTap(x, y, count)
        return true
    }

    val scrollInput = object : InputAdapter() {
        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            setZoom(camera.zoom * (1f + amountY * 0.1f))
            return true
        }
    }

    override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
        following = false
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

    fun follow(x: Float, y: Float, delta: Float) {
        if (!following) return
        val t = 1f - Math.pow(0.02, delta.toDouble()).toFloat()
        camera.position.x += (x - camera.position.x) * t
        camera.position.y += (y - camera.position.y) * t
        clamp()
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
