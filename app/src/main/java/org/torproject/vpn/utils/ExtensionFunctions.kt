package org.torproject.vpn.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.FloatRange
import androidx.annotation.IdRes
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDirections

/**
 * calculates center of the view
 */
fun View.center() = Point((width / 2), (height / 2))

/**
 * calculates center of the view in parent
 */
fun View.centerInParent() = Point((left + width / 2), (top + height / 2))

/**
 * Complete shorthand for Animators
 */
fun Animator.animateWithEndCallback(lifecycle: Lifecycle, onAnimationEnd: () -> Unit) {
    addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                onAnimationEnd()
            }
        }
    })

    start()
}

/**
 * scale for outline
 * source : https://github.com/rock3r/uplift
 */
fun Rect.scale(
    @FloatRange(from = -1.0, to = 1.0) scaleX: Float,
    @FloatRange(from = -1.0, to = 1.0) scaleY: Float
) {
    val newWidth = width() * scaleX
    val newHeight = height() * scaleY
    val deltaX = (width() - newWidth) / 2
    val deltaY = (height() - newHeight) / 2

    set(
        (left + deltaX).toInt(),
        (top + deltaY).toInt(),
        (right - deltaX).toInt(),
        (bottom - deltaY).toInt()
    )
}

fun NavController.navigateSafe(@IdRes action: Int, args: Bundle? = null): Boolean {
    return try {
        navigate(action, args)
        true
    } catch (t: Throwable) {
        Log.e("TorVPN navigation", "Navigation failed: ${t.localizedMessage}")
        false
    }
}
fun NavController.navigateSafe(action: NavDirections): Boolean {
    return try {
        navigate(action)
        true
    } catch (t: Throwable) {
        Log.e("TorVPN navigation", "Navigation failed: ${t.localizedMessage}")
        false
    }
}

fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}

/**
 * Creates a OnGlobalLayoutListener in order to get notified when the soft keyboard showed up and to
 * provide feedback on the keyboards' height.
 * It's the callers' responsibility to remove the listener before the view gets destroyed to avoid memory leaks.
 */
@Deprecated(
    message = "This method should only be called on devices with Android API levels below R (30)",
    level = DeprecationLevel.WARNING
)
fun View.onKeyboardVisibilityChanged(
    callback: (Boolean, Int) -> Unit
): ViewTreeObserver.OnGlobalLayoutListener {
    val initialHeight = this.rootView.height
    var previousKeyboardState = false

    val listener = ViewTreeObserver.OnGlobalLayoutListener {
        val visibleFrame = Rect()
        this.rootView.getWindowVisibleDisplayFrame(visibleFrame)
        val visibleHeight = visibleFrame.bottom - visibleFrame.top
        val heightDifference = initialHeight - visibleHeight
        val keyboardThreshold = 200.dpToPx(this.context)
        val isKeyboardVisible = heightDifference > keyboardThreshold
        if (isKeyboardVisible != previousKeyboardState) {
            previousKeyboardState = isKeyboardVisible
            callback(isKeyboardVisible, heightDifference)
        }
    }

    viewTreeObserver.addOnGlobalLayoutListener(listener)
    return listener
}
