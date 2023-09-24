package me.devsaki.hentoid.fragments.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.databinding.DialogPrefsStorageBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.file.FileHelper.MemoryUsageFigures
import org.apache.commons.lang3.tuple.ImmutablePair

class StorageUsageDialogFragment : DialogFragment(R.layout.dialog_prefs_storage) {
    // == UI
    private var _binding: DialogPrefsStorageBinding? = null
    private val binding get() = _binding!!

    private var rowPadding = 0


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        _binding = DialogPrefsStorageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        rowPadding = resources.getDimension(R.dimen.mem_row_padding).toInt()

        // Get free space and capacity for every location
        val stats1 = getStats(StorageLocation.PRIMARY_1)
        val stats2 = getStats(StorageLocation.PRIMARY_2)
        val statsExt = getStats(StorageLocation.EXTERNAL)

        // Remove duplicates by keeping capacities that are different (-> different volumes)
        val distinctVolumes = listOf(stats1, stats2, statsExt)
            .filterNot { p -> -1L == p.second }
            .distinctBy { p -> p.first.toString() + "." + p.second.toString() }

        val deviceFreeBytes = distinctVolumes.sumOf { p -> p.first }
        val deviceTotalBytes = distinctVolumes.sumOf { p -> p.second }

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

        binding.memoryGlobalGraph.apply {
            setTotalColor(R.color.primary_light)
            setProgress1Color(R.color.secondary_light)
            setProgress2Color(R.color.secondary_variant_light)
            setProgress3Color(R.color.white_opacity_25)
            setTotal(1)
            setProgress1(primaryUsageBytes * 1f / deviceTotalBytes) // Size taken by Hentoid primary library
            setProgress2(externalUsageBytes * 1f / deviceTotalBytes) // Size taken by Hentoid external library
            setProgress3(1 - deviceFreeBytes * 1f / deviceTotalBytes) // Total size taken on the device
        }

        binding.memoryTotalTxt.text = resources.getString(
            R.string.memory_total, FileHelper.formatHumanReadableSize(deviceTotalBytes, resources)
        )

        binding.memoryFreeTxt.text = resources.getString(
            R.string.memory_free, FileHelper.formatHumanReadableSize(deviceFreeBytes, resources)
        )

        binding.memoryHentoidPrimaryTxt.text = resources.getString(
            R.string.memory_hentoid_main,
            FileHelper.formatHumanReadableSize(primaryUsageBytes, resources)
        )

        binding.memoryHentoidExtTxt.isVisible = externalUsageBytes > 0
        binding.memoryHentoidExtColor.isVisible = externalUsageBytes > 0

        binding.memoryHentoidExtTxt.text = resources.getString(
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

    private fun getStats(location: StorageLocation): Pair<Long, Long> {
        val root = Preferences.getStorageUri(location)
        if (root.isNotEmpty()) {
            val rootFolder = FileHelper.getDocumentFromTreeUriString(requireActivity(), root)
            if (rootFolder != null) {
                val memUsage = MemoryUsageFigures(requireContext(), rootFolder)
                return Pair(memUsage.getfreeUsageBytes(), memUsage.totalSpaceBytes)
            }
        }
        return Pair(-1, -1)
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
            val fragment = StorageUsageDialogFragment()
            fragment.show(fragmentManager, null)
        }
    }
}