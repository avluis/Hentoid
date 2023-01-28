package me.devsaki.hentoid.activities

import android.content.DialogInterface
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.ActivityPrefsStorageBinding
import me.devsaki.hentoid.databinding.IncludePrefsStorageVolumeBinding
import me.devsaki.hentoid.fragments.preferences.LibRefreshDialogFragment
import me.devsaki.hentoid.fragments.preferences.MemoryUsageDialogFragment
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.viewmodels.PreferencesViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.workers.ExternalImportWorker
import me.devsaki.hentoid.workers.PrimaryImportWorker

class StoragePreferenceActivity : BaseActivity() {

    // == Communication
    private lateinit var viewModel: PreferencesViewModel

    // == UI
    private var binding: ActivityPrefsStorageBinding? = null
    private var binding1: IncludePrefsStorageVolumeBinding? = null
    private var binding2: IncludePrefsStorageVolumeBinding? = null
    private var bindingExt: IncludePrefsStorageVolumeBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)

        binding = ActivityPrefsStorageBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        val vmFactory = ViewModelFactory(application)
        viewModel = ViewModelProvider(this, vmFactory)[PreferencesViewModel::class.java]

        bindUI()
        init()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    private fun bindUI() {
        binding?.apply {
            addPrimary1.setOnClickListener {
                if (PrimaryImportWorker.isRunning(baseContext)) ToastHelper.toast(R.string.pref_import_running)
                else importLocation(LibRefreshDialogFragment.Location.PRIMARY_1)
            }
            addPrimary2.setOnClickListener {
                if (PrimaryImportWorker.isRunning(baseContext)) ToastHelper.toast(R.string.pref_import_running)
                else importLocation(LibRefreshDialogFragment.Location.PRIMARY_2)
            }
            addExternal.setOnClickListener {
                if (ExternalImportWorker.isRunning(baseContext)) ToastHelper.toast(R.string.pref_import_running)
                else importLocation(LibRefreshDialogFragment.Location.EXTERNAL)
            }
        }
    }

    private fun init() {
        binding?.apply {
            toolbar.setNavigationOnClickListener { finish() }

            browseModeWarning.isVisible = Preferences.isBrowserMode()
            browseModeImg.isVisible = Preferences.isBrowserMode()

            primaryVolume1.isVisible = Preferences.getStorageUri().isNotEmpty()
            if (primaryVolume1.isVisible) {
                binding1 = IncludePrefsStorageVolumeBinding.bind(primaryVolume1)
                bindLocation(
                    binding1,
                    LibRefreshDialogFragment.Location.PRIMARY_1,
                    Preferences.getStorageUri()
                )
            }
            addPrimary1.isVisible = Preferences.getStorageUri().isEmpty()

            primaryVolume2.isVisible = Preferences.getStorageUri2().isNotEmpty()
            if (primaryVolume2.isVisible) {
                binding2 = IncludePrefsStorageVolumeBinding.bind(primaryVolume2)
                bindLocation(
                    binding2,
                    LibRefreshDialogFragment.Location.PRIMARY_2,
                    Preferences.getStorageUri2()
                )
            }
            addPrimary2.isVisible = Preferences.getStorageUri2().isEmpty()

            externalVolume.isVisible = Preferences.getExternalLibraryUri().isNotEmpty()
            if (externalVolume.isVisible) {
                bindingExt =
                    IncludePrefsStorageVolumeBinding.bind(externalVolume)
                bindLocation(
                    bindingExt,
                    LibRefreshDialogFragment.Location.EXTERNAL,
                    Preferences.getExternalLibraryUri()
                )
            }
            addExternal.isVisible = Preferences.getExternalLibraryUri().isEmpty()
        }
    }

    private fun bindLocation(
        binding: IncludePrefsStorageVolumeBinding?,
        location: LibRefreshDialogFragment.Location,
        uriStr: String
    ) {
        binding?.apply {
            when (location) {
                LibRefreshDialogFragment.Location.PRIMARY_1 -> number.text = "1"
                LibRefreshDialogFragment.Location.PRIMARY_2 -> number.text = "2"
                else -> number.text = " "
            }
            val uri = Uri.parse(uriStr)
            path.text = FileHelper.getFullPathFromTreeUri(baseContext, uri)

            val rootFolder = FileHelper.getDocumentFromTreeUriString(baseContext, uriStr)
            if (rootFolder != null) {
                val memUsage = FileHelper.MemoryUsageFigures(baseContext, rootFolder)
                val locationFreeBytes = memUsage.getfreeUsageBytes()
                val locationTotalBytes = memUsage.totalSpaceBytes
                statsTxt.text = resources.getString(
                    R.string.location_storage,
                    FileHelper.formatHumanReadableSize(locationFreeBytes, resources),
                    locationFreeBytes * 100 / locationTotalBytes
                )
                statsGraph.apply {
                    isIndeterminate = false
                    progress = (locationFreeBytes * 100 / locationTotalBytes).toInt()
                }
                actionsBtn.setOnClickListener { onActionClick(location, actionsBtn) }
            }
        }
    }

    private fun importLocation(location: LibRefreshDialogFragment.Location) {
        LibRefreshDialogFragment.invoke(
            supportFragmentManager,
            showOptions = false,
            chooseFolder = true,
            location
        )
    }

    private fun onActionClick(location: LibRefreshDialogFragment.Location, anchor: View) {
        val powerMenuBuilder = PowerMenu.Builder(this)
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.storage_stats),
                    R.drawable.ic_stats,
                    false,
                    0
                )
            )
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.refresh_title),
                    R.drawable.ic_replace,
                    false,
                    1
                )
            )
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.remove_generic),
                    R.drawable.ic_action_remove,
                    false,
                    3
                )
            )
            .setAnimation(MenuAnimation.SHOWUP_TOP_RIGHT)
            .setMenuRadius(10f)
            .setLifecycleOwner(this)
            .setTextColor(ContextCompat.getColor(this, R.color.white_opacity_87))
            .setTextTypeface(Typeface.DEFAULT)
            .setMenuColor(ContextCompat.getColor(this, R.color.dark_gray))
            .setWidth(resources.getDimension(R.dimen.popup_menu_width).toInt())
            .setTextSize(Helper.dimensAsDp(this, R.dimen.text_subtitle_1))
            .setAutoDismiss(true)

        val powerMenu = powerMenuBuilder.build()

        powerMenu.setOnMenuItemClickListener { _, item ->
            when (item.tag) {
                0 -> { // Stats
                    // TODO adapt dialog
                    MemoryUsageDialogFragment.invoke(supportFragmentManager)
                }

                1 -> { // Refresh
                    if (PrimaryImportWorker.isRunning(baseContext)) {
                        ToastHelper.toast(R.string.pref_import_running)
                    } else {
                        LibRefreshDialogFragment.invoke(
                            supportFragmentManager,
                            showOptions = true,
                            chooseFolder = false,
                            location
                        )
                    }
                }

                else -> { // Remove
                    MaterialAlertDialogBuilder(
                        baseContext,
                        ThemeHelper.getIdForCurrentTheme(baseContext, R.style.Theme_Light_Dialog)
                    )
                        .setIcon(R.drawable.ic_warning)
                        .setCancelable(true)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.storage_remove_ask)
                        .setPositiveButton(R.string.yes) { dialog1: DialogInterface, _: Int ->
                            dialog1.dismiss()
                            viewModel.remove(location)
                            ToastHelper.toast(R.string.storage_remove_confirm)
                        }
                        .setNegativeButton(R.string.no) { dialog12: DialogInterface, _: Int -> dialog12.dismiss() }
                        .create()
                        .show()
                }
            }
        }

        powerMenu.setIconColor(ContextCompat.getColor(this, R.color.white_opacity_87))
        powerMenu.showAsAnchorRightTop(anchor)
    }
}