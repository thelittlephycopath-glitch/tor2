package org.torproject.vpn.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.transition.MaterialContainerTransform
import java.io.BufferedReader
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json
import org.torproject.onionmasq.BridgeLineParser
import org.torproject.onionmasq.BridgeLineParser.getIPtConfigFrom
import org.torproject.vpn.R
import org.torproject.vpn.vpn.DataUsage

private const val TAG = "Utils"

/**
 * Reusable configuration for JSON serialization
 */
val Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

fun getTextColorAnimation(
    textView: TextView,
    startColorRes: Int,
    endColorRes: Int,
    duration: Int
): ObjectAnimator {
    val startColor = ContextCompat.getColor(textView.context, startColorRes)
    val endColor = ContextCompat.getColor(textView.context, endColorRes)
    // Log.d("ANIMATE", "setUI animate colors ${String.format("#%08X", 0xFFFFFFFF and startColor.toLong())} -> ${String.format("#%08X", 0xFFFFFFFF and endColor.toLong())}")
    val animator: ObjectAnimator =
        ObjectAnimator.ofArgb(textView, "textColor", startColor, endColor)
    animator.duration = (duration.toLong() - 50).coerceAtLeast(50)
    return animator
}

fun getVerticalBiasChangeAnimator(view: View, startSize: Float, endSize: Float): Animator {
    val animationDuration: Long = 250 // Animation duration in ms

    val animator = ValueAnimator.ofFloat(startSize, endSize)
    animator.duration = animationDuration

    animator.addUpdateListener { valueAnimator ->
        view.updateLayoutParams<ConstraintLayout.LayoutParams> {
            verticalBias = (valueAnimator.animatedValue as Float)
        }
    }
    return animator
}

fun getVisibilityAnimator(
    view: View,
    startVisibility: Int,
    endVisibility: Int,
    lifecycle: Lifecycle
): Animator? {
    if (startVisibility == endVisibility) {
        return null
    }

    if ((
            startVisibility != View.VISIBLE &&
                startVisibility != View.INVISIBLE &&
                startVisibility != View.GONE
            ) ||
        (
            endVisibility != View.VISIBLE &&
                endVisibility != View.INVISIBLE &&
                endVisibility != View.GONE
            )
    ) {
        throw IllegalArgumentException(
            "animated start or end visibility is neither VISIBLE, INVISIBLE or GONE"
        )
    }

    var animator: ValueAnimator? = null
    when (startVisibility) {
        View.VISIBLE -> {
            view.alpha = 1f
            view.visibility = View.VISIBLE
            animator = ValueAnimator.ofFloat(1f, 0f)
        }

        View.GONE, View.INVISIBLE -> {
            view.alpha = 0f
            view.visibility = View.VISIBLE
            if (endVisibility == View.VISIBLE) {
                animator = ValueAnimator.ofFloat(0f, 1f)
            }
        }
    }

    animator?.duration = 250
    animator?.addUpdateListener { valueAnimator ->
        view.alpha = valueAnimator.animatedValue as Float
    }
    animator?.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                view.visibility = endVisibility
            }
        }
    })

    return animator
}

fun animateTextSizeChange(
    textView: TextView,
    startSize: Float,
    endSize: Float,
    lifecycle: Lifecycle,
    endCallback: () -> Unit
) {
    val animationDuration: Long = 6000 // Animation duration in ms

    val animator = ValueAnimator.ofFloat(startSize, endSize)
    animator.duration = animationDuration

    animator.addUpdateListener { valueAnimator ->
        textView.textSize = (valueAnimator.animatedValue as Float)
    }

    animator.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                endCallback()
            }
        }
    })

    animator.start()
}

fun connectionStateGradientAnimation(
    drawable: Drawable,
    context: Context,
    @ColorRes sixColors: IntArray
) {
    val colors = sixColors.map { ContextCompat.getColor(context, it) }
    val duration = context.resources.getInteger(R.integer.default_transition_anim_duration)
    // val gradient = drawable as GradientDrawable
    val gd = GradientDrawable()
    gd.orientation = GradientDrawable.Orientation.TOP_BOTTOM
    val evaluator = ArgbEvaluator()
    val animator = TimeAnimator.ofFloat(0.0f, 1.0f)
    animator.duration = duration.toLong()
    animator.interpolator = AccelerateInterpolator()
    animator.addUpdateListener {
        val fraction = it.animatedFraction

        val newStart = evaluator.evaluate(fraction, colors[0], colors[3]) as Int
        val newMid = evaluator.evaluate(fraction, colors[1], colors[4]) as Int
        val newEnd = evaluator.evaluate(fraction, colors[2], colors[5]) as Int

        gd.colors = intArrayOf(newStart, newMid, newEnd)
        // TODO cant use gd here.... :(. custom view required.
        drawable.setTint(newMid)
    }

    animator.start()
}

fun isRunningOnMainThread(): Boolean {
    return Looper.getMainLooper().thread === Thread.currentThread()
}

fun getFormattedDate(timestamp: Long, locale: Locale?): String? {
    val sdf = SimpleDateFormat("dd/MM/yy, HH:mm:ss.SSS", locale)
    return sdf.format(timestamp)
}

