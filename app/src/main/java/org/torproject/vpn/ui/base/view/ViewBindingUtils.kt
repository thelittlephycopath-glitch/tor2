package org.torproject.vpn.ui.base.view

import androidx.databinding.BindingAdapter
import kotlinx.coroutines.flow.StateFlow

object ViewBindingUtils {

    @BindingAdapter("secondaryText")
    @JvmStatic
    fun setSecondaryText(view: IconTextEntryView, flow: StateFlow<String>) {
        view.setStateFlowForSecondaryText(flow)
    }

    @BindingAdapter("checked")
    @JvmStatic
    fun setCheckedState(view: IconTextEntryView, flow: StateFlow<Boolean>) {
        view.isChecked = flow.value
    }
}
