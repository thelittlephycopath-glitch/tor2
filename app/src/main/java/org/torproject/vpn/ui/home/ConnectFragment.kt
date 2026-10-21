package org.torproject.vpn.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.torproject.onionmasq.logging.LogObservable
import org.torproject.vpn.MainActivity
import org.torproject.vpn.MainActivity.Companion.KEY_ACTION
import org.torproject.vpn.R
import org.torproject.vpn.databinding.FragmentConnectBinding
import org.torproject.vpn.extensions.navigateTo
import org.torproject.vpn.ui.help.OfflineHelpFragment
import org.torproject.vpn.ui.home.model.ACTION_APPS
import org.torproject.vpn.ui.home.model.ACTION_CONNECTION
import org.torproject.vpn.ui.home.model.ACTION_EXIT_NODE_SELECTION
import org.torproject.vpn.ui.home.model.ACTION_LOGS
import org.torproject.vpn.ui.home.model.ACTION_REQUEST_NOTIFICATION_PERMISSION
import org.torproject.vpn.ui.home.model.ConnectFragmentViewModel
import org.torproject.vpn.ui.navigation.Destination
import org.torproject.vpn.utils.PreferenceHelper
import org.torproject.vpn.utils.PreferenceHelper.Companion.BRIDGE_TYPE
import org.torproject.vpn.utils.applyInsetsToGuideLineBottom
import org.torproject.vpn.utils.applyInsetsToViewPadding
import org.torproject.vpn.utils.getDpInPx
import org.torproject.vpn.utils.navigateSafe
import org.torproject.vpn.utils.setStatusBarColor
import org.torproject.vpn.utils.startVectorAnimationWithEndCallback
import org.torproject.vpn.vpn.ConnectionState
import org.torproject.vpn.vpn.VpnServiceCommand
import org.torproject.vpn.vpn.VpnStatusObservable

class ConnectFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        val TAG: String = ConnectFragment::class.java.simpleName
        val TRIGGER_ACTION_CONNECT: String = "trigger_connect_action"
    }

    private var _binding: FragmentConnectBinding? = null
    private val binding get() = _binding!!
    private val connectFragmentViewModel: ConnectFragmentViewModel by viewModels()

    // this is required to store current state that UI is in so we can decide whether to animate to next state
    private lateinit var currentVpnState: ConnectionState

    private lateinit var preferenceHelper: PreferenceHelper

    private var vpnPermissionDialogStartTime = 0L

    private var initStateFabSpacing: Float = 0f
    private var connectingStateFabSpacing: Float = 0f
    private var connectedStateFabSpacing: Float = 0f
    private var animationDuration: Long = 0

    private var startForResult: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                VpnServiceCommand.startVpn(context)
            } else {
                // this indicates that the permission request failed almost instantly.
                // One of the reason could be that other VPN has always-on flag started.
                if (System.currentTimeMillis() - vpnPermissionDialogStartTime < 200) {
                    LogObservable.getInstance()
                        .addLog(
                            "VPN permission request failed instantly. Other VPN is likely on ALWAYS-ON mode!"
                        )
                }

                VpnStatusObservable.update(ConnectionState.CONNECTION_ERROR)
            }
            connectFragmentViewModel.onVpnPrepared()
        }

    private var startNotificationRequestForResult: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Log.w(TAG, "Notification request for Tor VPN was not granted!")
            }
            connectFragmentViewModel.onNotificationPermissionResult()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        preferenceHelper = PreferenceHelper(requireContext())
        preferenceHelper.registerListener(this)
        _binding = FragmentConnectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val c = binding.root.context
        applyInsetsToViewPadding(
            binding.toolbar,
            left = true,
            top = true,
            right = true,
            bottom = false,
            defaultLeft = c.resources.getDimension(
                R.dimen.connection_status_bar_horizontal_padding
            ),
            defaultTop = c.resources.getDimension(R.dimen.connection_status_bar_vertical_padding),
            defaultRight = c.resources.getDimension(
                R.dimen.connection_status_bar_horizontal_padding
            ),
            defaultBottom = c.resources.getDimension(R.dimen.connection_status_bar_vertical_padding)
        )
        applyInsetsToGuideLineBottom(
            binding.upperGuidelineActionBtns,
            requireContext().resources.getDimension(R.dimen.connection_button_upper_guideline)
        )
        applyInsetsToGuideLineBottom(
            binding.lowerGuidelineActionBtns,
            requireContext().resources.getDimension(R.dimen.connection_button_lower_guideline)
        )
        setStatusBarColor(colorRes = R.color.surface)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = connectFragmentViewModel

        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val rtlFactor = if (isRtl) 1f else -1f
        initStateFabSpacing = getDpInPx(requireContext(), 0f).toFloat() * rtlFactor
        // dp padding between connect - connecting
        connectingStateFabSpacing = getDpInPx(requireContext(), (9f)).toFloat() * rtlFactor
        // dp padding between connect - stop
        connectedStateFabSpacing = getDpInPx(requireContext(), (25f)).toFloat() * rtlFactor

        animationDuration = resources.getInteger(R.integer.default_transition_anim_duration)
            .toLong()

        // Listen for `TRIGGER_ACTION_CONNECT` result
        parentFragmentManager.setFragmentResultListener(TRIGGER_ACTION_CONNECT, this) { _, _ ->
            connectFragmentViewModel.connectStateButtonClicked()
        }

        connectFragmentViewModel.prepareVpn.observe(
            viewLifecycleOwner,
            Observer<Intent?> { intent ->
                intent?.let {
                    vpnPermissionDialogStartTime = System.currentTimeMillis()
                    startForResult.launch(it)
                }
            }
        )

        connectFragmentViewModel.updateConnectionLabel()

        currentVpnState = connectFragmentViewModel.connectionState.value
        setUIState(currentVpnState, connectFragmentViewModel.internetConnectivity.value)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        connectFragmentViewModel.connectionState,
                        connectFragmentViewModel.internetConnectivity
                    ) { vpnState, hasConnectivity ->
                        vpnState to hasConnectivity
                    }.collect { (vpnState, hasConnectivity) ->
                        setUIState(vpnState, hasConnectivity)
                    }
                }

                launch {
                    setUIState(currentVpnState, connectFragmentViewModel.internetConnectivity.value)
                }

                launch {
                    connectFragmentViewModel.guideScreenVisibility.collect { isVisible ->
                        if (isVisible) {
                            findNavController().navigateSafe(
                                R.id.action_navigation_connect_to_BetaInfoFragment
                            )
                        }
                    }
                }
                launch {
                    connectFragmentViewModel.action.collect { action ->
                        when (action) {
                            ACTION_LOGS -> {
                                findNavController().navigateSafe(
                                    R.id.action_navigation_connect_to_loggingFragment
                                )
                            }
                            ACTION_REQUEST_NOTIFICATION_PERMISSION -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    startNotificationRequestForResult.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                            }
                            ACTION_EXIT_NODE_SELECTION -> {
                                requireContext().navigateTo(Destination.ExitSelection)
                            }
                            ACTION_APPS -> {
                                requireContext().navigateTo(Destination.AppRouting)
                            }
                            ACTION_CONNECTION -> {
                                val shared = FragmentNavigatorExtras(
                                    binding.includeStats.clConnectionCard to
                                        "connection_container_transform"
                                )
                                findNavController().navigate(
                                    R.id.action_navigation_connect_to_BridgesSettingsFragment,
                                    null,
                                    null,
                                    shared
                                )
                            }
                            else -> {
                                // other cases of navigation.
                            }
                        }
                    }
                }

                launch {
                    connectFragmentViewModel.state.collect { state ->
                        if (state.previous == ConnectionState.PRIVATE_DNS_ERROR) {
                            Snackbar.make(
                                binding.root,
                                R.string.error_private_dns_hint,
                                Snackbar.LENGTH_SHORT
                            ).apply {
                                anchorView = binding.tvConnectActionBtn
                                show()
                            }
                        }
                    }
                }
            }
        }

        arguments?.let { bundle ->
            val action = bundle.getString(KEY_ACTION)
            if (MainActivity.ACTION_REQUEST_VPN_PERMISSON == action) {
                connectFragmentViewModel.prepareToStartVPN()
            }
        }

        binding.errorDns?.buttonLearn?.setOnClickListener {
            findNavController().navigate(
                ConnectFragmentDirections.actionNavigationConnectToOfflineHelpFragment(
                    OfflineHelpFragment.HELP_PAGE_PRIVATE_DNS
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        connectFragmentViewModel.checkIsPrivateDnsEnforced()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        preferenceHelper.unregisterListener(this)
    }

    private fun setUIState(vpnState: ConnectionState, hasInternetConnectivity: Boolean) {
        Log.d(
            TAG,
            "setUIState: ${if (::currentVpnState.isInitialized) currentVpnState else "not initialized"} --> ${vpnState.name} (hasInternetConnectivity; $hasInternetConnectivity)"
        )
        binding.gradientView.setState(vpnState, hasInternetConnectivity)

        when (vpnState) {
            ConnectionState.INIT -> showInitUI()
            ConnectionState.CONNECTING -> showConnectingTransition()
            ConnectionState.CONNECTED -> {
                showConnectedTransition()
            }
            ConnectionState.DISCONNECTED -> {
                showDisconnectedTransition()
            }
            ConnectionState.CONNECTION_ERROR -> {
                showErrorTransition()
            }
            ConnectionState.DISCONNECTING -> showDisconnectingTransition()

            ConnectionState.PRIVATE_DNS_ERROR -> showPrivateDnsErrorTransition()
        }
        currentVpnState = vpnState
    }

    private fun showInitUI() {
        binding.clSelectionExitInner.translationX = initStateFabSpacing
        binding.tvConnectActionBtn.setBackgroundResource(R.drawable.bg_btn_connect_interactive)
        binding.tvConnectActionBtn.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.inverse_on_surface)
        )
    }

    /*
     * Scene transitions
     *
     * ************
     */

    private fun showConnectingTransition() {
        if (currentVpnState == ConnectionState.INIT ||
            currentVpnState == ConnectionState.DISCONNECTED ||
            currentVpnState == ConnectionState.CONNECTION_ERROR
        ) {
            binding.clSelectionExitInner.animate().translationX(
                connectingStateFabSpacing
            ).setDuration(animationDuration)
                .setInterpolator(DecelerateInterpolator()).start()

            // connect/reconnect to cancel btn animation
            binding.tvConnectActionBtn.startVectorAnimationWithEndCallback(
                R.color.inverse_on_surface,
                R.color.on_surface,
                startAnimationDrawableRes = R.drawable.av_connect_to_cancel,
                onAnimationEnd = {
                    if (currentVpnState == ConnectionState.CONNECTING) {
                        _binding?.tvConnectActionBtn?.setBackgroundResource(
                            R.drawable.bg_btn_cancel_interactive
                        )
                    }
                }
            )
        } else {
            binding.clSelectionExitInner.translationX = connectingStateFabSpacing
            binding.tvConnectActionBtn.setBackgroundResource(R.drawable.bg_btn_cancel_interactive)
            binding.tvConnectActionBtn.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.on_surface)
            )
        }
    }

    private fun showConnectedTransition() {
        if (currentVpnState == ConnectionState.CONNECTING) {
            binding.clSelectionExitInner.animate()
                .translationX(connectedStateFabSpacing)
                .setDuration(animationDuration)
                .setInterpolator(DecelerateInterpolator())
                .start()

            // cancel to stop transition
            binding.tvConnectActionBtn.startVectorAnimationWithEndCallback(
                R.color.on_surface,
                R.color.transparent,
                startAnimationDrawableRes = R.drawable.av_cancel_to_stop,
                onAnimationEnd = {
                    if (currentVpnState == ConnectionState.CONNECTED) {
                        _binding?.tvConnectActionBtn?.setBackgroundResource(
                            R.drawable.bg_btn_stop_interactive
                        )
                    }
                }
            )
        } else {
            binding.clSelectionExitInner.translationX = connectedStateFabSpacing
            binding.tvConnectActionBtn.setBackgroundResource(R.drawable.bg_btn_stop_interactive)
            binding.tvConnectActionBtn.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.transparent)
            )
        }
    }

    private fun showErrorTransition() {
        binding.includeError.root.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        if (currentVpnState == ConnectionState.CONNECTING) {
            binding.clSelectionExitInner.animate()
                .translationX(initStateFabSpacing)
                .setDuration(animationDuration)
                .setInterpolator(DecelerateInterpolator())
                .start()

            // cancel to connect transition
            binding.tvConnectActionBtn.startVectorAnimationWithEndCallback(
                R.color.on_surface,
                R.color.inverse_on_surface,
                startAnimationDrawableRes = R.drawable.av_cancel_to_connect,
                onAnimationEnd = {
                    if (currentVpnState == ConnectionState.CONNECTION_ERROR) {
                        _binding?.tvConnectActionBtn?.setBackgroundResource(
                            R.drawable.bg_btn_connect_interactive
                        )
                    }
                }
            )
        } else {
            binding.clSelectionExitInner.translationX = initStateFabSpacing
            binding.tvConnectActionBtn.setBackgroundResource(R.drawable.bg_btn_connect_interactive)
            binding.tvConnectActionBtn.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.inverse_on_surface)
            )
        }
    }

    private fun showDisconnectingTransition() {
        if (currentVpnState == ConnectionState.CONNECTING) {
            binding.clSelectionExitInner.animate()
                .translationX(initStateFabSpacing)
                .setDuration(animationDuration)
                .setInterpolator(DecelerateInterpolator())
                .start()

            binding.tvConnectActionBtn.startVectorAnimationWithEndCallback(
                R.color.on_surface,
                R.color.inverse_on_surface,
                startAnimationDrawableRes = R.drawable.av_cancel_to_connect,
                onAnimationEnd = {
                    _binding?.tvConnectActionBtn?.setBackgroundResource(
                        R.drawable.bg_btn_connect_interactive
                    )
                }
            )
        } else if (currentVpnState == ConnectionState.CONNECTED) {
            binding.clSelectionExitInner.animate().translationX(initStateFabSpacing)
                .setDuration(animationDuration)
                .setInterpolator(DecelerateInterpolator())
                .start()

            binding.tvConnectActionBtn.startVectorAnimationWithEndCallback(
                R.color.transparent,
                R.color.inverse_on_surface,
                startAnimationDrawableRes = R.drawable.av_stop_connect,
                onAnimationEnd = {
                    _binding?.tvConnectActionBtn?.setBackgroundResource(
                        R.drawable.bg_btn_connect_interactive
                    )
                }
            )
        } else {
            binding.clSelectionExitInner.translationX = connectingStateFabSpacing
            binding.tvConnectActionBtn.setBackgroundResource(R.drawable.bg_btn_connect_interactive)
            binding.tvConnectActionBtn.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.inverse_on_surface)
            )
        }
    }

    private fun showDisconnectedTransition() {
        if (currentVpnState == ConnectionState.DISCONNECTING) {
            // Disconnecting animation is already running
        } else {
            binding.clSelectionExitInner.translationX = initStateFabSpacing
            binding.tvConnectActionBtn.setBackgroundResource(R.drawable.bg_btn_connect_interactive)
            binding.tvConnectActionBtn.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.inverse_on_surface)
            )
        }
    }

    private fun showPrivateDnsErrorTransition() {
        if (currentVpnState == ConnectionState.DISCONNECTING) {
            // Disconnecting animation is already running
        } else {
            binding.clSelectionExitInner.translationX = initStateFabSpacing
            binding.tvConnectActionBtn.setBackgroundResource(R.drawable.bg_btn_connect_interactive)
            binding.tvConnectActionBtn.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.connectingDisabled)
            )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        key?.let {
            when (it) {
                BRIDGE_TYPE -> connectFragmentViewModel.updateConnectionLabel()
            }
        }
    }
}
