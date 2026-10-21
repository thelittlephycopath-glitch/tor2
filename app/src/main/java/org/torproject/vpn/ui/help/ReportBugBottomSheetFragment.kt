package org.torproject.vpn.ui.help

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.torproject.vpn.R
import org.torproject.vpn.databinding.FragmentReportBugBottomSheetBinding

class ReportBugBottomSheetFragment :
    BottomSheetDialogFragment(R.layout.fragment_report_bug_bottom_sheet),
    ReportBugClickHandler {

    override fun getTheme(): Int = R.style.bottom_sheet_dialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentReportBugBottomSheetBinding.bind(view)
        binding.handler = this
        (dialog as? BottomSheetDialog)?.behavior?.let { behavior ->
            behavior.skipCollapsed = true
        }
    }

    override fun onTorForumClicked(v: View) {
        val webpage: Uri = "https://forum.torproject.org".toUri()
        val intent = Intent(Intent.ACTION_VIEW, webpage)
        this.context?.let {
            if (intent.resolveActivity(it.packageManager) != null) {
                startActivity(intent)
            }
        }
    }

    override fun onGitlabClicked(v: View) {
        val webpage: Uri = "https://gitlab.torproject.org/tpo/applications/vpn/-/issues".toUri()
        val intent = Intent(Intent.ACTION_VIEW, webpage)
        this.context?.let {
            if (intent.resolveActivity(it.packageManager) != null) {
                startActivity(intent)
            }
        }
    }
}
