package org.torproject.vpn.ui.base.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import org.torproject.vpn.R
import org.torproject.vpn.databinding.RadioButtonItemViewBinding

/**
 * Custom radio button item view used in BridgeSettingsFragment
 */
class RadioButtonItemView : ConstraintLayout {

    private val binding by lazy {
        RadioButtonItemViewBinding.inflate(LayoutInflater.from(context), this, true)
    }

    private var onRadioButtonClickListener: (() -> Unit)? = null

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
            R.styleable.RadioButtonItemView,
            defStyle,
            0
        ) {
            binding.tvTitle.text = getString(R.styleable.RadioButtonItemView_title)
            binding.tvSubtitle.text = getString(R.styleable.RadioButtonItemView_subtitle)

            val showEditIcon = getBoolean(R.styleable.RadioButtonItemView_showEditIcon, false)
            binding.ivEditIcon.visibility = if (showEditIcon) View.VISIBLE else View.GONE
            binding.view2.visibility = if (showEditIcon) View.VISIBLE else View.GONE

            binding.radioButton.isChecked =
                getBoolean(R.styleable.RadioButtonItemView_checked, false)
        }

        // Set click listener for the entire view to toggle radio button
        binding.root.setOnClickListener {
            if (!binding.radioButton.isChecked) {
                binding.radioButton.isChecked = true
                onRadioButtonClickListener?.invoke()
            }
        }

        // Also handle direct radio button clicks
        binding.radioButton.setOnClickListener {
            if (!binding.radioButton.isChecked) {
                binding.radioButton.isChecked = true
            }
            onRadioButtonClickListener?.invoke()
        }

        // Prevent radio button from being unchecked when clicked if already checked
        binding.radioButton.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked && binding.radioButton.isPressed) {
                // Don't allow unchecking via direct radio button click
                binding.radioButton.isChecked = true
            }
        }
    }

    fun setOnRadioButtonClickListener(listener: () -> Unit) {
        onRadioButtonClickListener = listener
    }

    fun setOnEditIconClickListener(listener: () -> Unit) {
        binding.ivEditIcon.setOnClickListener { listener() }
    }

    var isChecked: Boolean
        get() = binding.radioButton.isChecked
        set(value) {
            binding.radioButton.isChecked = value
        }

    var title: CharSequence
        get() = binding.tvTitle.text
        set(value) {
            binding.tvTitle.text = value
        }

    var subtitle: CharSequence
        get() = binding.tvSubtitle.text
        set(value) {
            binding.tvSubtitle.text = value
        }

    var showEditIcon: Boolean
        get() = binding.ivEditIcon.isVisible
        set(value) {
            binding.ivEditIcon.visibility = if (value) View.VISIBLE else View.GONE
        }
}
