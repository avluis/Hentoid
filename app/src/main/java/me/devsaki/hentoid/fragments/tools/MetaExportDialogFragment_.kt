package me.devsaki.hentoid.fragments.tools

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposables
import io.reactivex.schedulers.Schedulers
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consts
import me.devsaki.hentoid.core.startBrowserActivity
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogToolsMetaExportBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.json.JsonContentCollection
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.file.FileHelper
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class MetaExportDialogFragment_ : DialogFragment(R.layout.dialog_tools_meta_export) {

    // == UI
    private var binding: DialogToolsMetaExportBinding? = null

    // Variable used during the import process
    private lateinit var dao: CollectionDAO

    // Disposable for RxJava
    private var exportDisposable = Disposables.empty()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogToolsMetaExportBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        dao = ObjectBoxDAO(requireContext())
        val nbLibraryBooks = dao.countAllInternalBooks(false)
        val nbQueueBooks = dao.countAllQueueBooks()
        val nbBookmarks = dao.countAllBookmarks()
        binding?.let {
            it.exportQuestion.setOnCheckedChangeListener { _: RadioGroup?, id: Int ->
                run {
                    it.exportQuestion.isEnabled = false
                    it.exportQuestionYes.isEnabled = false
                    it.exportQuestionNo.isEnabled = false
                    val yes = (R.id.export_question_yes == id)
                    it.exportGroupYes.isVisible = yes
                    it.exportGroupNo.isVisible = !yes
                }
            }

            it.exportFavsOnly.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean -> refreshFavsDisplay() }
            if (nbLibraryBooks > 0) {
                it.exportFileLibraryChk.text = resources.getQuantityString(
                    R.plurals.export_file_library,
                    nbLibraryBooks.toInt(),
                    nbLibraryBooks.toInt()
                )
                it.exportFileLibraryChk.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean -> refreshDisplay() }
                it.exportGroupNo.addView(it.exportFileLibraryChk)
            }
            if (nbQueueBooks > 0) {
                it.exportFileQueueChk.text = resources.getQuantityString(
                    R.plurals.export_file_queue,
                    nbQueueBooks.toInt(),
                    nbQueueBooks.toInt()
                )
                it.exportFileQueueChk.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean -> refreshDisplay() }
                it.exportGroupNo.addView(it.exportFileQueueChk)
            }
            if (nbBookmarks > 0) {
                it.exportFileBookmarksChk.text = resources.getQuantityString(
                    R.plurals.export_file_bookmarks,
                    nbBookmarks.toInt(),
                    nbBookmarks.toInt()
                )
                it.exportFileBookmarksChk.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean -> refreshDisplay() }
                it.exportGroupNo.addView(it.exportFileBookmarksChk)
            }

            // Open library transfer FAQ
            it.exportWikiLink.setOnClickListener { requireActivity().startBrowserActivity(Consts.URL_WIKI_TRANSFER) }
            it.exportRunBtn.isEnabled = false
            if (0L == nbLibraryBooks + nbQueueBooks + nbBookmarks)
                it.exportRunBtn.visibility = View.GONE
            else it.exportRunBtn.setOnClickListener { _ ->
                runExport(
                    it.exportFileLibraryChk.isChecked,
                    it.exportFavsOnly.isChecked,
                    it.exportGroups.isChecked,
                    it.exportFileQueueChk.isChecked,
                    it.exportFileBookmarksChk.isChecked
                )
            }
        }
    }

    // Gray out run button if no option is selected
    private fun refreshDisplay() {
        binding?.let {
            it.exportRunBtn.isEnabled =
                it.exportFileQueueChk.isChecked || it.exportFileLibraryChk.isChecked || it.exportFileBookmarksChk.isChecked
            it.exportFavsOnly.visibility =
                if (it.exportFileLibraryChk.isChecked) View.VISIBLE else View.GONE
            it.exportGroups.visibility =
                if (it.exportFileLibraryChk.isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun refreshFavsDisplay() {
        binding?.let {
            val nbLibraryBooks = dao.countAllInternalBooks(it.exportFavsOnly.isChecked)
            it.exportFileLibraryChk.text = resources.getQuantityString(
                R.plurals.export_file_library,
                nbLibraryBooks.toInt(),
                nbLibraryBooks.toInt()
            )
        }
    }

    private fun runExport(
        exportLibrary: Boolean,
        exportFavsOnly: Boolean,
        exportCustomGroups: Boolean,
        exportQueue: Boolean,
        exportBookmarks: Boolean
    ) {
        isCancelable = false

        binding?.let {
            it.exportFileLibraryChk.isEnabled = false
            it.exportFileQueueChk.isEnabled = false
            it.exportFileBookmarksChk.isEnabled = false
            it.exportRunBtn.visibility = View.GONE
            it.exportProgressBar.isIndeterminate = true

            // fixes <= Lollipop progressBar tinting
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) it.exportProgressBar.indeterminateDrawable.colorFilter =
                PorterDuffColorFilter(
                    ThemeHelper.getColor(
                        requireContext(),
                        R.color.secondary_light
                    ), PorterDuff.Mode.SRC_IN
                )
            it.exportProgressBar.visibility = View.VISIBLE
            exportDisposable = Single.fromCallable {
                getExportedCollection(
                    exportLibrary,
                    exportFavsOnly,
                    exportCustomGroups,
                    exportQueue,
                    exportBookmarks
                )
            }
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map { c: JsonContentCollection? ->
                    it.exportProgressBar.max = 3
                    it.exportProgressBar.progress = 1
                    it.exportProgressBar.isIndeterminate = false
                    JsonHelper.serializeToJson(c, JsonContentCollection::class.java)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { s: String ->
                        it.exportProgressBar.progress = 2
                        onJsonSerialized(
                            s,
                            exportLibrary,
                            exportFavsOnly,
                            exportQueue,
                            exportBookmarks
                        )
                        it.exportProgressBar.progress = 3
                    }
                ) { t: Throwable? ->
                    Timber.w(t)
                    Helper.logException(t)
                    Snackbar.make(
                        it.root,
                        R.string.export_failed,
                        BaseTransientBottomBar.LENGTH_LONG
                    )
                        .show()
                    // Dismiss after 3s, for the user to be able to see and use the snackbar
                    Handler(Looper.getMainLooper())
                        .postDelayed({ this.dismissAllowingStateLoss() }, 3000)
                }
        }
    }

    private fun getExportedCollection(
        exportLibrary: Boolean,
        exportFavsOnly: Boolean,
        exportCustomgroups: Boolean,
        exportQueue: Boolean,
        exportBookmarks: Boolean
    ): JsonContentCollection {
        val jsonContentCollection = JsonContentCollection()
        if (exportLibrary) dao.streamAllInternalBooks(
            exportFavsOnly
        ) { content: Content? ->
            jsonContentCollection.addToLibrary(
                content!!
            )
        } // Using streaming here to support large collections
        if (exportQueue) jsonContentCollection.queue = dao.selectAllQueueBooks()
        if (exportCustomgroups) jsonContentCollection.customGroups =
            dao.selectGroups(Grouping.CUSTOM.id)
        if (exportBookmarks) jsonContentCollection.bookmarks = dao.selectAllBookmarks()
        jsonContentCollection.renamingRules =
            dao.selectRenamingRules(AttributeType.UNDEFINED, null)
        return jsonContentCollection
    }

    private fun onJsonSerialized(
        json: String,
        exportLibrary: Boolean,
        exportFavsOnly: Boolean,
        exportQueue: Boolean,
        exportBookmarks: Boolean
    ) {
        exportDisposable.dispose()

        // Use a random number to avoid erasing older exports by mistake
        var targetFileName = Helper.getRandomInt(9999).toString() + ".json"
        if (exportBookmarks) targetFileName = "bkmks-$targetFileName"
        if (exportQueue) targetFileName = "queue-$targetFileName"
        if (exportLibrary && !exportFavsOnly) targetFileName =
            "library-$targetFileName" else if (exportLibrary) targetFileName =
            "favs-$targetFileName"
        targetFileName = "export-$targetFileName"
        try {
            FileHelper.openNewDownloadOutputStream(
                requireContext(),
                targetFileName,
                JsonHelper.JSON_MIME_TYPE
            ).use { newDownload ->
                ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8))
                    .use { input ->
                        Helper.copy(
                            input,
                            newDownload
                        )
                    }
            }
            binding?.let {
                Snackbar.make(
                    it.root,
                    R.string.copy_download_folder_success,
                    BaseTransientBottomBar.LENGTH_LONG
                )
                    .setAction(R.string.open_folder) {
                        FileHelper.openFile(
                            requireContext(),
                            FileHelper.getDownloadsFolder()
                        )
                    }
                    .show()
            }
        } catch (e: IOException) {
            binding?.let {
                Snackbar.make(
                    it.root,
                    R.string.copy_download_folder_fail,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
        } catch (e: IllegalArgumentException) {
            binding?.let {
                Snackbar.make(
                    it.root,
                    R.string.copy_download_folder_fail,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
        }
        dao.cleanup()
        // Dismiss after 3s, for the user to be able to see and use the snackbar
        Handler(Looper.getMainLooper()).postDelayed({ this.dismissAllowingStateLoss() }, 3000)
    }

    companion object {
        fun invoke(fragmentManager: FragmentManager) {
            val fragment = MetaExportDialogFragment_()
            fragment.show(fragmentManager, null)
        }
    }
}