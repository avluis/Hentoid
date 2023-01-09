package me.devsaki.hentoid.fragments.preferences

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioGroup
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.DialogPrefsRefreshBinding
import me.devsaki.hentoid.databinding.IncludeImportStepsBinding
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.events.ServiceDestroyedEvent
import me.devsaki.hentoid.util.ImportHelper.*
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.file.PermissionHelper
import me.devsaki.hentoid.workers.PrimaryImportWorker
import org.apache.commons.lang3.tuple.ImmutablePair
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber

const val SHOW_OPTIONS = "show_options"
const val CHOOSE_FOLDER = "choose_folder"
const val EXTERNAL_LIBRARY = "external_library"

/**
 * Launcher dialog for the following features :
 * - Set download folder
 * - Library refresh
 */
class LibRefreshDialogFragment : DialogFragment(R.layout.dialog_prefs_refresh) {
    // == UI
    private var _binding1: DialogPrefsRefreshBinding? = null
    private val binding1 get() = _binding1!!

    private var _binding2: IncludeImportStepsBinding? = null
    private val binding2 get() = _binding2!!

    private var showOptions = false
    private var chooseFolder = false
    private var externalLibrary = false

    private var isServiceGracefulClose = false

    // Disposables for RxJava
    private var importDisposable: Disposable? = null
    private val compositeDisposable = CompositeDisposable()

    private val pickFolder =
        registerForActivityResult(PickFolderContract()) { result: ImmutablePair<Int?, Uri?> ->
            onFolderPickerResult(result.left!!, result.right!!)
        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        _binding1 = DialogPrefsRefreshBinding.inflate(inflater, container, false)
        requireNotNull(arguments) { "No arguments found" }
        arguments?.let {
            showOptions = it.getBoolean(SHOW_OPTIONS, false)
            chooseFolder = it.getBoolean(CHOOSE_FOLDER, false)
            externalLibrary = it.getBoolean(EXTERNAL_LIBRARY, false)
        }

        EventBus.getDefault().register(this)
        return binding1.root
    }