fun updateDataUsage(lastDataUsage: DataUsage, downstreamData: Long, upstreamData: Long): DataUsage {
    val currentTimeStamp = System.currentTimeMillis()
    val timeDelta = ((currentTimeStamp - lastDataUsage.timeStamp) / 1000).coerceAtLeast(1)

    return DataUsage(
        downstreamData = downstreamData,
        upstreamData = upstreamData,
        timeStamp = currentTimeStamp,
        upstreamDataPerSec = (upstreamData - lastDataUsage.upstreamData) / timeDelta,
        downstreamDataPerSec = (downstreamData - lastDataUsage.downstreamData) / timeDelta
    )
}

fun formatBits(bits: Long): String {
    return if (bits < 1000000) {
        "%.2f Kbit".format(bits / 1000.0)
    } else if (bits < 1000000000) {
        "%.2f Mbit".format(bits / 1000000.0)
    } else {
        "%.2f Gbit".format(bits / 1000000000.0)
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1_000_000 -> "%.1f KB".format(bytes / 1_000.0)
    bytes < 1_000_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "%.1f GB".format(bytes / 1_000_000_000.0)
}

fun formatBitRate(bitsPerSec: Long): String = when {
    bitsPerSec < 1_000 -> "$bitsPerSec bit/s"
    bitsPerSec < 1_000_000 -> "%.1f Kbit/s".format(bitsPerSec / 1_000.0)
    bitsPerSec < 1_000_000_000 -> "%.1f Mbit/s".format(bitsPerSec / 1_000_000.0)
    else -> "%.1f Gbit/s".format(bitsPerSec / 1_000_000_000.0)
}

fun formatByteRateToBitRate(bytesPerSec: Long): String {
    return formatBitRate(bytesPerSec * 8)
}

fun getDpInPx(context: Context, dp: Float): Int {
    val scale: Float = context.resources.displayMetrics.density
    return (dp * scale + 0.5f).toInt()
}

fun readAsset(context: Context, fileName: String): String {
    return context
        .assets
        .open(fileName)
        .bufferedReader()
        .use(BufferedReader::readText)
}

fun applyInsetsToGuideLineBottom(view: Guideline, defaultBottomPixels: Float = 0.0f) {
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        val params = v.layoutParams as ConstraintLayout.LayoutParams
        params.guideEnd = insets.bottom + defaultBottomPixels.toInt()
        v.layoutParams = params
        return@setOnApplyWindowInsetsListener windowInsets
    }
}

fun applyInsetsToViewPadding(
    view: View,
    left: Boolean,
    top: Boolean,
    right: Boolean,
    bottom: Boolean,
    defaultLeft: Float = 0.0f,
    defaultTop: Float = 0.0f,
    defaultRight: Float = 0.0f,
    defaultBottom: Float = 0.0f
) {
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        if (left) {
            v.setPadding(
                insets.left + defaultLeft.toInt(),
                v.paddingTop,
                v.paddingRight,
                v.paddingBottom
            )
        }
        if (right) {
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                insets.right + defaultRight.toInt(),
                v.paddingBottom
            )
        }
        if (bottom) {
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                insets.bottom + defaultBottom.toInt()
            )
        }
        if (top) {
            v.setPadding(
                v.paddingLeft,
                insets.top + defaultTop.toInt(),
                v.paddingRight,
                v.paddingBottom
            )
        }
        return@setOnApplyWindowInsetsListener windowInsets
    }
}

fun Fragment.applyDefaultContainerSharedElementTransition() {
    val duration = resources.getInteger(R.integer.default_transition_anim_duration).toLong()
    val containerColor = ContextCompat.getColor(requireContext(), R.color.surface)
    sharedElementEnterTransition = MaterialContainerTransform().apply {
        this.duration = duration
        scrimColor = Color.TRANSPARENT
        drawingViewId = R.id.nav_host_fragment_activity_main
        setAllContainerColors(containerColor)
    }
    sharedElementReturnTransition = MaterialContainerTransform().apply {
        this.duration = duration
        scrimColor = Color.TRANSPARENT
        drawingViewId = R.id.nav_host_fragment_activity_main
        setAllContainerColors(containerColor)
    }
}

fun String.isValidBridgeLine(): Boolean {
    return getIPtConfigFrom(this)?.let { true } ?: false
}

fun String.bridgeType(): String? {
    return getIPtConfigFrom(this)?.bridgeType
}

fun String.capitalizeFirstChar(): String {
    return this.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
}

fun getBridgeTypeLabelRes(type: String?): Int? {
    return when (type) {
        BridgeLineParser.WEBTUNNEL -> R.string.webtunnel_label
        BridgeLineParser.OBFS4 -> R.string.obfs4_label
        BridgeLineParser.SNOWFLAKE -> R.string.snowflake_label
        else -> null
    }
}

fun getBridgeTypeLabel(c: Context, type: String?): String {
    return getBridgeTypeLabelRes(type)?.let { c.getString(it) } ?: ""
}

