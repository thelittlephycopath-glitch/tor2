package org.torproject.vpn.ui.help

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import org.torproject.vpn.R
import org.torproject.vpn.databinding.FragmentOfflineHelpBinding
import org.torproject.vpn.utils.applyInsetsToViewPadding
import org.torproject.vpn.utils.setStatusBarColor

class OfflineHelpFragment : Fragment(R.layout.fragment_offline_help) {
    companion object {
        val TAG: String = OfflineHelpFragment::class.java.simpleName
        const val HELP_PAGE_BUG_REPORT = 1
        const val LICENSES = 2
        const val PRIVACY_POLICY = 3
        const val HELP_PAGE_PRIVATE_DNS = 4
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentOfflineHelpBinding.bind(view)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        var fileName = "offline/tor-vpn/index.html"
        var title = view.context.resources.getString(R.string.help)
        arguments?.let {
            val arguments = OfflineHelpFragmentArgs.fromBundle(it)
            when (arguments.argHelpPageID) {
                HELP_PAGE_BUG_REPORT -> {
                    fileName = "offline/tor-vpn/encountering-issues/feedback-and-support/index.html"
                }
                LICENSES -> {
                    fileName = "OPEN_SOURCE_LICENSES.md.html"
                    title = view.context.resources.getString(R.string.open_source_licenses)
                }
                PRIVACY_POLICY -> {
                    fileName = "PRIVACY_POLICY.md.html"
                    title = view.context.resources.getString(R.string.privacy_policy)
                }
                HELP_PAGE_PRIVATE_DNS -> {
                    fileName =
                        "offline/tor-vpn/encountering-issues/troubleshooting/index.html#tor-vpn-private-dns"
                }
                else -> {
                    // defaults are already set
                }
            }
            binding.toolbar.title = title
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain("localhost")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(view.context))
            .build()
        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (binding.wvHelp.canGoBack()) {
                    binding.wvHelp.goBack()
                }
                isEnabled = binding.wvHelp.canGoBack()
            }
        }
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, backCallback)

        binding.wvHelp.webViewClient = LocalContentWebViewClient(assetLoader, backCallback)
        binding.wvHelp.loadUrl("file:///android_asset/help/$fileName")
        binding.wvHelp.setBackgroundColor(
            ResourcesCompat.getColor(view.resources, R.color.surface, null)
        )

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val params = binding.wvHelp.layoutParams as (FrameLayout.LayoutParams)
            params.bottomMargin = systemBarInsets.bottom

            binding.wvHelpContainer.setPadding(
                cutoutInsets.left,
                0,
                cutoutInsets.right,
                0
            )
            return@setOnApplyWindowInsetsListener windowInsets
        }
        setStatusBarColor(binding.appBar)
        applyInsetsToViewPadding(
            binding.appBar,
            left = true,
            top = true,
            right = true,
            bottom = false
        )
        applyInsetsToViewPadding(
            binding.wvHelpContainer,
            left = true,
            top = false,
            right = true,
            bottom = false
        )
    }

    private inner class LocalContentWebViewClient(
        private val assetLoader: WebViewAssetLoader,
        private val backCallback: OnBackPressedCallback
    ) : WebViewClientCompat() {
        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            backCallback.isEnabled = view.canGoBack()
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            // Check if the URL is external, open in separate browser
            if (!url.startsWith("file:///android_asset/help/") && url.startsWith("https://")) {
                val intent = Intent(Intent.ACTION_VIEW, request.url)
                startActivity(intent)
                return true
            }

            // Fix directory references before loading
            val fixedUrl = appendIndexIfDirectory(request.url).toString()
            if (fixedUrl != url) {
                view.loadUrl(fixedUrl)
                return true
            }

            // Allow normal loading
            return false
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val originalUri = request.url
            val fixedUri = appendIndexIfDirectory(originalUri)
            return assetLoader.shouldInterceptRequest(fixedUri)
        }

        // The offline help pages contain references to directories instead of index.html files.
        // This method appends "index.html" in case the uri seems to be a directory so that linked
        // pages are loaded correctly.
        private fun appendIndexIfDirectory(uri: Uri): Uri {
            val path = uri.path ?: return uri
            val normalized = path.trimEnd('/')

            if (normalized.endsWith("index.html", ignoreCase = true)) {
                return uri
            }

            // Determine if the last path segment looks like a file (contains a dot)
            val lastSegment = uri.lastPathSegment
            val looksLikeFile = lastSegment?.contains('.') == true

            // If it looks like a file, don't append index.html
            if (looksLikeFile) {
                return uri
            }

            // Build new path by ensuring exactly one slash between normalized and index.html
            val newPath = "$normalized/index.html"

            return uri.buildUpon().path(newPath).build()
        }
    }
}