    override fun onDestroyView() {
        EventBus.getDefault().unregister(this)
        compositeDisposable.clear()
        _binding1 = null
        _binding2 = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        if (showOptions) { // Show option screen first
            binding1.let {
                it.refreshLocation.setOnCheckedChangeListener { _: RadioGroup?, i: Int ->
                    onLocationChanged(i)
                }
                it.refreshOptions.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        it.refreshOptionsSubgroup.visibility = View.VISIBLE
                        val warningVisibility =
                            if (it.refreshOptionsRenumberPages.isChecked) View.VISIBLE else View.GONE
                        it.refreshRenumberWarningTxt.visibility = warningVisibility
                        it.warningImg.visibility = warningVisibility
                    } else {
                        it.refreshOptionsSubgroup.visibility = View.GONE
                        it.refreshRenumberWarningTxt.visibility = View.GONE
                        it.warningImg.visibility = View.GONE
                    }
                }
                it.refreshOptionsRenumberPages.setOnCheckedChangeListener { _, isChecked ->
                    val visibility = if (isChecked) View.VISIBLE else View.GONE
                    it.refreshRenumberWarningTxt.visibility = visibility
                    it.warningImg.visibility = visibility
                }

                if (Preferences.getExternalLibraryUri().isNotEmpty())
                    it.refreshLocationGroup.visibility = View.VISIBLE

                it.actionButton.setOnClickListener { _ ->
                    launchRefreshImport(
                        it.refreshLocationExternal.isChecked,
                        it.refreshOptionsRename.isChecked,
                        it.refreshOptionsRemovePlaceholders.isChecked,
                        it.refreshOptionsRemove1.isChecked,
                        it.refreshOptionsRemove2.isChecked
                    )
                }
            }
        } else { // Show import progress layout immediately
            showImportProgressLayout(chooseFolder, externalLibrary)
        }
    }

    private fun onLocationChanged(@IdRes checkedId: Int) {
        binding1.let {
            if (checkedId == R.id.refresh_location_external)
                it.refreshOptions.visibility = View.GONE
            else it.refreshOptions.visibility = View.VISIBLE
            it.refreshOptions.isChecked = false
        }
    }

    private fun launchRefreshImport(
        isExternal: Boolean,
        rename: Boolean,
        removePlaceholders: Boolean,
        cleanAbsent: Boolean,
        cleanNoImages: Boolean
    ) {
        showImportProgressLayout(false, isExternal)
        isCancelable = false

        // Run import
        if (isExternal) {
            val externalUri = Uri.parse(Preferences.getExternalLibraryUri())
            compositeDisposable.add(Single.fromCallable {
                setAndScanExternalFolder(
                    requireContext(), externalUri
                )
            }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ res: Int ->
                    if (ProcessFolderResult.KO_INVALID_FOLDER == res || ProcessFolderResult.KO_CREATE_FAIL == res || ProcessFolderResult.KO_APP_FOLDER == res || ProcessFolderResult.KO_DOWNLOAD_FOLDER == res || ProcessFolderResult.KO_ALREADY_RUNNING == res || ProcessFolderResult.KO_OTHER == res) {
                        Snackbar.make(
                            binding1.root, getMessage(res), BaseTransientBottomBar.LENGTH_LONG
                        ).show()
                        Handler(Looper.getMainLooper()).postDelayed(
                            { dismissAllowingStateLoss() }, 3000
                        )
                    }
                }) { t: Throwable? ->
                    Timber.w(t)
                    Snackbar.make(
                        binding1.root,
                        getMessage(ProcessFolderResult.KO_OTHER),
                        BaseTransientBottomBar.LENGTH_LONG
                    ).show()
                    Handler(Looper.getMainLooper()).postDelayed(
                        { dismissAllowingStateLoss() }, 3000
                    )
                })
        } else {
            val options = ImportOptions()
            options.rename = rename
            options.removePlaceholders = removePlaceholders
            options.cleanNoJson = cleanAbsent
            options.cleanNoImages = cleanNoImages
            options.importGroups = false
            val uriStr = Preferences.getStorageUri()
            if (uriStr.isEmpty()) {
                ToastHelper.toast(requireContext(), R.string.import_invalid_uri)
                dismissAllowingStateLoss()
                return
            }
            val rootUri = Uri.parse(uriStr)
            compositeDisposable.add(Single.fromCallable {
                setAndScanHentoidFolder(
                    requireContext(), rootUri, false, options
                )
            }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ res: Int ->
                    if (ProcessFolderResult.KO_INVALID_FOLDER == res || ProcessFolderResult.KO_CREATE_FAIL == res || ProcessFolderResult.KO_APP_FOLDER == res || ProcessFolderResult.KO_DOWNLOAD_FOLDER == res || ProcessFolderResult.KO_ALREADY_RUNNING == res || ProcessFolderResult.KO_OTHER == res) {
                        Snackbar.make(
                            binding1.root, getMessage(res), BaseTransientBottomBar.LENGTH_LONG
                        ).show()
                        Handler(Looper.getMainLooper()).postDelayed(
                            { dismissAllowingStateLoss() }, 3000
                        )
                    }
                }) { t: Throwable? ->
                    Timber.w(t)
                    Snackbar.make(
                        binding1.root,
                        getMessage(ProcessFolderResult.KO_OTHER),
                        BaseTransientBottomBar.LENGTH_LONG
                    ).show()
                    Handler(Looper.getMainLooper()).postDelayed(
                        { dismissAllowingStateLoss() }, 3000
                    )
                })
        }
    }

    private fun showImportProgressLayout(askFolder: Boolean, isExternal: Boolean) {
        // Replace launch options layout with import progress layout
        binding1.root.removeAllViews()
        _binding2 =
            IncludeImportStepsBinding.inflate(requireActivity().layoutInflater, binding1.root)

        // Memorize UI elements that will be updated during the import events
        if (isExternal) {
            binding2.importStep1Button.setText(R.string.api29_migration_step1_select_external)
            binding2.importStep1Text.setText(R.string.api29_migration_step1_external)
        } else {
            binding2.importStep1Button.setText(R.string.api29_migration_step1_select)
            binding2.importStep1Text.setText(R.string.api29_migration_step1)
        }
        if (askFolder) {
            binding2.importStep1Button.visibility = View.VISIBLE
            binding2.importStep1Button.setOnClickListener { pickFolder() }
            pickFolder() // Ask right away, there's no reason why the user should click again
        } else {
            binding2.importStep1Folder.text = FileHelper.getFullPathFromTreeUri(
                requireContext(), Uri.parse(
                    Preferences.getStorageUri()
                )
            )
            binding2.importStep1Check.visibility = View.VISIBLE
            binding2.importStep2.visibility = View.VISIBLE
            binding2.importStep2Bar.isIndeterminate = true
        }
    }

    private fun pickFolder() {
        if (PermissionHelper.requestExternalStorageReadWritePermission(
                requireActivity(), PermissionHelper.RQST_STORAGE_PERMISSION
            )
        ) { // Make sure permissions are set
            Preferences.setBrowserMode(false)
            pickFolder.launch(0) // Run folder picker
        }
    }

    private fun onFolderPickerResult(resultCode: Int, uri: Uri) {
        when (resultCode) {
            PickerResult.OK -> importDisposable = Single.fromCallable {
                if (externalLibrary) return@fromCallable setAndScanExternalFolder(
                    requireContext(), uri
                ) else return@fromCallable setAndScanHentoidFolder(
                    requireContext(), uri, true, null
                )
            }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ code: Int ->
                    onScanHentoidFolderResult(code)
                }) { t: Throwable? -> Timber.w(t) }
            PickerResult.KO_CANCELED -> Snackbar.make(
                binding2.root, R.string.import_canceled, BaseTransientBottomBar.LENGTH_LONG
            ).show()
            PickerResult.KO_OTHER, PickerResult.KO_NO_URI -> {
                Snackbar.make(
                    binding2.root, R.string.import_other, BaseTransientBottomBar.LENGTH_LONG
                ).show()
                isCancelable = true
            }
            else -> {}
        }
    }

    private fun onScanHentoidFolderResult(@ProcessFolderResult resultCode: Int) {
        importDisposable!!.dispose()
        when (resultCode) {
            ProcessFolderResult.OK_EMPTY_FOLDER -> dismissAllowingStateLoss()
            ProcessFolderResult.OK_LIBRARY_DETECTED ->                 // Hentoid folder is finally selected at this point -> Update UI
                updateOnSelectFolder()
            ProcessFolderResult.OK_LIBRARY_DETECTED_ASK -> {
                updateOnSelectFolder()
                showExistingLibraryDialog(requireContext()) { onCancelExistingLibraryDialog() }
            }
            ProcessFolderResult.KO_INVALID_FOLDER, ProcessFolderResult.KO_APP_FOLDER, ProcessFolderResult.KO_DOWNLOAD_FOLDER, ProcessFolderResult.KO_CREATE_FAIL, ProcessFolderResult.KO_ALREADY_RUNNING, ProcessFolderResult.KO_OTHER -> {
                Snackbar.make(
                    binding2.root, getMessage(resultCode), BaseTransientBottomBar.LENGTH_LONG
                ).show()
                isCancelable = true
            }
            else -> {}
        }
    }

    @StringRes
    private fun getMessage(@ProcessFolderResult resultCode: Int): Int {
        return when (resultCode) {
            ProcessFolderResult.KO_INVALID_FOLDER -> R.string.import_invalid
            ProcessFolderResult.KO_APP_FOLDER -> R.string.import_app_folder
            ProcessFolderResult.KO_DOWNLOAD_FOLDER -> R.string.import_download_folder
            ProcessFolderResult.KO_CREATE_FAIL -> R.string.import_create_fail
            ProcessFolderResult.KO_ALREADY_RUNNING -> R.string.service_running
            ProcessFolderResult.KO_OTHER -> R.string.import_other
            ProcessFolderResult.OK_EMPTY_FOLDER, ProcessFolderResult.OK_LIBRARY_DETECTED, ProcessFolderResult.OK_LIBRARY_DETECTED_ASK ->                 // Nothing should happen here
                R.string.none
            else -> R.string.none
        }
    }

    private fun onCancelExistingLibraryDialog() {
        // Revert back to initial state where only the "Select folder" button is visible
        binding2.importStep1Check.visibility = View.INVISIBLE
        binding2.importStep2.visibility = View.INVISIBLE
        binding2.importStep1Folder.text = ""
        binding2.importStep1Button.visibility = View.VISIBLE
        isCancelable = true
    }

    private fun updateOnSelectFolder() {
        binding2.importStep1Folder.text = FileHelper.getFullPathFromTreeUri(
            requireContext(), Uri.parse(
                Preferences.getStorageUri()
            )
        )
        binding2.importStep1Button.visibility = View.INVISIBLE
        binding2.importStep1Check.visibility = View.VISIBLE
        binding2.importStep2.visibility = View.VISIBLE
        binding2.importStep2Bar.isIndeterminate = true
        isCancelable = false
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onImportEvent(event: ProcessEvent) {
        if (event.processId != R.id.import_external && event.processId != R.id.import_primary) return
        binding2.let {
            val progressBar: ProgressBar = when (event.step) {
                PrimaryImportWorker.STEP_2_BOOK_FOLDERS -> it.importStep2Bar
                PrimaryImportWorker.STEP_3_BOOKS -> it.importStep3Bar
                else -> it.importStep4Bar
            }
            if (ProcessEvent.EventType.PROGRESS == event.eventType) {
                if (event.elementsTotal > -1) {
                    progressBar.isIndeterminate = false
                    progressBar.max = event.elementsTotal
                    progressBar.progress = event.elementsOK + event.elementsKO
                } else {
                    progressBar.isIndeterminate = true
                }
                if (PrimaryImportWorker.STEP_2_BOOK_FOLDERS == event.step) {
                    it.importStep2Text.text = event.elementName
                } else if (PrimaryImportWorker.STEP_3_BOOKS == event.step) {
                    it.importStep2Bar.isIndeterminate = false
                    it.importStep2Bar.max = 1
                    it.importStep2Bar.progress = 1
                    it.importStep2Text.visibility = View.GONE
                    it.importStep2Check.visibility = View.VISIBLE
                    it.importStep3.visibility = View.VISIBLE
                    it.importStep3Text.text = resources.getString(
                        R.string.api29_migration_step3,
                        event.elementsKO + event.elementsOK,
                        event.elementsTotal
                    )
                } else if (PrimaryImportWorker.STEP_4_QUEUE_FINAL == event.step) {
                    it.importStep3Check.visibility = View.VISIBLE
                    it.importStep4.visibility = View.VISIBLE
                }
            } else if (ProcessEvent.EventType.COMPLETE == event.eventType) {
                if (PrimaryImportWorker.STEP_2_BOOK_FOLDERS == event.step) {
                    it.importStep2Bar.isIndeterminate = false
                    it.importStep2Bar.max = 1
                    it.importStep2Bar.progress = 1
                    it.importStep2Text.visibility = View.GONE
                    it.importStep2Check.visibility = View.VISIBLE
                    it.importStep3.visibility = View.VISIBLE
                } else if (PrimaryImportWorker.STEP_3_BOOKS == event.step) {
                    it.importStep3Text.text = resources.getString(
                        R.string.api29_migration_step3, event.elementsTotal, event.elementsTotal
                    )
                    it.importStep3Check.visibility = View.VISIBLE
                    it.importStep4.visibility = View.VISIBLE
                } else if (PrimaryImportWorker.STEP_4_QUEUE_FINAL == event.step) {
                    it.importStep4Check.visibility = View.VISIBLE
                    isServiceGracefulClose = true
                    dismissAllowingStateLoss()
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
            Snackbar.make(
                binding1.root, R.string.import_unexpected, BaseTransientBottomBar.LENGTH_LONG
            ).show()
            Handler(Looper.getMainLooper()).postDelayed({ dismissAllowingStateLoss() }, 3000)
        }
    }

    companion object {
        fun invoke(
            fragmentManager: FragmentManager,
            showOptions: Boolean,
            chooseFolder: Boolean,
            externalLibrary: Boolean
        ) {
            val fragment = LibRefreshDialogFragment()

            val args = Bundle()
            args.putBoolean(SHOW_OPTIONS, showOptions)
            args.putBoolean(CHOOSE_FOLDER, chooseFolder)
            args.putBoolean(EXTERNAL_LIBRARY, externalLibrary)
            fragment.arguments = args

            fragment.show(fragmentManager, null)
        }
    }
}