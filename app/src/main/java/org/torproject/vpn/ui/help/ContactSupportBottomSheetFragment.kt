package org.torproject.vpn.ui.help

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.torproject.onionmasq.logging.LogObservable
import org.torproject.vpn.R
import org.torproject.vpn.databinding.FragmentContactSupportBottomSheetBinding
import org.torproject.vpn.ui.help.model.ContactSupportFragmentModel

class ContactSupportBottomSheetFragment :
    BottomSheetDialogFragment(R.layout.fragment_contact_support_bottom_sheet),
    ContactSupportClickHandler {

    private val viewModel: ContactSupportFragmentModel by viewModels()
    override fun getTheme(): Int = R.style.bottom_sheet_dialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentContactSupportBottomSheetBinding.bind(view)
        binding.handler = this
        (dialog as? BottomSheetDialog)?.behavior?.let { behavior ->
            behavior.skipCollapsed = true
        }
        viewModel.load()
        viewModel.emailActivityInfo.observe(viewLifecycleOwner) { info ->
            if (info != null) {
                binding.ivAppImageEmail.setImageDrawable(
                    viewModel.getDrawable(info, R.drawable.ic_dummy_app)
                )
            } else {
                context?.let {
                    binding.ivAppImageEmail.setImageDrawable(
                        ContextCompat.getDrawable(it, R.drawable.ic_dummy_app)
                    )
                }
            }
        }
        viewModel.telegramActivityInfo.observe(viewLifecycleOwner) { info ->
            if (info != null) {
                binding.ivAppImageTelegram.setImageDrawable(
                    viewModel.getDrawable(info, R.drawable.ic_telegram_default)
                )
                binding.tvAppTitleTelegram.text = viewModel.getText(info)
                viewModel.setCustomTelegramURLScheme()
            } else {
                context?.let {
                    binding.ivAppImageTelegram.setImageDrawable(
                        ContextCompat.getDrawable(it, R.drawable.ic_telegram_default)
                    )
                    binding.tvAppTitleTelegram.text = ContextCompat.getString(it, R.string.telegram)
                }
            }
        }
        viewModel.whatsAppActivityInfo.observe(viewLifecycleOwner) { info ->
            if (info != null) {
                binding.ivAppImageWhatsApp.setImageDrawable(
                    viewModel.getDrawable(info, R.drawable.ic_whatsapp_default)
                )
                binding.tvAppTitleWhatsApp.text = viewModel.getText(info)
                viewModel.setCustomWhatsappURLScheme()
            } else {
                context?.let {
                    binding.ivAppImageWhatsApp.setImageDrawable(
                        ContextCompat.getDrawable(it, R.drawable.ic_whatsapp_default)
                    )
                    binding.tvAppTitleWhatsApp.text = ContextCompat.getString(it, R.string.whatsapp)
                }
            }
        }
        viewModel.signalActivityInfo.observe(viewLifecycleOwner) { info ->
            if (info != null) {
                binding.ivAppImageSignal.setImageDrawable(
                    viewModel.getDrawable(info, R.drawable.ic_signal_default)
                )
                binding.tvAppTitleSignal.text = viewModel.getText(info)
                viewModel.setCustomSignalURLScheme()
            } else {
                context?.let {
                    binding.ivAppImageSignal.setImageDrawable(
                        ContextCompat.getDrawable(it, R.drawable.ic_signal_default)
                    )
                    binding.tvAppTitleSignal.text = ContextCompat.getString(it, R.string.signal)
                }
            }
        }
    }

    override fun onWhatsappClicked(v: View) {
        startActivity(viewModel.whatsappIntent)
    }

    override fun onSignalClicked(v: View) {
        startActivity(viewModel.signalIntent)
    }

    override fun onTelegramClicked(v: View) {
        startActivity(viewModel.telegramIntent)
    }

    override fun onEmailClicked(v: View) {
        try {
            startActivity(viewModel.emailIntent)
        } catch (e: ActivityNotFoundException) {
            LogObservable.getInstance().addLog("No email client found: ${e.message}")
            Toast.makeText(v.context, R.string.error_no_email_client, Toast.LENGTH_LONG).show()
        }
    }
}