fun PreferenceHelper.Companion.BridgeType.getDisplayLabelRes(
    bridgeLines: List<String>
): Pair<Int, Int?> {
    return when (this) {
        PreferenceHelper.Companion.BridgeType.None -> R.string.connect_direct_to_tor to null
        PreferenceHelper.Companion.BridgeType.Snowflake -> R.string.snowflake_built_in to null
        PreferenceHelper.Companion.BridgeType.Obfs4 -> R.string.obfs4_built_in to null
        PreferenceHelper.Companion.BridgeType.Manual -> {
            val uniqueTypes = bridgeLines.mapNotNull { it.bridgeType() }.toSet()
            if (uniqueTypes.size > 1) {
                return R.string.multiple_added_by_you to null
            }
            val firstType = uniqueTypes.firstOrNull() ?: BridgeLineParser.VANILLA
            val typeLabelRes = getBridgeTypeLabelRes(firstType)
            if (typeLabelRes == null) {
                R.string.bridge_type_added_by_you to R.string.bridges
            } else {
                R.string.bridge_type_added_by_you to typeLabelRes
            }
        }
    }
}

fun PreferenceHelper.Companion.BridgeType.getDisplayLabel(
    context: Context,
    bridgeLines: List<String>
): String {
    val (resId, argResId) = getDisplayLabelRes(bridgeLines)
    return if (argResId != null) {
        context.getString(resId, context.getString(argResId)).trim()
    } else {
        context.getString(resId)
    }
}

@Suppress("DEPRECATION")
fun Fragment.setStatusBarColor(
    appBarLayout: AppBarLayout? = null,
    @ColorRes colorRes: Int? = null
) {
    // statusBarColor is deprecated and a no-op on API 35+ with edge-to-edge
    // https://developer.android.com/about/versions/16/behavior-changes-16#edge-to-edge
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    if (appBarLayout != null) {
        appBarLayout.addOnOffsetChangedListener { _, _ ->
            requireActivity().window.statusBarColor = appBarLayout.solidColor
        }
    } else if (colorRes != null) {
        requireActivity().window.statusBarColor = ContextCompat.getColor(requireContext(), colorRes)
    }
}

/**
 * Apply system window insets to the fragment container and the AppBarLayout.
 *
 * - Pads `contentView` using system bar insets and adds `activityPaddingBottom` to the bottom.
 * - Pads `appBarLayout` using top and horizontal system bar insets.
 */
fun setupFragmentContainerInsets(
    contentView: View? = null,
    appBarLayout: AppBarLayout? = null,
    activityPaddingBottom: Int = 0
) {
    contentView?.let { view ->
        ViewCompat.setOnApplyWindowInsetsListener(view) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            val bottomPadding = activityPaddingBottom.takeIf { it != 0 } ?: systemBars.bottom

            view.updatePadding(
                left = systemBars.left,
                top = view.paddingTop,
                right = systemBars.right,
                bottom = bottomPadding
            )

            windowInsets
        }
    }

    appBarLayout?.let {
        ViewCompat.setOnApplyWindowInsetsListener(it) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right
            )

            windowInsets
        }
    }
}

/**
 * Loads icon for the given package as bitmap
 */
@Composable
fun getIconForPackage(
    packageName: String,
    context: Context = LocalContext.current,
    isPreview: Boolean = LocalInspectionMode.current
): ImageBitmap {
    if (isPreview) {
        return createBitmap(1, 1).apply {
            eraseColor(Color.GRAY)
        }.asImageBitmap()
    }

    return try {
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        val icon = packageInfo.applicationInfo!!.loadIcon(context.packageManager)
        if (icon.intrinsicWidth > 0 && icon.intrinsicHeight > 0) {
            icon.toBitmap(96, 96)
        } else {
            context.packageManager.defaultActivityIcon.toBitmap(96, 96)
        }
    } catch (exception: Exception) {
        Log.e(TAG, "Failed to get icon for package!", exception)
        context.packageManager.defaultActivityIcon.toBitmap(96, 96)
    }.asImageBitmap()
}

/**
 * Returns a launch intent for the package if exists
 */
fun getLaunchIntent(context: Context, packageName: String): Intent? {
    val intent = when {
        isTv(context) -> context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
        else -> context.packageManager.getLaunchIntentForPackage(packageName)
    } ?: return null

    return intent.apply {
        addCategory(
            if (isTv(context)) {
                Intent.CATEGORY_LEANBACK_LAUNCHER
            } else {
                Intent.CATEGORY_LAUNCHER
            }
        )
    }
}

/**
 * Returns a distinct flow of paging objects loaded from the database
 */
fun <T : Any> distinctPagedFlow(
    pageSize: Int = 20,
    enablePlaceholders: Boolean = false,
    data: () -> PagingSource<Int, T>
): Flow<PagingData<T>> = Pager(
    config = PagingConfig(enablePlaceholders = enablePlaceholders, pageSize = pageSize),
    pagingSourceFactory = data
).flow.distinctUntilChanged()

/**
 * Whether this device is TV
 */
private fun isTv(context: Context): Boolean {
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
