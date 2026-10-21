package org.torproject.vpn.ui.configure

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.torproject.vpn.MainActivityViewModel
import org.torproject.vpn.R
import org.torproject.vpn.databinding.FragmentConfigureBinding
import org.torproject.vpn.extensions.navigateTo
import org.torproject.vpn.ui.base.view.BaseDialogFragment
import org.torproject.vpn.ui.configure.model.ConfigureFragmentViewModel
import org.torproject.vpn.ui.navigation.Destination
import org.torproject.vpn.utils.navigateSafe
import org.torproject.vpn.utils.setStatusBarColor

class ConfigureFragment : Fragment(R.layout.fragment_configure), ClickHandler {
    companion object {
        val TAG: String = ConfigureFragment::class.java.simpleName
    }

    private val activityViewModel: MainActivityViewModel by activityViewModels()
    private val configureFragmentViewModel: ConfigureFragmentViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentConfigureBinding.bind(view)
        binding.viewModel = configureFragmentViewModel
        binding.handler = this
        binding.lifecycleOwner = viewLifecycleOwner

        setStatusBarColor(binding.appBar)

        binding.quickstart.isChecked = configureFragmentViewModel.startOnBoot.value
        binding.quickstart.setOnCheckedChangeListener(
            configureFragmentViewModel::onStartOnBootChanged
        )
        binding.verboseLogs.isChecked = configureFragmentViewModel.verboseLogs.value
        setCheckedChangeListener(binding)

        binding.exitLocation.subtitle = configureFragmentViewModel.exitNodeCountry
        binding.bridges.subtitle = configureFragmentViewModel.selectedBridgeType
        activityViewModel.bottomNavBarHeight.observe(viewLifecycleOwner) { height ->
            binding.spacer.post {
                val params = binding.spacer.layoutParams
                params.height = height
                binding.spacer.layoutParams = params
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // we cannot use applyInsetsToViewPadding() for this appBar, which collapses
            binding.appBar.setPadding(
                systemBarInsets.left,
                v.paddingTop,
                systemBarInsets.right,
                v.paddingBottom
            )

            binding.llContentContainer.setPadding(
                cutoutInsets.left + systemBarInsets.left,
                0,
                cutoutInsets.right + systemBarInsets.right,
                0
            )
            return@setOnApplyWindowInsetsListener windowInsets
        }
    }

    fun setCheckedChangeListener(binding: FragmentConfigureBinding) {
        binding.verboseLogs.setOnCheckedChangeListener { button, isChecked ->
            if (button.isPressed) {
                val dialog = BaseDialogFragment.createEnableVerboseLogsDialog(
                    isChecked,
                    object : BaseDialogFragment.OnCancelInterface {
                        override fun onCancel() {
                            binding.verboseLogs.setOnCheckedChangeListener(null)
                            binding.verboseLogs.isChecked = !isChecked
                            setCheckedChangeListener(binding)
                        }
                    }
                )
                dialog.show(parentFragmentManager, "ENABLE_VERBOSE_LOGS_DIALOG")
            }
        }
    }

    override fun onAppsClicked(v: View) {
        requireContext().navigateTo(Destination.AppRouting)
    }

    override fun onAppIconClicked(v: View) {
        requireContext().navigateTo(Destination.IconSetting)
    }

    override fun onAboutClicked(v: View) {
        requireContext().navigateTo(Destination.About)
    }

    override fun onPrivacyPolicyClicked(v: View) {
        findNavController().navigateSafe(R.id.action_configureFragment_to_PrivacyPolicy)
    }

    override fun onLicencesClicked(v: View) {
        findNavController().navigateSafe(R.id.action_configureFragment_to_Licenses)
    }

    override fun onTorLogsClicked(v: View) {
        findNavController().navigateSafe(R.id.action_configureFragment_to_LoggingFragment)
    }

    override fun onBridgeSettingsClicked(v: View) {
        findNavController().navigateSafe(R.id.action_configureFragment_to_bridgeSettingsFragment)
    }

    override fun onAlwaysOnClicked(v: View) {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_VPN_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }.onFailure {
            it.printStackTrace()
        }
    }

    override fun onExitLocationClicked(v: View) {
        requireContext().navigateTo(Destination.ExitSelection)
    }
}
