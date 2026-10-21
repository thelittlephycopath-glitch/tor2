package org.torproject.vpn.utils

import android.content.ContextWrapper
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import org.torproject.vpn.R

fun View.getLifeCycleOwner(): LifecycleOwner? {
    var context = this.context

    while (context is ContextWrapper) {
        if (context is LifecycleOwner) {
            return context
        }
        context = context.baseContext
    }
    return null
}

/**
 * Starts animating the text color of a TextView and its background.
 *
 * @param startTextColorRes resourceId to source text color
 * @param endTextColorRes resourceId to target text color
 * @param startAnimationDrawableRes resourceId of the animated Drawable
 * @param onAnimationStart optional callback when the Animation started
 * @param onAnimationEnd optional callback when the Animation stopped
 */
fun TextView.startVectorAnimationWithEndCallback(
    startTextColorRes: Int,
    endTextColorRes: Int,
    startAnimationDrawableRes: Int,
    onAnimationStart: (() -> Unit)? = null,
    onAnimationEnd: (() -> Unit)? = null
) {
    val lifecycle = this.getLifeCycleOwner()?.lifecycle
    val textAnimator = getTextColorAnimation(
        this,
        startTextColorRes,
        endTextColorRes,
        resources.getInteger(
            R.integer.default_transition_anim_duration
        )
    )

    setBackgroundResource(startAnimationDrawableRes)
    val drawable = background

    AnimatedVectorDrawableCompat.registerAnimationCallback(
        drawable,
        object : Animatable2Compat.AnimationCallback() {
            override fun onAnimationStart(drawable: Drawable?) {
                super.onAnimationStart(drawable)
                lifecycle?.let { lifecycle ->
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        textAnimator.start()
                        onAnimationStart?.invoke()
                    } else {
                        Log.w("ANIMATE", "setUI - animation not started")
                    }
                } ?: {
                    Log.w("ANIMATE", "setUI - animation not started - lifecycle null")
                }
            }

            override fun onAnimationEnd(drawable: Drawable?) {
                lifecycle?.let { lifecycle ->
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        textAnimator.cancel()
                        onAnimationEnd?.invoke()
                        setTextColor(ContextCompat.getColor(context, endTextColorRes))
                    } else {
                        Log.w("ANIMATE", "setUI - animationEnd callback not called stopped")
                    }
                } ?: {
                    Log.w("ANIMATE", "setUI - animation not started - lifecycle null")
                }
            }
        }
    )
    (drawable as Animatable).start()
}
