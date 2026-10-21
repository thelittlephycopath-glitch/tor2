package org.torproject.vpn.ui.bridgesettings.view

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.databinding.BindingAdapter
import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.StateFlow

object ViewBindingUtils {

    @BindingAdapter("secondaryText")
    @JvmStatic
    fun setSecondaryText(view: BridgeRequestEntryView, spannableString: SpannableString) {
        view.setSpannableForSecondaryText(spannableString)
    }

    @BindingAdapter("drawable")
    @JvmStatic
    fun setDrawable(view: BridgeRequestEntryView, flow: StateFlow<Drawable?>) {
        view.setDrawableStateFlowForIcon(flow)
    }

    /**
     * This binding adapter only supports LiveData with color and drawable resources for now.
     */
    @BindingAdapter("src")
    @JvmStatic
    fun setDrawable(view: ImageView, resourceId: LiveData<Int>) {
        resourceId.value?.let {
            AppCompatResources.getDrawable(view.context, it)?.let { drawable ->
                view.foreground = drawable
            } ?: run {
                try {
                    view.context.resources.getColor(it, view.context.theme)
                } catch (resourceNotFound: Resources.NotFoundException) {
                    resourceNotFound.printStackTrace()
                }
            }
        }
    }
}
