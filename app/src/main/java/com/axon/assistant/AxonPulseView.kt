package com.axon.assistant

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * AXON Premium Holographic Orb.
 * Replaces the old PulseView with the new 'Living Essence' design.
 */
class AxonPulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    private var currentState = State.IDLE

    // -- Colors
    private val colorCyan    = Color.parseColor("#00F2FF")
    private val colorMagenta = Color.parseColor("#FF00FF")
    private val colorWhite   = Color.WHITE

    // -- Paints
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(60f, BlurMaskFilter.Blur.NORMAL)
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // -- Anim State
    private var morphFactor = 0f
    private var rotateAngle = 0f
    private var pulseScale  = 1f
    private var glowAlpha   = 100

    private var masterAnimator: ValueAnimator? = null
    private var rotateAnimator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        startAnimations()
    }

    fun setState(state: State) {
        if (currentState == state) return
        currentState = state
        invalidate()
    }

    private fun startAnimations() {
        masterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                morphFactor = it.animatedValue as Float
                pulseScale = 1f + (morphFactor * 0.05f)
                invalidate()
            }
            start()
        }

        rotateAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 10000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotateAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.6f

        canvas.save()
        canvas.scale(pulseScale, pulseScale, cx, cy)

        // Draw Glow
        glowPaint.color = when(currentState) {
            State.LISTENING -> colorMagenta
            State.THINKING  -> colorWhite
            else           -> colorCyan
        }
        glowPaint.alpha = if (currentState == State.THINKING) 180 else 80
        canvas.drawCircle(cx, cy, radius * 0.8f, glowPaint)

        // Draw Rings
        drawRing(canvas, cx, cy, radius * 1.2f, rotateAngle, colorCyan, 0.2f)
        drawRing(canvas, cx, cy, radius * 1.5f, -rotateAngle * 0.7f, colorMagenta, 0.1f)

        // Draw Core
        corePaint.shader = RadialGradient(
            cx, cy, radius * 0.8f,
            intArrayOf(colorWhite, if (currentState == State.LISTENING) colorMagenta else colorCyan),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        
        // Jitter in listening state
        var jx = 0f
        var jy = 0f
        if (currentState == State.LISTENING) {
            jx = (Math.random() * 10 - 5).toFloat()
            jy = (Math.random() * 10 - 5).toFloat()
        }

        canvas.drawCircle(cx + jx, cy + jy, radius * 0.7f, corePaint)

        canvas.restore()

        if (currentState == State.THINKING) {
            rotateAngle += 15f // Extra fast spin for thinking
            invalidate()
        }
    }

    private fun drawRing(canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float, color: Int, opacity: Float) {
        canvas.save()
        canvas.rotate(angle, cx, cy)
        ringPaint.color = color
        ringPaint.alpha = (opacity * 255).toInt()
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawOval(rect, ringPaint)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        masterAnimator?.cancel()
        rotateAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
