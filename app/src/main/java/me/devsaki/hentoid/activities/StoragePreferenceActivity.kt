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
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.ActivityPrefsStorageBinding
import me.devsaki.hentoid.databinding.IncludePrefsStorageVolumeBinding
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.preferences.DownloadStrategyDialogFragment
import me.devsaki.hentoid.fragments.preferences.LibRefreshDialogFragment
import me.devsaki.hentoid.enums.StorageLocation
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
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class StoragePreferenceActivity : BaseActivity(), DownloadStrategyDialogFragment.Parent {

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
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onStop() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Make sure display is up to date even after leaving the app in the background during import
        refreshDisplay()
    }

    private fun bindUI() {
        binding?.apply {
            toolbar.setNavigationOnClickListener { finish() }

            addPrimary1.setOnClickListener {
                if (PrimaryImportWorker.isRunning(baseContext)) ToastHelper.toast(R.string.pref_import_running)
                else importLocation(StorageLocation.PRIMARY_1)
            }
            addPrimary2.setOnClickListener {
                if (PrimaryImportWorker.isRunning(baseContext)) ToastHelper.toast(R.string.pref_import_running)
                else importLocation(StorageLocation.PRIMARY_2)
            }
            addExternal.setOnClickListener {
                if (ExternalImportWorker.isRunning(baseContext)) ToastHelper.toast(R.string.pref_import_running)
                else importLocation(StorageLocation.EXTERNAL)
            }

            alertLowPanel.setOnClickListener {
                MaterialAlertDialogBuilder(this@StoragePreferenceActivity)
                    .setCancelable(true)
                    .setTitle(R.string.pref_memory_alert_title)
                    .setSingleChoiceItems(
                        R.array.pref_memory_alert_entries,
                        Helper.getPrefsIndex(
                            resources,
                            R.array.pref_memory_alert_values,
                            Preferences.getMemoryAlertThreshold().toString()
                        )
                    ) { dialog, which ->
                        val array = resources.getStringArray(R.array.pref_memory_alert_values)
                        Preferences.setMemoryAlertThreshold(array[which].toInt())
                        refreshDisplay()
                        dialog.dismiss()
                    }
                    .create()
                    .show()
            }

            strategyPanel.setOnClickListener {
                DownloadStrategyDialogFragment.invoke(supportFragmentManager)
            }

            statsPanel.setOnClickListener {
                MemoryUsageDialogFragment.invoke(supportFragmentManager)
            }
        }
    }

    private fun refreshDisplay() {
        binding?.apply {
            browseModeWarning.isVisible = Preferences.isBrowserMode()
            browseModeImg.isVisible = Preferences.isBrowserMode()

            primaryVolume1.isVisible = Preferences.getStorageUri(StorageLocation.PRIMARY_1).isNotEmpty()
            if (primaryVolume1.isVisible) {
                if (null == binding1) binding1 =
                    IncludePrefsStorageVolumeBinding.bind(primaryVolume1)
                bindLocation(
                    binding1,
                    StorageLocation.PRIMARY_1,
                    Preferences.getStorageUri(StorageLocation.PRIMARY_1)
                )
            }
            addPrimary1.isVisible = !primaryVolume1.isVisible

            primaryVolume2.isVisible = Preferences.getStorageUri(StorageLocation.PRIMARY_2).isNotEmpty()
            if (primaryVolume2.isVisible) {
                if (null == binding2) binding2 =
                    IncludePrefsStorageVolumeBinding.bind(primaryVolume2)
                bindLocation(
                    binding2,
                    StorageLocation.PRIMARY_2,
                    Preferences.getStorageUri(StorageLocation.PRIMARY_2)
                )
            }
            addPrimary2.isVisible = primaryVolume1.isVisible && !primaryVolume2.isVisible

            alertLowPanel.isVisible = (primaryVolume1.isVisible || primaryVolume2.isVisible)
            var textArray = resources.getStringArray(R.array.pref_memory_alert_entries)
            alertDesc.text = textArray[Helper.getPrefsIndex(
                resources,
                R.array.pref_memory_alert_values,
                Preferences.getMemoryAlertThreshold().toString()
            )]

            strategyPanel.isVisible = (primaryVolume1.isVisible && primaryVolume2.isVisible)
            textArray = resources.getStringArray(R.array.pref_storage_strategy_name)
            strategyTitle.text = resources.getString(
                R.string.storage_strategy_title,
                textArray[Preferences.getStorageDownloadStrategy()]
            )
            textArray = resources.getStringArray(R.array.pref_storage_strategy_desc)
            strategyDesc.text = String.format(
                textArray[Preferences.getStorageDownloadStrategy()],
                Preferences.getStorageSwitchThresholdPc()
            )

            externalVolume.isVisible = Preferences.getExternalLibraryUri().isNotEmpty()
            if (externalVolume.isVisible) {
                if (null == bindingExt) bindingExt =
                    IncludePrefsStorageVolumeBinding.bind(externalVolume)
                bindLocation(
                    bindingExt,
                    StorageLocation.EXTERNAL,
                    Preferences.getExternalLibraryUri()
                )
            }
            addExternal.isVisible = Preferences.getExternalLibraryUri().isEmpty()
        }
    }

    private fun bindLocation(
        binding: IncludePrefsStorageVolumeBinding?,
        location: StorageLocation,
        uriStr: String
    ) {
        binding?.apply {
            when (location) {
                StorageLocation.PRIMARY_1 -> number.text = "1"
                StorageLocation.PRIMARY_2 -> number.text = "2"
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

    private fun importLocation(location: StorageLocation) {
        LibRefreshDialogFragment.invoke(
            supportFragmentManager,
            showOptions = false,
            chooseFolder = true,
            location
        )
    }

    private fun onActionClick(location: StorageLocation, anchor: View) {
        val powerMenuBuilder = PowerMenu.Builder(this)
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.storage_action_replace),
                    R.drawable.ic_folder_edit,
                    false,
                    2
                )
            )
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.refresh_title),
                    R.drawable.ic_action_refresh,
                    false,
                    0
                )
            )
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.storage_action_remove),
                    R.drawable.ic_action_remove,
                    false,
                    1
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
                2 -> { // Replace with
                    if (PrimaryImportWorker.isRunning(baseContext)) {
                        ToastHelper.toast(R.string.pref_import_running)
                    } else {
                        LibRefreshDialogFragment.invoke(
                            supportFragmentManager,
                            showOptions = false,
                            chooseFolder = true,
                            location
                        )
                    }
                }

                0 -> { // Refresh
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

                else -> onDetachSelected(location)
            }
        }

        powerMenu.setIconColor(ContextCompat.getColor(this, R.color.white_opacity_87))
        powerMenu.showAsAnchorRightTop(anchor)
    }

    private fun onDetachSelected(location: StorageLocation) {
        if (StorageLocation.PRIMARY_1 == location
            && Preferences.getStorageUri(StorageLocation.PRIMARY_2).isNotBlank()
        ) {
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.storage_remove_ko1,
                BaseTransientBottomBar.LENGTH_LONG
            ).show()
        } else {
            confirmDetach(location)
        }
    }

    private fun confirmDetach(location: StorageLocation) {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_warning)
            .setCancelable(true)
            .setTitle(R.string.app_name)
            .setMessage(R.string.storage_remove_ask)
            .setPositiveButton(R.string.yes) { dialog1: DialogInterface, _: Int ->
                dialog1.dismiss()
                viewModel.detach(location)
                refreshDisplay()
                ToastHelper.toast(R.string.storage_remove_confirm)
            }
            .setNegativeButton(R.string.no) { dialog12: DialogInterface, _: Int -> dialog12.dismiss() }
            .create()
            .show()
    }

    override fun onStrategySelected() {
        refreshDisplay()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onImportEventComplete(event: ProcessEvent) {
        if (ProcessEvent.EventType.COMPLETE == event.eventType
            && event.logFile != null
            && (event.processId == R.id.import_external || event.processId == R.id.import_primary)
        ) {
            refreshDisplay()
            val snackbar =
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.task_done,
                    BaseTransientBottomBar.LENGTH_LONG
                )
            snackbar.setAction(R.string.read_log) { FileHelper.openFile(this, event.logFile) }
            snackbar.show()
        }
    }
}