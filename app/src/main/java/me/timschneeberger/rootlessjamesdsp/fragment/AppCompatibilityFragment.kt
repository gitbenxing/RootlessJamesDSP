package me.timschneeberger.rootlessjamesdsp.fragment

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import me.timschneeberger.rootlessjamesdsp.MainApplication
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.activity.MainActivity
import me.timschneeberger.rootlessjamesdsp.databinding.FragmentAppCompatibilityBinding
import me.timschneeberger.rootlessjamesdsp.model.room.AppBlocklistViewModel
import me.timschneeberger.rootlessjamesdsp.model.room.AppBlocklistViewModelFactory
import me.timschneeberger.rootlessjamesdsp.model.room.BlockedApp
import me.timschneeberger.rootlessjamesdsp.service.RootlessAudioProcessorService
import me.timschneeberger.rootlessjamesdsp.utils.Constants
import me.timschneeberger.rootlessjamesdsp.utils.ContextExtensions.getAppIcon
import me.timschneeberger.rootlessjamesdsp.utils.ContextExtensions.getAppNameFromUidSafe
import me.timschneeberger.rootlessjamesdsp.utils.ContextExtensions.getPackageNameFromUid
import me.timschneeberger.rootlessjamesdsp.utils.SystemServices
import me.timschneeberger.rootlessjamesdsp.utils.getParcelableAs
import timber.log.Timber
import java.util.*
import kotlin.concurrent.schedule

class AppCompatibilityFragment : Fragment() {
    private lateinit var binding: FragmentAppCompatibilityBinding
    private val viewModel: AppBlocklistViewModel by viewModels {
        AppBlocklistViewModelFactory((requireActivity().application as MainApplication).blockedAppRepository)
    }

    private val knownQuirks = mapOf(
        arrayOf(
            "com.google.android.youtube",
            "com.vanced.android.youtube",
            "app.revanced.android.youtube"
        ) to R.string.app_compat_quirk_voice_search
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        SystemServices.get(requireContext(), NotificationManager::class.java)
            .cancel(Constants.NOTIFICATION_ID_APP_INCOMPATIBILITY)

        val args = requireArguments()
        val projectIntent = args.getParcelableAs<Intent>(BUNDLE_MEDIA_PROJECTION)
        val internalCall = args.getBoolean(BUNDLE_INTERNAL_CALL, false)
        val appUid = args.getInt(BUNDLE_APP_UID, -1)
        val appPackage = requireContext().getPackageNameFromUid(appUid) ?: getString(R.string.app_compat_unknown_pkg_name)
        val appName = requireContext().getAppNameFromUidSafe(appUid)
        val appIcon = requireContext().getAppIcon(appPackage) ?: requireContext().getAppIcon("android")

        binding = FragmentAppCompatibilityBinding.inflate(layoutInflater, container, false)

        binding.icon.setImageDrawable(appIcon)
        binding.appName.text = appName
        binding.packageName.text = appPackage

        val hint = findAppHint(appPackage)
        binding.hint.isVisible = hint != null
        hint?.let {
            binding.hint.text = getString(it)
        }

        binding.appCard.setOnClickListener {
            val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(appPackage)
            launchIntent?.let {
                startActivity(it)
            }
        }

        binding.retry.setOnClickListener {
            binding.exclude.isEnabled = false
            binding.retry.isEnabled = false

            Timber.d("Requesting retry")

            projectIntent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    RootlessAudioProcessorService.start(requireContext(), it)
            }

            Timer("Close", false).schedule(300L) {
                requireActivity().finish()
                if(internalCall)
                    startActivity(Intent(requireContext(), MainActivity::class.java))
            }
        }

        binding.exclude.setOnClickListener {
            binding.exclude.isEnabled = false
            binding.retry.isEnabled = false

            Timber.d("Requesting exclude of $appPackage")
            viewModel.insert(BlockedApp(appUid, appPackage, appName))

            Timer("Reboot", false).schedule(100L) {
                projectIntent?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        RootlessAudioProcessorService.start(requireContext(), it)
                }

                Timer("Close", false).schedule(300L) {
                    requireActivity().finish()
                    if(internalCall)
                        startActivity(Intent(requireContext(), MainActivity::class.java))
                }
            }
        }

        return binding.root
    }

    @StringRes
    private fun findAppHint(pkg: String): Int? {
        for((packages, hint) in knownQuirks) {
            if(packages.contains(pkg))
                return hint
        }
        return null
    }

    companion object {
        private const val BUNDLE_APP_UID = "appUid"
        private const val BUNDLE_MEDIA_PROJECTION = "mediaProjection"
        private const val BUNDLE_INTERNAL_CALL = "internalCall"

        fun newInstance(uid: Int, mediaProjectionIntent: Intent?, internalCall: Boolean): AppCompatibilityFragment {
            val fragment = AppCompatibilityFragment()
            val args = Bundle()
            args.putInt(BUNDLE_APP_UID, uid)
            args.putBoolean(BUNDLE_INTERNAL_CALL, internalCall)
            args.putParcelable(BUNDLE_MEDIA_PROJECTION, mediaProjectionIntent)
            fragment.arguments = args
            return fragment
        }
    }
}