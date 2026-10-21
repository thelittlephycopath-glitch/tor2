package org.torproject.vpn.ui.bridgesettings.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import kotlinx.coroutines.flow.StateFlow
import org.torproject.vpn.R
import org.torproject.vpn.databinding.BridgeRequestEntryViewBinding

/**
 * BridgeRequestEntry used in Bridges settings screen
 */
class BridgeRequestEntryView : ConstraintLayout {

    private val binding by lazy {
        BridgeRequestEntryViewBinding.inflate(LayoutInflater.from(context), this, true)
    }

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        // Load attributes
        context.withStyledAttributes(
            attrs,
            R.styleable.BridgeRequestEntryView,
            defStyle,
            0
        ) {
            binding.tvTitle.text = getString(R.styleable.BridgeRequestEntryView_text)
            binding.tvSubtitle.text = getString(R.styleable.BridgeRequestEntryView_secondaryText)
            binding.ivArrow.isVisible =
                getBoolean(R.styleable.BridgeRequestEntryView_external, true)
            binding.ivIcon.setImageDrawable(
                getDrawable(R.styleable.BridgeRequestEntryView_drawable)
            )
        }
    }

    fun setSpannableForSecondaryText(spannable: Spannable) {
        binding.tvSubtitle.text = spannable
    }

    fun setDrawableStateFlowForIcon(flow: StateFlow<Drawable?>) {
        binding.ivIcon.setImageDrawable(flow.value)
    }
}
