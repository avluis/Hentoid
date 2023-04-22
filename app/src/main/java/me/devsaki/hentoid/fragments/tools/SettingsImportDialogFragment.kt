package me.devsaki.hentoid.fragments.tools

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.DialogToolsSettingsImportBinding
import me.devsaki.hentoid.json.JsonSettings
import me.devsaki.hentoid.util.ImportHelper
import me.devsaki.hentoid.util.ImportHelper.PickFileContract
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.Preferences
import org.apache.commons.lang3.tuple.ImmutablePair
import timber.log.Timber
import java.io.IOException

/**
 * Dialog for the settings metadata import feature
 */
class SettingsImportDialogFragment : DialogFragment() {
    private var binding: DialogToolsSettingsImportBinding? = null
    private var dismissHandler: Handler? = null


    private val pickFile = registerForActivityResult(
        PickFileContract()
    ) { result: ImmutablePair<Int, Uri> ->
        onFilePickerResult(result.left, result.right)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogToolsSettingsImportBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        binding?.apply {
            selectFileBtn.setOnClickListener { pickFile.launch(0) }
        }
    }

    override fun onDestroyView() {
        dismissHandler?.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }

    private fun onFilePickerResult(resultCode: Int, uri: Uri) {
        binding?.apply {
            when (resultCode) {
                ImportHelper.PickerResult.OK -> {
                    // File selected
                    val doc = DocumentFile.fromSingleUri(requireContext(), uri) ?: return
                    selectFileBtn.visibility = View.GONE
                    checkFile(doc)
                }

                ImportHelper.PickerResult.KO_CANCELED -> Snackbar.make(
                    root,
                    R.string.import_canceled,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                ImportHelper.PickerResult.KO_NO_URI -> Snackbar.make(
                    root,
                    R.string.import_invalid,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                ImportHelper.PickerResult.KO_OTHER -> Snackbar.make(
                    root,
                    R.string.import_other,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                else -> {}
            }
        }
    }

    private fun checkFile(jsonFile: DocumentFile) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    deserialiseJson(jsonFile)
                }
                onFileDeserialized(result, jsonFile)
            } catch (e: Exception) {
                binding?.apply {
                    importFileInvalidText.text = resources.getString(
                        R.string.import_file_invalid,
                        jsonFile.name
                    )
                    importFileInvalidText.visibility = View.VISIBLE
                }
                Timber.w(e)
            }
        }
    }

    private fun onFileDeserialized(
        collection: JsonSettings?,
        jsonFile: DocumentFile
    ) {
        binding?.apply {
            if (null == collection || collection.settings.isEmpty()) {
                importFileInvalidText.text =
                    resources.getString(R.string.import_file_invalid, jsonFile.name)
                importFileInvalidText.visibility = View.VISIBLE
            } else {
                selectFileBtn.visibility = View.GONE
                importFileInvalidText.visibility = View.GONE
                runImport(collection)
            }
        }
    }

    private fun deserialiseJson(jsonFile: DocumentFile): JsonSettings? {
        val result: JsonSettings = try {
            JsonHelper.jsonToObject(requireContext(), jsonFile, JsonSettings::class.java)
        } catch (e: IOException) {
            Timber.w(e)
            return null
        }
        return result
    }

    private fun runImport(settings: JsonSettings) {
        isCancelable = false
        Preferences.importInformation(settings.settings)
        finish()
    }

    private fun finish() {
        binding?.apply {
            Snackbar.make(
                root,
                resources.getString(R.string.import_settings_success),
                BaseTransientBottomBar.LENGTH_LONG
            ).show()
        }

        // Dismiss after 3s, for the user to be able to see the snackbar
        dismissHandler = Handler(Looper.getMainLooper())
        dismissHandler!!.postDelayed({ dismiss() }, 3000)
    }

    companion object {
        fun invoke(fragmentManager: FragmentManager) {
            val fragment = SettingsImportDialogFragment()
            fragment.show(fragmentManager, null)
        }
    }
}