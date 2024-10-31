package me.devsaki.hentoid.fragments.preferences

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.DialogPrefsRefreshBinding
import me.devsaki.hentoid.databinding.IncludeImportStepsBinding
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.events.ServiceDestroyedEvent
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.ImportOptions
import me.devsaki.hentoid.util.PickFolderContract
import me.devsaki.hentoid.util.PickerResult
import me.devsaki.hentoid.util.ProcessFolderResult
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.RQST_STORAGE_PERMISSION
import me.devsaki.hentoid.util.file.getFullPathFromUri
import me.devsaki.hentoid.util.file.requestExternalStorageReadWritePermission
import me.devsaki.hentoid.util.setAndScanExternalFolder
import me.devsaki.hentoid.util.setAndScanPrimaryFolder
import me.devsaki.hentoid.util.showExistingLibraryDialog
import me.devsaki.hentoid.util.toastShort
import me.devsaki.hentoid.workers.STEP_2_BOOK_FOLDERS
import me.devsaki.hentoid.workers.STEP_3_BOOKS
import me.devsaki.hentoid.workers.STEP_3_PAGES
import me.devsaki.hentoid.workers.STEP_4_QUEUE_FINAL
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber

/**
 * Launcher dialog for the following features :
 * - Set/replace download folder
 * - Library refresh
 */
class LibRefreshDialogFragment : BaseDialogFragment<LibRefreshDialogFragment.Parent>() {
    // == UI
    private var binding1: DialogPrefsRefreshBinding? = null
    private var binding2: IncludeImportStepsBinding? = null

    // === VARIABLES
    private var showOptions = false
    private var chooseFolder = false
    private var location = StorageLocation.NONE

    private var isServiceGracefulClose = false

