package org.torproject.vpn.ui.help

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.AppBarLayout
import org.torproject.vpn.MainActivityViewModel
import org.torproject.vpn.R
import org.torproject.vpn.databinding.FragmentHelpBinding
import org.torproject.vpn.utils.navigateSafe
import org.torproject.vpn.utils.setStatusBarColor

class HelpFragment : Fragment(R.layout.fragment_help), ClickHandler {

    private val activityViewModel: MainActivityViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHelpBinding.bind(view)
        binding.lifecycleOwner = viewLifecycleOwner

        // set collapsing app bar scroll behavior for portrait layouts
        binding.portraitHelpScrollContainer?.let { container ->
            (binding.toolbarLayout?.layoutParams as? AppBarLayout.LayoutParams)?.let { params ->
                if (container.canScrollVertically(1)) {
                    // allow collapsing of the app bar if the content of the scroll container is not completely visible
                    params.setScrollFlags(
                        AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                    )
                } else {
                    // do not allow collapsing of the app bar in case everything fits on the screen
                    params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL)
                }
            }
        }

        // set bottom padding for scroll container in landscape mode
        binding.landscapeHelpScrollContainer?.let { container ->
            activityViewModel.bottomNavBarHeight.observe(viewLifecycleOwner) { height ->
                container.updatePadding(
                    left = container.paddingLeft,
                    top = container.paddingTop,
                    right = container.paddingRight,
                    bottom = height
                )
            }
        }

        binding.handler = this
        setStatusBarColor(binding.appBar)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            binding.appBar.setPadding(
                systemBarInsets.left,
                v.paddingTop,
                systemBarInsets.right,
                v.paddingBottom
            )

            binding.landscapeHelpScrollContainer?.setPadding(
                systemBarInsets.left + cutoutInsets.left,
                binding.landscapeHelpScrollContainer.paddingTop,
                systemBarInsets.right + cutoutInsets.right,
                binding.landscapeHelpScrollContainer.paddingBottom
            )
            windowInsets
        }
    }

    override fun onOfflineHelpClicked(v: View) {
        findNavController().navigateSafe(R.id.action_helpFragment_to_offlineHelpFragment)
    }

    override fun onReportBugClicked(v: View) {
        if (isAdded) {
            ReportBugBottomSheetFragment().show(parentFragmentManager, "ReportBugBottomSheet")
        }
    }

    override fun onContactSupportClicked(v: View) {
        if (isAdded) {
            ContactSupportBottomSheetFragment()
                .show(parentFragmentManager, "ContactSupportBottomSheet")
        }
    }
}
