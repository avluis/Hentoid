package me.devsaki.hentoid.fragments.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.databinding.DialogPrefsMemoryBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.file.FileHelper.MemoryUsageFigures
import org.apache.commons.lang3.tuple.ImmutablePair

class MemoryUsageDialogFragmentK : DialogFragment(R.layout.dialog_prefs_memory) {
    // == UI
    private var _binding: DialogPrefsMemoryBinding? = null
    private val binding get() = _binding!!

    private var rowPadding = 0


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        _binding = DialogPrefsMemoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        rowPadding = resources.getDimension(R.dimen.mem_row_padding).toInt()

        var deviceFreeBytes: Long = -1
        var deviceTotalBytes: Long = -1

        val rootFolder =
            FileHelper.getDocumentFromTreeUriString(requireActivity(), Preferences.getStorageUri())
        if (rootFolder != null) {
            val memUsage = MemoryUsageFigures(requireContext(), rootFolder)
            deviceFreeBytes = memUsage.getfreeUsageBytes()
            deviceTotalBytes = memUsage.totalSpaceBytes
        }

        val primaryMemUsage: Map<Site, ImmutablePair<Int, Long>>
        val externalMemUsage: Map<Site, ImmutablePair<Int, Long>>
        val dao: CollectionDAO = ObjectBoxDAO(requireContext())
        try {
            primaryMemUsage = dao.selectPrimaryMemoryUsagePerSource()
            externalMemUsage = dao.selectExternalMemoryUsagePerSource()
        } finally {
            dao.cleanup()
        }
        val primaryUsageBytes = primaryMemUsage.map { e -> e.value.right }.sum()
        val externalUsageBytes = externalMemUsage.map { e -> e.value.right }.sum()

        binding.memoryGlobalGraph.let { donut ->
            donut.setTotalColor(R.color.primary_light)
            donut.setProgress1Color(R.color.secondary_light)
            donut.setProgress2Color(R.color.secondary_variant_light)
            donut.setProgress3Color(R.color.white_opacity_25)
            donut.setTotal(1)
            donut.setProgress1(primaryUsageBytes * 1f / deviceTotalBytes) // Size taken by Hentoid primary library
            donut.setProgress2(externalUsageBytes * 1f / deviceTotalBytes) // Size taken by Hentoid external library
            donut.setProgress3(1 - deviceFreeBytes * 1f / deviceTotalBytes) // Total size taken on the device
        }

        binding.memoryTotal.text = resources.getString(
            R.string.memory_total, FileHelper.formatHumanReadableSize(deviceTotalBytes, resources)
        )

        binding.memoryFree.text = resources.getString(
            R.string.memory_free, FileHelper.formatHumanReadableSize(deviceFreeBytes, resources)
        )

        binding.memoryHentoidMain.text = resources.getString(
            R.string.memory_hentoid_main,
            FileHelper.formatHumanReadableSize(primaryUsageBytes, resources)
        )

        binding.memoryHentoidExt.text = resources.getString(
            R.string.memory_hentoid_ext,
            FileHelper.formatHumanReadableSize(externalUsageBytes, resources)
        )

        val table = binding.memoryDetailsTable
        addRow(
            table,
            resources.getString(R.string.memory_details_source),
            resources.getString(R.string.memory_details_books),
            resources.getString(R.string.memory_details_size)
        )

        // Sort sources by largest size
        val sitesBySize = primaryMemUsage.toList().sortedBy { e -> -e.second.right }
        sitesBySize.forEach {
            addRow(
                binding.memoryDetailsTable,
                it.first.description,
                it.second.left.toString() + "",
                FileHelper.formatHumanReadableSize(it.second.right, resources)
            )
        }

        // Make details fold/unfold
        binding.memoryDetails.setOnClickListener { onDetailsClick() }

        val dbMaxSizeKb = Preferences.getMaxDbSizeKb()
        binding.memoryDb.text =
            resources.getString(
                R.string.memory_database, FileHelper.formatHumanReadableSize(
                    dao.dbSizeBytes,
                    resources
                ), dao.dbSizeBytes * 100 / 1024f / dbMaxSizeKb
            )
    }

    private fun onDetailsClick() {
        if (View.VISIBLE == binding.memoryDetailsTable.visibility) {
            binding.memoryDetailsIcon.setImageResource(R.drawable.ic_drop_down)
            binding.memoryDetailsTable.visibility = View.GONE
        } else {
            binding.memoryDetailsIcon.setImageResource(R.drawable.ic_drop_up)
            binding.memoryDetailsTable.visibility = View.VISIBLE
        }
    }

    private fun addRow(table: TableLayout, vararg values: String) {
        val tableParams = TableRow.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1.0f
        )
        val tableRow = TableRow(requireContext())
        tableRow.layoutParams = tableParams
        var column = 1
        for (value in values) {
            val textView = TextView(requireContext())
            textView.layoutParams = TableRow.LayoutParams(column++)
            textView.text = value
            textView.setPadding(rowPadding, rowPadding, rowPadding, rowPadding)
            tableRow.addView(textView)
        }
        table.addView(tableRow)
    }

    companion object {
        fun invoke(fragmentManager: FragmentManager) {
            val fragment = MemoryUsageDialogFragmentK()
            fragment.show(fragmentManager, null)
        }
    }
}