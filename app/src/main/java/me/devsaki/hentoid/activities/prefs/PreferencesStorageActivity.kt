package me.devsaki.hentoid.activities.prefs

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import kotlinx.coroutines.launch
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.BaseActivity
import me.devsaki.hentoid.core.URL_GITHUB_WIKI_STORAGE
import me.devsaki.hentoid.core.startBrowserActivity
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.databinding.ActivityPrefsStorageBinding
import me.devsaki.hentoid.databinding.IncludePrefsStorageVolumeBinding
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.ProgressDialogFragment
import me.devsaki.hentoid.fragments.preferences.DownloadStrategyDialogFragment
import me.devsaki.hentoid.fragments.preferences.LibRefreshDialogFragment
import me.devsaki.hentoid.fragments.preferences.StorageUsageDialogFragment
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.applyTheme
import me.devsaki.hentoid.util.dimensAsDp
import me.devsaki.hentoid.util.file.MemoryUsageFigures
import me.devsaki.hentoid.util.file.formatHumanReadableSize
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getFullPathFromUri
import me.devsaki.hentoid.util.file.openFile
import me.devsaki.hentoid.util.getPathRoot
import me.devsaki.hentoid.util.getPrefsIndex
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.viewmodels.PreferencesViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.workers.ExternalImportWorker
import me.devsaki.hentoid.workers.PrimaryImportWorker
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class PreferencesStorageActivity : BaseActivity(), DownloadStrategyDialogFragment.Parent,
    LibRefreshDialogFragment.Parent {

    // == Communication
    private lateinit var viewModel: PreferencesViewModel

    // == UI
    private var binding: ActivityPrefsStorageBinding? = null
    private var binding1: IncludePrefsStorageVolumeBinding? = null
    private var binding2: IncludePrefsStorageVolumeBinding? = null
    private var bindingExt: IncludePrefsStorageVolumeBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()

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
            toolbar.setOnMenuItemClickListener { i -> onMenuItemSelected(i) }

            addPrimary1.setOnClickListener {
                if (PrimaryImportWorker.isRunning(baseContext)
                    || ExternalImportWorker.isRunning(baseContext)
                ) toast(R.string.pref_import_running)
                else importLocation(StorageLocation.PRIMARY_1)
            }
            addPrimary2.setOnClickListener {
                if (PrimaryImportWorker.isRunning(baseContext)
                    || ExternalImportWorker.isRunning(baseContext)
                ) toast(R.string.pref_import_running)
                else importLocation(StorageLocation.PRIMARY_2)
            }
            addExternal.setOnClickListener {
                if (PrimaryImportWorker.isRunning(baseContext)
                    || ExternalImportWorker.isRunning(baseContext)
                ) toast(R.string.pref_import_running)
                else importLocation(StorageLocation.EXTERNAL)
            }

            alertLowPanel.setOnClickListener {
                MaterialAlertDialogBuilder(this@PreferencesStorageActivity)
                    .setCancelable(true)
                    .setTitle(R.string.pref_memory_alert_title)
                    .setSingleChoiceItems(
                        R.array.pref_memory_alert_entries,
                        getPrefsIndex(
                            resources,
                            R.array.pref_memory_alert_values,
                            Settings.memoryAlertThreshold.toString()
                        )
                    ) { dialog, which ->
                        val array = resources.getStringArray(R.array.pref_memory_alert_values)
                        Settings.memoryAlertThreshold = array[which].toInt()
                        refreshDisplay()
                        dialog.dismiss()
                    }
                    .create()
                    .show()
            }

            strategyPanel.setOnClickListener {
                DownloadStrategyDialogFragment.invoke(this@PreferencesStorageActivity)
            }

            statsPanel.setOnClickListener {
                StorageUsageDialogFragment.invoke(this@PreferencesStorageActivity)
            }
        }
    }

    private fun refreshDisplay() {
        binding?.apply {
            browseModeWarning.isVisible = Settings.isBrowserMode
            browseModeImg.isVisible = Settings.isBrowserMode

            primaryVolume1.isVisible =
                Settings.getStorageUri(StorageLocation.PRIMARY_1).isNotEmpty()
            if (primaryVolume1.isVisible) {
                if (null == binding1) binding1 =
                    IncludePrefsStorageVolumeBinding.bind(primaryVolume1)
                bindLocation(
                    binding1,
                    StorageLocation.PRIMARY_1,
                    Settings.getStorageUri(StorageLocation.PRIMARY_1)
                )
            }
            addPrimary1.isVisible = !primaryVolume1.isVisible

            primaryVolume2.isVisible =
                Settings.getStorageUri(StorageLocation.PRIMARY_2).isNotEmpty()
            if (primaryVolume2.isVisible) {
                if (null == binding2) binding2 =
                    IncludePrefsStorageVolumeBinding.bind(primaryVolume2)
                bindLocation(
                    binding2,
                    StorageLocation.PRIMARY_2,
                    Settings.getStorageUri(StorageLocation.PRIMARY_2)
                )
            }
            addPrimary2.isVisible = primaryVolume1.isVisible && !primaryVolume2.isVisible

            alertLowPanel.isVisible = (primaryVolume1.isVisible || primaryVolume2.isVisible)
            var textArray = resources.getStringArray(R.array.pref_memory_alert_entries)
            alertDesc.text = textArray[getPrefsIndex(
                resources,
                R.array.pref_memory_alert_values,
                Settings.memoryAlertThreshold.toString()
            )]

            strategyPanel.isVisible = (primaryVolume1.isVisible && primaryVolume2.isVisible)
            textArray = resources.getStringArray(R.array.pref_storage_strategy_name)
            strategyTitle.text = resources.getString(
                R.string.storage_strategy_title,
                textArray[Settings.storageDownloadStrategy]
            )
            textArray = resources.getStringArray(R.array.pref_storage_strategy_desc)
            strategyDesc.text = String.format(
                textArray[Settings.storageDownloadStrategy],
                Settings.storageSwitchThresholdPc
            )

            externalVolume.isVisible = Settings.externalLibraryUri.isNotEmpty()
            if (externalVolume.isVisible) {
                if (null == bindingExt) bindingExt =
                    IncludePrefsStorageVolumeBinding.bind(externalVolume)
                bindLocation(
                    bindingExt,
                    StorageLocation.EXTERNAL,
                    Settings.externalLibraryUri
                )
            }
            addExternal.isVisible = Settings.externalLibraryUri.isEmpty()
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
            val uri = uriStr.toUri()
            path.text = getFullPathFromUri(baseContext, uri)

            val rootFolder = getDocumentFromTreeUriString(baseContext, uriStr)
            if (rootFolder != null) {
                val memUsage = MemoryUsageFigures(baseContext, rootFolder)
                val locationFreeBytes = memUsage.getfreeUsageBytes()
                val locationTotalBytes = memUsage.totalSpaceBytes
                statsTxt.text = resources.getString(
                    R.string.location_storage,
                    formatHumanReadableSize(locationFreeBytes, resources),
                    locationFreeBytes * 100 / locationTotalBytes
                )
                statsGraph.apply {
                    isIndeterminate = false
                    progress = (locationFreeBytes * 100 / locationTotalBytes).toInt()
                }
            }
            actionsBtn.setOnClickListener { onActionClick(location, actionsBtn) }
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
                    resources.getString(R.string.open_folder),
                    false,
                    R.drawable.ic_folder,
                    null,
                    null,
                    4
                )
            )
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.storage_action_change),
                    false,
                    R.drawable.ic_edit,
                    null,
                    null,
                    2
                )
            )
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.refresh_title),
                    false,
                    R.drawable.ic_action_refresh,
                    null,
                    null,
                    0
                )
            )
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.storage_action_remove),
                    false,
                    R.drawable.ic_action_remove,
                    null,
                    null,
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
            .setTextSize(dimensAsDp(this, R.dimen.text_subtitle_1))
            .setAutoDismiss(true)

        if (location == StorageLocation.PRIMARY_2)
            powerMenuBuilder.addItem(
                3,
                PowerMenuItem(
                    resources.getString(R.string.storage_action_merge_to_1),
                    false,
                    R.drawable.ic_action_merge,
                    null,
                    null,
                    3
                )
            )

        val powerMenu = powerMenuBuilder.build()

        powerMenu.setOnMenuItemClickListener { _, item ->
            when (item.tag) {
                2 -> { // Replace with
                    if (PrimaryImportWorker.isRunning(baseContext)) {
                        toast(R.string.pref_import_running)
                    } else {
                        importLocation(location)
                    }
                }

                0 -> { // Refresh
                    if (PrimaryImportWorker.isRunning(baseContext)) {
                        toast(R.string.pref_import_running)
                    } else {
                        LibRefreshDialogFragment.invoke(
                            supportFragmentManager,
                            showOptions = location != StorageLocation.EXTERNAL,
                            chooseFolder = false,
                            location
                        )
                    }
                }

                1 -> onDetachSelected(location)

                3 -> onMergeToPrimaryOne()

                4 -> { // Open folder
                    val folder =
                        getDocumentFromTreeUriString(
                            this,
                            Settings.getStorageUri(location)
                        )
                    if (folder != null) openFile(this, folder)
                }
            }
        }

        powerMenu.setIconColor(ContextCompat.getColor(this, R.color.white_opacity_87))
        powerMenu.showAsAnchorRightTop(anchor)
    }

    private fun onDetachSelected(location: StorageLocation) {
        if (StorageLocation.PRIMARY_1 == location
            && Settings.getStorageUri(StorageLocation.PRIMARY_2).isNotBlank()
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
                toast(R.string.storage_remove_complete)
            }
            .setNegativeButton(R.string.no) { dialog12: DialogInterface, _: Int -> dialog12.dismiss() }
            .create()
            .show()
    }

    // Check if merging is possible
    private fun onMergeToPrimaryOne() {
        val location2Usage: Long
        val nbBooks: Long
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            val location2Usages =
                dao.selectPrimaryMemoryUsagePerSource(getPathRoot(StorageLocation.PRIMARY_2))
            location2Usage = location2Usages.map { e -> e.value.second }.sum()
            nbBooks = dao.countAllInternalBooks(
                getPathRoot(StorageLocation.PRIMARY_2),
                false
            )
        } finally {
            dao.cleanup()
        }

        var location1free = -1L
        val root1 = Settings.getStorageUri(StorageLocation.PRIMARY_1)
        if (root1.isNotEmpty()) {
            val root1Folder = getDocumentFromTreeUriString(this, root1)
            if (root1Folder != null) {
                location1free = MemoryUsageFigures(this, root1Folder).getfreeUsageBytes()
            }
        }

        // Test if there are books to merge
        if (0L == nbBooks) {
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.storage_merge_ko2,
                BaseTransientBottomBar.LENGTH_LONG
            ).show()
            return
        }

        // Test if enough free space is availkable; take 2% margin
        if (location1free > location2Usage * 1.02) confirmMerge(nbBooks)
        else {
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.storage_merge_ko1,
                BaseTransientBottomBar.LENGTH_LONG
            ).show()
        }
    }

    private fun confirmMerge(nbBooks: Long) {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_warning)
            .setCancelable(true)
            .setTitle(R.string.app_name)
            .setMessage(resources.getString(R.string.storage_merge_ask, nbBooks))
            .setPositiveButton(R.string.yes) { dialog1: DialogInterface, _: Int ->
                val fragment = ProgressDialogFragment.invoke(
                    this,
                    resources.getString(R.string.storage_merge_progress),
                    R.plurals.book
                )
                dialog1.dismiss()
                lifecycleScope.launch {
                    viewModel.merge2to1(nbBooks.toInt())
                    refreshDisplay()
                    fragment.dismissAllowingStateLoss()
                    toast(R.string.storage_merge_complete)
                }
            }
            .setNegativeButton(R.string.no) { dialog12: DialogInterface, _: Int -> dialog12.dismiss() }
            .create()
            .show()
    }

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_help -> startBrowserActivity(URL_GITHUB_WIKI_STORAGE)
            else -> return false
        }
        return true
    }

    override fun onStrategySelected() {
        refreshDisplay()
    }

    override fun onFolderSuccess() {
        refreshDisplay()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onImportEventComplete(event: ProcessEvent) {
        if (ProcessEvent.Type.COMPLETE == event.eventType
            && (event.processId == R.id.import_external || event.processId == R.id.import_primary)
        ) {
            event.logFile?.let { logFile ->
                refreshDisplay()
                val snackbar =
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.task_done,
                        BaseTransientBottomBar.LENGTH_LONG
                    )
                snackbar.setAction(R.string.read_log) { openFile(this, logFile) }
                snackbar.show()
            }
        }
    }
}