package org.torproject.vpn.ui.bridgesettings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Guideline
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.torproject.vpn.R
import org.torproject.vpn.databinding.ChipItemBinding
import org.torproject.vpn.databinding.FragmentBridgelinesBinding
import org.torproject.vpn.ui.bridgesettings.model.BridgeLinesFragmentViewModel
import org.torproject.vpn.utils.applyInsetsToViewPadding
import org.torproject.vpn.utils.dpToPx
import org.torproject.vpn.utils.isValidBridgeLine
import org.torproject.vpn.utils.onKeyboardVisibilityChanged

class BridgeLinesFragment :
    Fragment(R.layout.fragment_bridgelines),
    OnSharedPreferenceChangeListener {

    companion object {
        val TAG = BridgeLinesFragment::class.java.simpleName
    }

    private val viewModel: BridgeLinesFragmentViewModel by viewModels()
    private var _binding: FragmentBridgelinesBinding? = null
    private val binding get() = _binding!!
    private var listener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentBridgelinesBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.save -> {
                    handleUnsavedBridges()
                    val savedSuccessfully = viewModel.save()
                    if (savedSuccessfully) {
                        findNavController().popBackStack()
                    }
                    return@setOnMenuItemClickListener savedSuccessfully
                }
                else -> return@setOnMenuItemClickListener false
            }
        }
        binding.textinputBridges.doAfterTextChanged { text ->
            if (text.isNullOrBlank() || !text.contains("\n")) {
                return@doAfterTextChanged
            }
            val trimmed = removeEmptyLines(text)
            val lastLineEndsWithNewLine = text.endsWith("\n")
            val bridgeEntries = trimmed.split("\n")

            val entriesToProcess = if (lastLineEndsWithNewLine) {
                bridgeEntries
            } else {
                bridgeEntries.dropLast(1)
            }
            for (element in entriesToProcess) {
                handleBridgeLine(binding, element)
            }

            if (lastLineEndsWithNewLine) {
                binding.textinputBridges.text = null
            } else {
                binding.textinputBridges.setText(bridgeEntries.last())
                binding.textinputBridges.text?.let {
                    binding.textinputBridges.setSelection(it.length)
                }
            }
        }

        viewModel.bridgeLines.value?.let { initialBridgeLines ->
            for (line in initialBridgeLines) {
                addChipView(binding, line)
            }
        }

        viewModel.bridgeLines.observe(viewLifecycleOwner) { bridgeLines ->
            val hasError = bridgeLines.any { !it.isValidBridgeLine() }
            updateBridgeState(hasError)
        }

        binding.textinputBridges.requestFocus()
        val imm = binding.root.context.getSystemService<InputMethodManager>()
        imm?.showSoftInput(binding.textinputBridges, 0)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.helperText.collect { helperText ->
                    binding.tvHelpline.text = helperText
                }
            }
        }

        // calculate the positioning of the snackbar and the minimal and maximal height of the
        // bridge line input field
        val snackBarHeight = calculateSnackBarHeight(view)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            binding.root.doOnPreDraw {
                listener = binding.root.onKeyboardVisibilityChanged { isVisible, keyboardSize ->

                    // Abort if the fragment's view has been destroyed to prevent a NullPointerException
                    if (_binding == null) return@onKeyboardVisibilityChanged

                    Log.d(TAG, "+++++++++++++ onKeyboardVisibilityChanged!")
                    val bottomGuideLineEnd = if (!isVisible) {
                        // keyboard is not visible: set bottomGuideline above system navigation bar
                        0
                    } else {
                        // keyboard is visible: position the guideline slightly below the keyboard.
                        // Once the snackbar gets added to the view tree the guideline will automatically move up
                        // by snackbarHeight, so that the snackbar is actually visible just above the keyboard
                        keyboardSize - snackBarHeight
                    }

                    binding.bottomGuideline.setGuidelineEnd(bottomGuideLineEnd)
                    calculateScrollViewHeightConstraints(keyboardSize, 0)
                    binding.textinputBridges.requestLayout()
                }
            }
            binding.root.requestLayout()
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(binding.scrollViewContainer) { view, insets ->
                val keyboardInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                calculateScrollViewHeightConstraints(keyboardInsets.bottom, systemBarInsets.bottom)
                ViewCompat.onApplyWindowInsets(view, insets)
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.bottomGuideline) { view, insets ->
                val keyboardInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())

                val bottomGuideLineEnd = if (keyboardInsets.bottom == 0) {
                    // keyboard is not visible: set bottomGuideline above system navigation bar
                    systemBarInsets.bottom + cutoutInsets.bottom
                } else {
                    // keyboard is visible: position the guideline slightly below the keyboard.
                    // Once the snackbar gets added to the view tree the guideline will automatically move up
                    // by snackbarHeight, so that the snackbar is actually visible just above the keyboard
                    keyboardInsets.bottom - snackBarHeight
                }

                (view as Guideline).setGuidelineEnd(bottomGuideLineEnd)
                ViewCompat.onApplyWindowInsets(view, insets)
            }
        }

        // set content container padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.clContent) { view, windowInsets ->
            val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                left = systemBarInsets.left + cutoutInsets.left,
                top = view.paddingTop,
                right = systemBarInsets.right + cutoutInsets.right,
                bottom = systemBarInsets.bottom + cutoutInsets.bottom
            )
            ViewCompat.onApplyWindowInsets(view, windowInsets)
        }

        // set app bar padding
        applyInsetsToViewPadding(
            view = binding.appBar,
            left = true,
            top = true,
            right = true,
            bottom = false
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.let {
            binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
            listener = null
        }
        _binding = null
    }

    private fun calculateScrollViewHeightConstraints(keyboardInsets: Int, systemBarInsets: Int) {
        if (binding.clContent.height == 0) {
            binding.root.doOnPreDraw {
                calculate(keyboardInsets, systemBarInsets)
            }
        } else {
            calculate(keyboardInsets, systemBarInsets)
        }
    }

    /**
     * Calculates the minimal and maximal height the input field is allowed to have.
     * Between these thresholds the input field can grow if multiple entries
     * get added. This calculation takes users display and font scaling
     * (accessibility) settings into consideration.
     */
    private fun calculate(keyboardInsets: Int, systemBarInsets: Int) {
        val parentContainerPaddingBottom = if (keyboardInsets == 0) {
            binding.clContent.paddingBottom
        } else {
            0
        }
        // the minimal height the input field view should show at least 2 lines,
        // even on a small / low density screen or if font scaling was enabled
        val twoLinesHeight = 2 * (
            TypedValue.applyDimension(
                COMPLEX_UNIT_SP,
                16f,
                binding.root.context.resources.displayMetrics
            )
            ).toInt()
        // in order to determine the maximum input container size subtract all UI elements
        // heights that are part of the parent container as well as the keyboard and insets.
        // If accessibility settings are enabled the screen space might be too little to show all
        // information on the screen while providing also sufficient space for the input field.
        // In that case the twoLinesHeight size is applied and top content is pushed outside the
        // screen in favor of the input field
        val maxHeight = (
            binding.clContent.height -
                binding.tvDescription.height - binding.chipGroupBridgeType.height -
                binding.tvHelpline.height -
                keyboardInsets - systemBarInsets -
                parentContainerPaddingBottom
            ).coerceAtLeast(twoLinesHeight.dpToPx(binding.root.context))

        // default minimal height is 150dp
        val minHeight = 150.dpToPx(binding.root.context).coerceAtMost(maxHeight)

        binding.llScrollViewChild.minimumHeight =
            minHeight - binding.scrollViewContainer.marginTop -
            binding.scrollViewContainer.marginBottom
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.clEditText)
        constraintSet.constrainMinHeight(binding.scrollViewContainer.id, minHeight)
        constraintSet.constrainMaxHeight(binding.scrollViewContainer.id, maxHeight)
        constraintSet.applyTo(binding.clEditText)
    }

    @SuppressLint("ShowToast")
    private fun calculateSnackBarHeight(view: View): Int {
        val snackbar = Snackbar.make(view, "Message", Snackbar.LENGTH_SHORT)
        snackbar.view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return snackbar.view.measuredHeight
    }

    private fun removeEmptyLines(myString: CharSequence): CharSequence {
        val regex = Regex("\n\\s*\n")
        return myString.replace(regex, "")
    }

    private fun handleBridgeLine(binding: FragmentBridgelinesBinding, value: String) {
        val trimmedLine = value.trim()
        if (trimmedLine.isEmpty()) {
            return
        }
        if (viewModel.addBridgeLine(trimmedLine)) {
            addChipView(binding, trimmedLine)
        } else {
            Snackbar.make(
                binding.snackbarContainer,
                getString(R.string.duplicate_addresses),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleUnsavedBridges() {
        _binding?.textinputBridges?.text?.let { unSavedBridges ->
            val bridgeEntries = unSavedBridges.split("\n")

            for (element in bridgeEntries) {
                handleBridgeLine(binding, element)
            }
            binding.textinputBridges.text = null
        }
    }

    private fun addChipView(rootBinding: FragmentBridgelinesBinding, value: String) {
        val chipBinding = ChipItemBinding.inflate(layoutInflater)
        chipBinding.chip.text = value
        chipBinding.chip.setOnCloseIconClickListener {
            viewModel.removeBridgeLine(chipBinding.chip.text!!.toString())
            rootBinding.chipContainer.removeView(chipBinding.chip)
        }
        chipBinding.chip.setOnClickListener {
            handleUnsavedBridges()
            val newText = chipBinding.chip.text?.toString() ?: ""
            val textInput = rootBinding.textinputBridges
            textInput.setText(newText)
            textInput.text?.let { textInput.setSelection(it.length) }
            viewModel.removeBridgeLine(newText)
            rootBinding.chipContainer.removeView(chipBinding.chip)
            textInput.requestFocus()
            val imm = rootBinding.root.context.getSystemService<InputMethodManager>()
            imm?.showSoftInput(textInput, 0)
        }
        context?.let {
            chipBinding.chip.closeIconContentDescription = it.getString(R.string.remove_bridge)
            if (!value.isValidBridgeLine()) {
                chipBinding.chip.closeIcon = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.warning_circle_fill
                )
                chipBinding.chip.closeIcon?.setTint(
                    ContextCompat.getColor(requireContext(), R.color.error)
                )
            }
        }
        rootBinding.chipContainer.addView(chipBinding.chip)
        chipBinding.chip.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
    }

    private fun updateBridgeState(isError: Boolean) {
        // Disable menu if there are errors
        binding.toolbar.menu.findItem(R.id.save)?.isEnabled = !isError
        val containerBackground = if (isError) {
            R.drawable.round_material_edittext_outline_error
        } else {
            R.drawable.round_material_edittext_outline
        }
        val textColor = if (isError) R.color.error else R.color.primary

        binding.scrollViewContainer.setBackgroundResource(containerBackground)
        val color = ContextCompat.getColor(requireContext(), textColor)
        binding.tvBridgeAddress.setTextColor(color)
        binding.tvHelpline.setTextColor(color)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    }
}
