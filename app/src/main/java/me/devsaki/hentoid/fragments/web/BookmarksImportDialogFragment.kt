package me.devsaki.hentoid.fragments.web

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.databinding.DialogWebBookmarksImportBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.fragments.web.BookmarksImportDialogFragment.Parent
import me.devsaki.hentoid.util.PickFileContract
import me.devsaki.hentoid.util.PickerResult
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.importBookmarks
import me.devsaki.hentoid.util.parseBookmarks
import timber.log.Timber

/**
 * Dialog for the bookmarks import feature
 */
class BookmarksImportDialogFragment : BaseDialogFragment<Parent>() {

    private lateinit var site: Site

    companion object {
        private const val KEY_SITE = "site"

        fun invoke(fragment: Fragment, site: Site) {
            val args = Bundle()
            args.putInt(KEY_SITE, site.code)
            invoke(fragment, BookmarksImportDialogFragment(), args)
        }
    }


    private var binding: DialogWebBookmarksImportBinding? = null


    private val pickFile = registerForActivityResult(PickFileContract()) { result ->
        onFilePickerResult(result.first, result.second)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkNotNull(arguments) { "No arguments found" }

        arguments?.apply {
            site = Site.searchByCode(getInt(KEY_SITE).toLong())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogWebBookmarksImportBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        binding?.apply {
            importCurrentSite.text = getString(R.string.import_current_site, site.description)
            importSelectFileBtn.setOnClickListener { pickFile.launch(0) }
        }
    }

    private fun onFilePickerResult(resultCode: PickerResult, uri: Uri) {
        binding?.apply {
            when (resultCode) {
                PickerResult.OK -> {
                    // File selected
                    val doc = DocumentFile.fromSingleUri(requireContext(), uri) ?: return
                    importSelectFileBtn.visibility = View.GONE
                    checkFile(doc)
                }

                PickerResult.KO_CANCELED -> Snackbar.make(
                    root,
                    R.string.import_canceled,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                PickerResult.KO_NO_URI -> Snackbar.make(
                    root,
                    R.string.import_invalid,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                PickerResult.KO_OTHER -> Snackbar.make(
                    root,
                    R.string.import_other,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun checkFile(file: DocumentFile) {
        binding?.apply {
            // Display indeterminate progress bar while file is being deserialized
            importFileInvalidText.setText(R.string.checking_file)
            importFileInvalidText.visibility = View.VISIBLE

            lifecycleScope.launch {
                var errorFileName = ""
                val result = withContext(Dispatchers.IO) {
                    try {
                        return@withContext readFile(requireContext(), file)
                    } catch (e: Exception) {
                        Timber.Forest.w(e)
                        errorFileName = file.name ?: ""
                    }
                    return@withContext emptyList<SiteBookmark>()
                }
                coroutineScope {
                    if (errorFileName.isEmpty()) onFileRead(result, file)
                    else {
                        importFileInvalidText.text = resources.getString(
                            R.string.import_file_invalid,
                            errorFileName
                        )
                    }
                }
            }
        }
    }

    private fun readFile(context: Context, file: DocumentFile): List<SiteBookmark> {
        getInputStream(context, file).use { return parseBookmarks(it) }
    }

    private fun onFileRead(
        bookmarks: List<SiteBookmark>,
        jsonFile: DocumentFile
    ) {
        binding?.apply {
            importFileInvalidText.visibility = View.GONE
            if (bookmarks.isEmpty()) {
                importFileInvalidText.text =
                    resources.getString(R.string.import_file_invalid, jsonFile.name)
                importFileInvalidText.visibility = View.VISIBLE
            } else {
                importSelectFileBtn.visibility = View.GONE
                importFileInvalidText.visibility = View.GONE
                importFileValidText.text = resources.getQuantityString(
                    R.plurals.import_bookmarks_found,
                    bookmarks.size,
                    bookmarks.size
                )
                importFileValidText.visibility = View.VISIBLE
                importRunBtn.visibility = View.VISIBLE
                importRunBtn.setOnClickListener {
                    val filterSite = if (importCurrentSite.isChecked) site else Site.ANY
                    val validBookmarks =
                        bookmarks.filter { filterSite == Site.ANY || it.site == filterSite }
                    runImport(validBookmarks)
                }
                importRunBtn.isEnabled = true
            }
        }
    }

    private fun runImport(bookmarks: List<SiteBookmark>) {
        isCancelable = false
        binding?.apply {
            importRunBtn.visibility = View.GONE
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val dao: CollectionDAO = ObjectBoxDAO()
                    try {
                        importBookmarks(dao, bookmarks)
                    } finally {
                        dao.cleanup()
                    }
                }
                dismissAllowingStateLoss()
            }
        }
    }

    interface Parent {
        fun onLoaded()
    }
}