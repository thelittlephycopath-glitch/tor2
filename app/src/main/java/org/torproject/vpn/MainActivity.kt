package org.torproject.vpn

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.torproject.vpn.databinding.ActivityMainBinding
import org.torproject.vpn.utils.applyInsetsToViewPadding
import org.torproject.vpn.utils.navigateSafe

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainActivityViewModel by viewModels()
    lateinit var navController: NavController
    companion object {
        const val KEY_ACTION = "ACTION"
        val ACTION_REQUEST_VPN_PERMISSON =
            MainActivity::class.java.simpleName + ".ACTION_REQUEST_VPN_PERMISSON"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val navView: BottomNavigationView = binding.navView
        val navHostFragment = supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment_activity_main
        ) as NavHostFragment
        navController = navHostFragment.navController

        applyInsetsToViewPadding(navView, true, false, true, true)

        navView.setupWithNavController(navController)

        val appBarConfig = AppBarConfiguration(
            setOf(
                R.id.navigation_connect,
                R.id.navigation_help,
                R.id.configure_fragment
            )
        )
        navController.addOnDestinationChangedListener { _, destination: NavDestination, _ ->
            if (appBarConfig.topLevelDestinations.contains(destination.id)) {
                showBottomNavigationView(navView)
            } else {
                hideBottomNavigationView(navView)
            }
        }

        handleIntentAction()

        binding.navView.addOnLayoutChangeListener { view, _, top, _, bottom, _, _, _, _ ->
            viewModel.setNavigationBarHeight(bottom - top)
            // in case the bottom navigation bar should be hidden, make sure that the
            // bottom navigation bar is always moved completely out of the screen after rotation
            if (!appBarConfig.topLevelDestinations.contains(navController.currentDestination?.id)) {
                hideBottomNavigationView(view as BottomNavigationView, false)
            }
        }
    }

    private fun handleIntentAction() {
        intent?.let { intent ->
            when (intent.action) {
                ACTION_REQUEST_VPN_PERMISSON -> {
                    val bundle = Bundle()
                    bundle.putString(KEY_ACTION, ACTION_REQUEST_VPN_PERMISSON)
                    navController.navigateSafe(R.id.navigation_connect, bundle)
                }

                else -> {}
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentAction()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun hideBottomNavigationView(view: BottomNavigationView, animate: Boolean = true) {
        view.animate().cancel()
        val duration = if (animate) 260L else 0L
        view.animate()
            .translationY(view.height.toFloat())
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = true
        } else {
            @Suppress("DEPRECATION")
            window.navigationBarColor = 0x80000000.toInt()
        }
    }

    private fun showBottomNavigationView(view: BottomNavigationView, animate: Boolean = true) {
        view.animate().cancel()
        val duration = if (animate) 260L else 0L
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        } else {
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
