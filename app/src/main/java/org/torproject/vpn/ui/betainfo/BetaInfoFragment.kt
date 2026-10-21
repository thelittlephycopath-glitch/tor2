package org.torproject.vpn.ui.betainfo

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.torproject.vpn.R
import org.torproject.vpn.databinding.FragmentBetaInfoBinding
import org.torproject.vpn.ui.help.OfflineHelpFragment
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.applyInsetsToGuideLineBottom
import org.torproject.vpn.utils.applyInsetsToViewPadding
import org.torproject.vpn.utils.navigateSafe
import org.torproject.vpn.utils.setStatusBarColor

class BetaInfoFragment :
    Fragment(R.layout.fragment_beta_info),
    ClickHandler,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        val TAG: String = BetaInfoFragment::class.java.simpleName
    }

    private var preferenceHelper: PreferenceHelper? = null
    private var onPreferencesChanged: (() -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentBetaInfoBinding.bind(view)
        preferenceHelper = PreferenceHelper(view.context)
        preferenceHelper?.registerListener(this)
        binding.lifecycleOwner = viewLifecycleOwner

        applyInsetsToViewPadding(
            binding.contentContainer,
            left = true,
            top = true,
            right = true,
            bottom = false,
            defaultTop = resources.getDimension(R.dimen.compact_padding)
        )
        applyInsetsToGuideLineBottom(binding.guidelineButtons)
        setStatusBarColor(colorRes = R.color.surface)

        activity?.onBackPressedDispatcher?.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // do nothing. This prevents sneaking out of this info fragment by a back gesture
                    // instead of pressing one of the available action buttons
                }
            }
        )

        binding.handler = this
    }

    override fun onDestroyView() {
        super.onDestroyView()
        preferenceHelper?.unregisterListener(this)
        preferenceHelper = null
        onPreferencesChanged = null
    }

    override fun onReportBugsClicked() {
        val bundle = Bundle()
        bundle.putInt("argHelpPageID", OfflineHelpFragment.HELP_PAGE_BUG_REPORT)
        findNavController().navigateSafe(
            R.id.action_navigation_betaInfo_to_OfflineHelpFragment,
            bundle
        )
    }

    override fun onLearnMoreClicked(v: View) {
        findNavController().navigateSafe(R.id.action_navigation_betaInfo_to_OfflineHelpFragment)
    }

    override fun onStartTestingClicked(v: View) {
        if (onPreferencesChanged == null) {
            onPreferencesChanged = {
                findNavController().popBackStack()
            }
        }
        preferenceHelper?.shouldShowGuide = false
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PreferenceHelper.SHOULD_SHOW_GUIDE) {
            onPreferencesChanged?.invoke()
        }
    }
}