    private val pickFolder =
        registerForActivityResult(PickFolderContract()) { result: Pair<PickerResult, Uri> ->
            onFolderPickerResult(result.first, result.second)
        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View? {
        binding1 = DialogPrefsRefreshBinding.inflate(inflater, container, false)
        requireNotNull(arguments) { "No arguments found" }
        arguments?.apply {
            showOptions = getBoolean(SHOW_OPTIONS, false)
            chooseFolder = getBoolean(CHOOSE_FOLDER, false)
            location = StorageLocation.entries.toTypedArray()[getInt(
                LOCATION,
                StorageLocation.NONE.ordinal
            )]
        }

        EventBus.getDefault().register(this)
        return binding1?.root
    }

    override fun onDestroyView() {
        EventBus.getDefault().unregister(this)
        binding1 = null
        binding2 = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        if (showOptions) { // Show option screen first
            binding1?.apply {
                refreshOptions.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        refreshOptionsSubgroup.visibility = View.VISIBLE
                        val warningVisibility =
                            if (refreshOptionsRenumberPages.isChecked) View.VISIBLE else View.GONE
                        refreshRenumberWarningTxt.visibility = warningVisibility
                        warningImg.visibility = warningVisibility
                    } else {
                        refreshOptionsSubgroup.visibility = View.GONE
                        refreshRenumberWarningTxt.visibility = View.GONE
                        warningImg.visibility = View.GONE
                    }
                }
                refreshOptionsRenumberPages.setOnCheckedChangeListener { _, isChecked ->
                    val visibility = if (isChecked) View.VISIBLE else View.GONE
                    refreshRenumberWarningTxt.visibility = visibility
                    warningImg.visibility = visibility
                }

                actionButton.setOnClickListener {
                    showImportProgressLayout(
                        false,
                        location
                    )
                    runImport(
                        location,
                        refreshOptionsRename.isChecked,
                        refreshOptionsRemovePlaceholders.isChecked,
                        refreshOptionsRenumberPages.isChecked,
                        refreshOptionsRemove1.isChecked,
                        refreshOptionsRemove2.isChecked
                    )
                }
            }
        } else { // Show import progress layout immediately
            showImportProgressLayout(chooseFolder, location)
            if (!chooseFolder) runImport(location)
        }
    }

    private fun runImport(
        location: StorageLocation,
        rename: Boolean = false,
        removePlaceholders: Boolean = false,
        renumberPages: Boolean = false,
        cleanAbsent: Boolean = false,
        cleanNoImages: Boolean = false
    ) {
        isCancelable = false

        if (location == StorageLocation.EXTERNAL) {
            val externalUri = Uri.parse(Settings.externalLibraryUri)

            lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) {
                    try {
                        val res = setAndScanExternalFolder(requireContext(), externalUri)
                        return@withContext res.first
                    } catch (e: Exception) {
                        Timber.w(e)
                        return@withContext ProcessFolderResult.KO_OTHER
                    }
                }
                if (ProcessFolderResult.KO_INVALID_FOLDER == res
                    || ProcessFolderResult.KO_CREATE_FAIL == res
                    || ProcessFolderResult.KO_APP_FOLDER == res
                    || ProcessFolderResult.KO_DOWNLOAD_FOLDER == res
                    || ProcessFolderResult.KO_ALREADY_RUNNING == res
                    || ProcessFolderResult.KO_OTHER == res
                ) {
                    binding1?.apply {
                        Snackbar.make(
                            root,
                            getMessage(res),
                            BaseTransientBottomBar.LENGTH_LONG
                        ).show()
                        delay(3000)
                    }
                    dismissAllowingStateLoss()
                }
            }
        } else {
            val options = ImportOptions(
                rename,
                removePlaceholders,
                renumberPages,
                cleanAbsent,
                cleanNoImages,
                false
            )
            val uriStr = Settings.getStorageUri(location)
            if (uriStr.isEmpty()) {
                toastShort(R.string.import_invalid_uri)
                dismissAllowingStateLoss()
                return
            }
            val rootUri = Uri.parse(uriStr)

            lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) {
                    try {
                        val res = setAndScanPrimaryFolder(
                            requireContext(), rootUri, location, false, options
                        )
                        return@withContext res.first
                    } catch (e: Exception) {
                        Timber.w(e)
                        return@withContext ProcessFolderResult.KO_OTHER
                    }
                }

                if (ProcessFolderResult.KO_INVALID_FOLDER == res
                    || ProcessFolderResult.KO_CREATE_FAIL == res
                    || ProcessFolderResult.KO_APP_FOLDER == res
                    || ProcessFolderResult.KO_DOWNLOAD_FOLDER == res
                    || ProcessFolderResult.KO_ALREADY_RUNNING == res
                    || ProcessFolderResult.KO_OTHER_PRIMARY == res
                    || ProcessFolderResult.KO_PRIMARY_EXTERNAL == res
                    || ProcessFolderResult.OK_EMPTY_FOLDER == res
                    || ProcessFolderResult.KO_OTHER == res
                ) {
                    binding1?.apply {
                        Snackbar.make(root, getMessage(res), BaseTransientBottomBar.LENGTH_LONG)
                            .show()
                        delay(3000)
                    }
                    if (ProcessFolderResult.OK_EMPTY_FOLDER == res) parent?.onFolderSuccess()
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    private fun showImportProgressLayout(askFolder: Boolean, location: StorageLocation) {
        // Replace launch options layout with import progress layout
        binding1?.apply {
            root.removeAllViews()
            binding2 = IncludeImportStepsBinding.inflate(requireActivity().layoutInflater, root)
        }

        // Memorize UI elements that will be updated during the import events
        binding2?.apply {
            when (location) {
                StorageLocation.PRIMARY_1 -> {
                    importStep1Button.setText(R.string.refresh_step1)
                    importStep1Text.setText(R.string.refresh_step1_select)
                }

                StorageLocation.PRIMARY_2 -> {
                    importStep1Button.setText(R.string.refresh_step1_2)
                    importStep1Text.setText(R.string.refresh_step1_select_2)
                }

                StorageLocation.EXTERNAL -> {
                    importStep1Button.setText(R.string.refresh_step1_select_external)
                    importStep1Text.setText(R.string.refresh_step1_external)
                }

                else -> {
                    // Nothing
                }
            }
            if (askFolder) {
                importStep1Button.visibility = View.VISIBLE
                importStep1Button.setOnClickListener { pickFolder() }
                pickFolder() // Ask right away, there's no reason why the user should click again
            } else {
                importStep1Folder.text = getFullPathFromUri(
                    requireContext(), Uri.parse(Settings.getStorageUri(location))
                )
                importStep1Folder.isVisible = true
                importStep1Text.isVisible = true
                importStep1Check.isVisible = true
                importStep2.isVisible = true
                importStep2Bar.isIndeterminate = true
            }
        }
    }

    private fun pickFolder() {
        // Make sure permissions are set
        if (requireActivity().requestExternalStorageReadWritePermission(RQST_STORAGE_PERMISSION)) {
            Settings.isBrowserMode = false
            pickFolder.launch(location) // Run folder picker
        }
    }

    private fun onFolderPickerResult(resultCode: PickerResult, uri: Uri) {
        when (resultCode) {
            PickerResult.OK -> {
                lifecycleScope.launch {
                    val res = withContext(Dispatchers.IO) {
                        return@withContext if (location == StorageLocation.EXTERNAL) setAndScanExternalFolder(
                            requireContext(), uri
                        ) else setAndScanPrimaryFolder(
                            requireContext(), uri, location, true, null
                        )
                    }
                    onScanHentoidFolderResult(res.first, res.second)
                }
            }

            PickerResult.KO_CANCELED -> {
                binding2?.apply {
                    Snackbar.make(
                        root,
                        R.string.import_canceled,
                        BaseTransientBottomBar.LENGTH_LONG
                    ).show()
                }
            }

            PickerResult.KO_OTHER, PickerResult.KO_NO_URI -> {
                binding2?.apply {
                    Snackbar.make(root, R.string.import_other, BaseTransientBottomBar.LENGTH_LONG)
                        .show()
                }
                isCancelable = true
            }
        }
    }

    private fun onScanHentoidFolderResult(resultCode: ProcessFolderResult, rootUri: String) {
        when (resultCode) {
            ProcessFolderResult.OK_EMPTY_FOLDER -> {
                parent?.onFolderSuccess()
                dismissAllowingStateLoss()
            }

            ProcessFolderResult.OK_LIBRARY_DETECTED ->                 // Hentoid folder is finally selected at this point -> Update UI
                updateOnSelectFolder()

            ProcessFolderResult.OK_LIBRARY_DETECTED_ASK -> {
                updateOnSelectFolder()
                showExistingLibraryDialog(
                    requireContext(),
                    location,
                    rootUri
                ) { onCancelExistingLibraryDialog() }
            }

            ProcessFolderResult.KO_INVALID_FOLDER,
            ProcessFolderResult.KO_APP_FOLDER,
            ProcessFolderResult.KO_DOWNLOAD_FOLDER,
            ProcessFolderResult.KO_CREATE_FAIL,
            ProcessFolderResult.KO_ALREADY_RUNNING,
            ProcessFolderResult.KO_OTHER_PRIMARY,
            ProcessFolderResult.KO_PRIMARY_EXTERNAL,
            ProcessFolderResult.KO_OTHER -> {
                binding2?.apply {
                    Snackbar.make(root, getMessage(resultCode), BaseTransientBottomBar.LENGTH_LONG)
                        .show()
                }
                isCancelable = true
            }
        }
    }

    @StringRes
    private fun getMessage(resultCode: ProcessFolderResult): Int {
        return when (resultCode) {
            ProcessFolderResult.KO_INVALID_FOLDER -> R.string.import_invalid
            ProcessFolderResult.KO_APP_FOLDER -> R.string.import_app_folder
            ProcessFolderResult.KO_DOWNLOAD_FOLDER -> R.string.import_download_folder
            ProcessFolderResult.KO_CREATE_FAIL -> R.string.import_create_fail
            ProcessFolderResult.KO_ALREADY_RUNNING -> R.string.service_running
            ProcessFolderResult.KO_OTHER_PRIMARY -> R.string.import_other_primary
            ProcessFolderResult.KO_PRIMARY_EXTERNAL -> R.string.import_other_external_inside_primary
            ProcessFolderResult.OK_EMPTY_FOLDER -> R.string.import_empty
            ProcessFolderResult.KO_OTHER -> R.string.import_other
            ProcessFolderResult.OK_LIBRARY_DETECTED,
            ProcessFolderResult.OK_LIBRARY_DETECTED_ASK -> R.string.none
            // Nothing should happen here
        }
    }

    private fun onCancelExistingLibraryDialog() {
        // Revert back to initial state where only the "Select folder" button is visible
        binding2?.apply {
            importStep1Text.visibility = View.INVISIBLE
            importStep1Folder.text = ""
            importStep1Check.visibility = View.INVISIBLE
            importStep2.visibility = View.INVISIBLE
            importStep1Button.isVisible = true
        }
        isCancelable = true
    }

    private fun updateOnSelectFolder() {
        binding2?.apply {
            importStep1Folder.text = getFullPathFromUri(
                requireContext(), Uri.parse(Settings.getStorageUri(location))
            )
            importStep1Folder.isVisible = true
            importStep1Text.isVisible = true
            importStep1Button.visibility = View.INVISIBLE
            importStep1Check.isVisible = true
            importStep2.isVisible = true
            importStep2Bar.isIndeterminate = true
        }
        isCancelable = false
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onImportEvent(event: ProcessEvent) {
        if (event.processId != R.id.import_external
            && event.processId != R.id.import_primary
            && event.processId != R.id.import_primary_pages
        ) return

        importEvent(event)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onImportStickyEvent(event: ProcessEvent) {
        if (event.processId != R.id.import_external
            && event.processId != R.id.import_primary
            && event.processId != R.id.import_primary_pages
        ) return
        EventBus.getDefault().removeStickyEvent(event)
        importEvent(event)
    }

    private fun importEvent(event: ProcessEvent) {
        binding2?.apply {
            val progressBar: ProgressBar = when (event.step) {
                STEP_2_BOOK_FOLDERS -> importStep2Bar
                STEP_3_BOOKS -> importStep3Bar
                STEP_3_PAGES -> importStep3SubBar
                else -> importStep4Bar
            }
            if (ProcessEvent.Type.PROGRESS == event.eventType) {
                if (event.elementsTotal > -1) {
                    progressBar.isIndeterminate = false
                    progressBar.max = event.elementsTotal
                    progressBar.progress = event.elementsOK + event.elementsKO
                } else {
                    progressBar.isIndeterminate = true
                }
                when (event.step) {
                    STEP_2_BOOK_FOLDERS -> {
                        importStep2Text.text = event.elementName
                    }

                    STEP_3_BOOKS -> {
                        importStep2Bar.isIndeterminate = false
                        importStep2Bar.max = 1
                        importStep2Bar.progress = 1
                        importStep2Text.visibility = View.GONE
                        importStep2Check.visibility = View.VISIBLE
                        importStep3.visibility = View.VISIBLE
                        importStep3Text.text = resources.getString(
                            R.string.refresh_step3,
                            event.elementsKO + event.elementsOK,
                            event.elementsTotal
                        )
                    }

                    STEP_3_PAGES -> {
                        progressBar.visibility = View.VISIBLE
                    }

                    STEP_4_QUEUE_FINAL -> {
                        importStep3Check.visibility = View.VISIBLE
                        importStep4.visibility = View.VISIBLE
                    }
                }
            } else if (ProcessEvent.Type.COMPLETE == event.eventType) {
                when (event.step) {
                    STEP_2_BOOK_FOLDERS -> {
                        importStep2Bar.isIndeterminate = false
                        importStep2Bar.max = 1
                        importStep2Bar.progress = 1
                        importStep2Text.visibility = View.GONE
                        importStep2Check.visibility = View.VISIBLE
                        importStep3.visibility = View.VISIBLE
                    }

                    STEP_3_BOOKS -> {
                        importStep3Text.text = resources.getString(
                            R.string.refresh_step3,
                            event.elementsTotal,
                            event.elementsTotal
                        )
                        importStep3Check.visibility = View.VISIBLE
                        importStep4.visibility = View.VISIBLE
                    }

                    STEP_3_PAGES -> {
                        progressBar.visibility = View.GONE
                    }

                    STEP_4_QUEUE_FINAL -> {
                        importStep4Check.visibility = View.VISIBLE

                        isServiceGracefulClose = true
                        // Tell library screens to go back to top
                        EventBus.getDefault().post(
                            CommunicationEvent(
                                CommunicationEvent.Type.SCROLL_TOP,
                                CommunicationEvent.Recipient.ALL
                            )
                        )
                        dismissAllowingStateLoss()
                    }
                }
            }
        }
    }

    /**
     * Service destroyed event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onServiceDestroyed(event: ServiceDestroyedEvent) {
        if (event.service != R.id.import_service) return
        if (!isServiceGracefulClose) {
            binding1?.apply {
                Snackbar.make(
                    root,
                    R.string.import_unexpected,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
            Handler(Looper.getMainLooper()).postDelayed(
                { dismissAllowingStateLoss() },
                3000
            )
        }
    }

    companion object {
        const val SHOW_OPTIONS = "show_options"
        const val CHOOSE_FOLDER = "choose_folder"
        const val LOCATION = "location"

        fun invoke(
            fragmentManager: FragmentManager,
            showOptions: Boolean,
            chooseFolder: Boolean,
            location: StorageLocation
        ) {
            val fragment = LibRefreshDialogFragment()

            val args = Bundle()
            args.putBoolean(SHOW_OPTIONS, showOptions)
            args.putBoolean(CHOOSE_FOLDER, chooseFolder)
            args.putInt(LOCATION, location.ordinal)
            fragment.arguments = args

            fragment.show(fragmentManager, null)
        }
    }

    interface Parent {
        fun onFolderSuccess()
    }
}