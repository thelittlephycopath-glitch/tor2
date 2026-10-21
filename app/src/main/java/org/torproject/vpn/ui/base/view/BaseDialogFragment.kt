package org.torproject.vpn.ui.base.view

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.view.View.GONE
import android.view.Window
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import org.torproject.onionmasq.logging.LogObservable
import org.torproject.vpn.R
import org.torproject.vpn.databinding.FragmentDialogBinding
import org.torproject.vpn.utils.PreferenceHelper

class BaseDialogFragment : DialogFragment(R.layout.fragment_dialog) {

    companion object {
        const val VIEW_TYPE: String = "viewType"

        const val TYPE_ENABLE_VERBOSE_LOGS = 2
        const val TYPE_DISABLE_VERBOSE_LOGS = 3

        fun createEnableVerboseLogsDialog(
            enabled: Boolean,
            onCancelListener: OnCancelInterface
        ): BaseDialogFragment {
            val fragment = BaseDialogFragment()
            val args = Bundle()
            args.putInt(
                VIEW_TYPE,
                if (enabled) {
                    TYPE_ENABLE_VERBOSE_LOGS
                } else {
                    TYPE_DISABLE_VERBOSE_LOGS
                }
            )
            fragment.arguments = args
            fragment.onCancelListener = onCancelListener
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = FragmentDialogBinding.inflate(layoutInflater)

        bind(requireArguments().getInt(VIEW_TYPE), binding)
        val builder = AlertDialog.Builder(requireActivity())
        builder.setView(binding.root)
        val dialog = builder.create()
        dialog.window?.apply {
            setBackgroundDrawable(TRANSPARENT.toDrawable())
            requestFeature(Window.FEATURE_NO_TITLE)
        }

        return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCancelListener?.onCancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        onCancelListener = null
    }

    interface OnCancelInterface {
        fun onCancel()
    }
    var onCancelListener: OnCancelInterface? = null

    private fun bind(type: Int, binding: FragmentDialogBinding) {
        binding.tvActionCancel.setOnClickListener { _ ->
            dismiss()
            onCancelListener?.onCancel()
        }
        binding.tvAction.setText(R.string.action_refresh)

        when (type) {
            TYPE_ENABLE_VERBOSE_LOGS -> {
                binding.ivHeader.visibility = GONE
                binding.tvDescription.setText(R.string.enable_verbose_logs_description)
                binding.tvHeader.setText(R.string.enable_verbose_logs)
                binding.tvAction.setText(R.string.enable)
                binding.tvAction.setOnClickListener { _ ->
                    LogObservable.getInstance().setVerboseLogs(true)
                    PreferenceHelper(binding.root.context).verboseLogging = true
                    dismiss()
                }
            }

            TYPE_DISABLE_VERBOSE_LOGS -> {
                binding.ivHeader.visibility = GONE
                binding.tvDescription.setText(R.string.disable_verbose_logs_description)
                binding.tvHeader.setText(R.string.disable_verbose_logs)
                binding.tvAction.setText(R.string.disable)
                binding.tvAction.setOnClickListener { _ ->
                    LogObservable.getInstance().setVerboseLogs(false)
                    LogObservable.getInstance().clearLogs()
                    PreferenceHelper(binding.root.context).verboseLogging = false
                    dismiss()
                }
            }
            else -> {}
        }
    }
}
