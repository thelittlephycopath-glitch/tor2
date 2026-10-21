package org.torproject.vpn.utils

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import org.torproject.vpn.R
import org.torproject.vpn.vpn.ConnectionState

/**
 * State indicator View for Connect fragment.
 */
class GradientGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private lateinit var bitmap: Bitmap
    private lateinit var bitmapCanvas: Canvas

    // initial state is just transparent
    private var colorConfig: ColorConfig = StaticColorConfig(android.R.color.transparent, null)

    private val paintForBitmapShader = Paint().apply {
        isDither = true
        strokeWidth = 28f
    }
    private val paintForTransparentGradient = Paint().apply {
        isDither = true
    }

    /**
     * black with alpha for ComposedShader.
     */
    private val blackForGradient = ColorUtils.setAlphaComponent(Color.BLACK, 48)

    init {
        // set current config to animate. This prevents black background for entire view until state is set
        // TODO test required to see if this is necessary
        colorConfig.animate()
    }

    /**
     * set VPN state for this view to sync up with
     */
    fun setState(connectionState: ConnectionState, hasConnectivity: Boolean) {
        colorConfig = when (connectionState) {
            ConnectionState.INIT -> StaticColorConfig(
                android.R.color.transparent,
                previousConfig = colorConfig
            )
            ConnectionState.CONNECTING -> if (hasConnectivity) {
                AnimatedColorConfig(
                    previousConfig = colorConfig
                )
            } else {
                StaticColorConfig(R.color.error, previousConfig = colorConfig)
            }
            ConnectionState.CONNECTED -> if (hasConnectivity) {
                StaticColorConfig(
                    R.color.tertiary,
                    previousConfig = colorConfig
                )
            } else {
                StaticColorConfig(R.color.error, previousConfig = colorConfig)
            }
            ConnectionState.DISCONNECTING -> if (hasConnectivity) {
                AnimatedColorConfig(
                    previousConfig = colorConfig
                )
            } else {
                StaticColorConfig(R.color.error, previousConfig = colorConfig)
            }
            ConnectionState.PRIVATE_DNS_ERROR,
            ConnectionState.DISCONNECTED -> StaticColorConfig(
                R.color.warning,
                previousConfig = colorConfig
            )
            ConnectionState.CONNECTION_ERROR -> StaticColorConfig(
                R.color.error,
                previousConfig = colorConfig
            )
        }
        colorConfig.animate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        colorConfig.onViewDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (this::bitmap.isInitialized) {
            bitmap.recycle()
        }
        bitmap = createBitmap(width, 1)
        bitmapCanvas = Canvas(bitmap)
        colorConfig.prepare()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isInEditMode) {
            return
        }
        // draw opaque 20f thick line
        canvas.drawLine(0f, 0f, width.toFloat(), 0f, paintForBitmapShader)
        // draw the glow
        // todo the 'top' padding needs ot be calculated for 20f stroke width, although it kind of works with approximate value
        canvas.drawRect(
            0f,
            10f,
            width.toFloat(),
            height.toFloat() - 20,
            paintForTransparentGradient
        )
    }

    /**
     * Color config parent. Provides abstraction for managing animation start/stop and shader calculations.
     */
    abstract inner class ColorConfig {
        @ColorInt
        var color1Current: Int = 0

        @ColorInt
        var color2Current: Int = 0
        val animatorSet = AnimatorSet()
        private val evaluator = ArgbEvaluator()
        private var waitingForAnimationPrepare: Boolean = false
        private var isAnimationPrepared: Boolean = false

        protected abstract fun prepareAnimators()

        fun onViewDetachedFromWindow() {
            animatorSet.cancel()
        }

        fun prepare() {
            animatorSet.cancel()
            prepareAnimators()
            isAnimationPrepared = true
            if (waitingForAnimationPrepare) { // assuming we are attached here
                animate()
            }
        }

        fun animate() {
            if (isAnimationPrepared) {
                animatorSet.start()
            } else {
                waitingForAnimationPrepare = true
                if (this@GradientGlowView::bitmap.isInitialized) {
                    prepare()
                }
            }
        }

        protected fun calculateColorTransformationShaders(
            fraction: Float,
            color1Start: Int,
            color1End: Int,
            color2Start: Int,
            color2End: Int
        ) {
            color1Current = evaluator.evaluate(fraction, color1Start, color1End) as Int
            color2Current = evaluator.evaluate(fraction, color2Start, color2End) as Int

            if (this@GradientGlowView::bitmapCanvas.isInitialized) {
                paintForBitmapShader.shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    0f,
                    intArrayOf(color1Current, color2Current),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )

                val gradientShader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    height.toFloat(),
                    intArrayOf(blackForGradient, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.8f),
                    Shader.TileMode.CLAMP
                )

                bitmapCanvas.drawRect(0f, 0f, width.toFloat(), 1f, paintForBitmapShader)

                val bitmapShader =
                    BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

                paintForTransparentGradient.shader =
                    ComposeShader(bitmapShader, gradientShader, PorterDuff.Mode.DST_IN)

                invalidate()
            }
        }

        protected fun calculateColorTranslationShaders(
            fraction: Float,
            colors: IntArray,
            colorsPositions: FloatArray
        ) {
            if (this@GradientGlowView::bitmapCanvas.isInitialized) {
                val matrix = Matrix()
                matrix.setTranslate(fraction, 0f)
                val colorShader = LinearGradient(
                    (-(colors.size - 2) * width).toFloat(),
                    0f,
                    width.toFloat(),
                    0f,
                    colors,
                    colorsPositions,
                    Shader.TileMode.CLAMP
                )
                paintForBitmapShader.shader = colorShader
                colorShader.setLocalMatrix(matrix)

                val gradientShader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    height.toFloat(),
                    intArrayOf(blackForGradient, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.8f),
                    Shader.TileMode.CLAMP
                )
                gradientShader.setLocalMatrix(matrix)

                bitmapCanvas.drawRect(0f, 0f, width.toFloat(), 1f, paintForBitmapShader)

                val bitmapShader =
                    BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

                paintForTransparentGradient.shader =
                    ComposeShader(bitmapShader, gradientShader, PorterDuff.Mode.DST_IN)

                invalidate()
            }
        }
    }

    /**
     * Static color configuration for this view. This will animate from current color(s) to the static color passed in the argument.
     */
    inner class StaticColorConfig(
        @ColorRes color: Int,
        val previousConfig: ColorConfig?
    ) : ColorConfig() {
        @ColorInt
        val color1: Int = ContextCompat.getColor(context, color)
        override fun prepareAnimators() {
            previousConfig?.animatorSet?.cancel()

            val animatorA = TimeAnimator.ofFloat(0f, 1f)
            animatorA.duration = 800

            animatorA.addUpdateListener {
                calculateColorTransformationShaders(
                    it.animatedFraction,
                    previousConfig?.color1Current ?: android.R.color.transparent,
                    color1,
                    previousConfig?.color2Current ?: android.R.color.transparent,
                    color1
                )
            }

            animatorSet.play(animatorA)
        }
    }

    /**
     * Animated color configuration for this view.
     * First the current colors are animated to progress colors(pink and green), those colors are then animated indefinitely!
     */
    inner class AnimatedColorConfig(val previousConfig: ColorConfig?) : ColorConfig() {
        val start: Int = ContextCompat.getColor(context, R.color.connectingRainbowStart)
        val end: Int = ContextCompat.getColor(context, R.color.connectingRainbowEnd)

        // 3 undulations
        private val colors = intArrayOf(start, end, start, end, start, end)
        private val colorsPositions = floatArrayOf(0.00f, 0.20f, 0.40f, 0.60f, 0.80f, 1.00f)

        init {
            color1Current = start
            color2Current = end
        }

        override fun prepareAnimators() {
            previousConfig?.animatorSet?.cancel()

            val animatorA = TimeAnimator.ofFloat(0f, 1f)
            animatorA.duration = 800
            animatorA.addUpdateListener {
                calculateColorTransformationShaders(
                    it.animatedFraction,
                    previousConfig?.color1Current ?: android.R.color.transparent,
                    start,
                    previousConfig?.color2Current ?: android.R.color.transparent,
                    end
                )
            }

            val animatorB = ValueAnimator.ofFloat(0f, (width * (colors.size - 2)).toFloat()).apply {
                duration = 6000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
            }

            animatorB.addUpdateListener { animation ->
                val translation = (animation.animatedValue as Float)
                calculateColorTranslationShaders(translation, colors, colorsPositions)
            }

            animatorSet.playSequentially(animatorA, animatorB)
        }
    }
}
