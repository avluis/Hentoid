package me.devsaki.hentoid.activities

import android.net.Uri
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.ActivityPrefsStorageBinding
import me.devsaki.hentoid.databinding.IncludePrefsStorageVolumeBinding
import me.devsaki.hentoid.fragments.preferences.LibRefreshDialogFragment
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
                binding1 = IncludePrefsStorageVolumeBinding.inflate(layoutInflater, primaryVolume1)
                bindLocation(binding1, "1", Preferences.getStorageUri())
            }
            addPrimary1.isVisible = Preferences.getStorageUri().isEmpty()

            primaryVolume2.isVisible = Preferences.getStorageUri2().isNotEmpty()
            if (primaryVolume2.isVisible) {
                binding2 = IncludePrefsStorageVolumeBinding.inflate(layoutInflater, primaryVolume2)
                bindLocation(binding2, "2", Preferences.getStorageUri2())
            }
            addPrimary2.isVisible = Preferences.getStorageUri2().isEmpty()

            externalVolume.isVisible = Preferences.getExternalLibraryUri().isNotEmpty()
            if (externalVolume.isVisible) {
                bindingExt =
                    IncludePrefsStorageVolumeBinding.inflate(layoutInflater, externalVolume)
                bindLocation(bindingExt, "", Preferences.getExternalLibraryUri())
            }
            addExternal.isVisible = Preferences.getExternalLibraryUri().isEmpty()
        }
    }

    private fun bindLocation(
        binding: IncludePrefsStorageVolumeBinding?,
        id: String,
        uriStr: String
    ) {
        binding?.apply {
            number.text = id
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
//                    setTotalColor(R.color.primary_light)
//                    setProgress1Color(R.color.secondary_light)
                    //setTotal(locationTotalBytes)
                    //setProgress1(locationFreeBytes.toFloat())
                    isIndeterminate = false
                    progress = (locationFreeBytes * 100 / locationTotalBytes).toInt()
                }
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
}