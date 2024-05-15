package me.devsaki.hentoid.fragments.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.databinding.DialogPrefsStorageBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.file.MemoryUsageFigures
import me.devsaki.hentoid.util.file.formatHumanReadableSize
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString

class StorageUsageDialogFragment : BaseDialogFragment<Nothing>() {
    companion object {
        fun invoke(activity: FragmentActivity) {
            invoke(activity, StorageUsageDialogFragment())
        }
    }

    // == UI
    private var binding: DialogPrefsStorageBinding? = null

    private var rowPadding = 0


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        binding = DialogPrefsStorageBinding.inflate(inflater, container, false)
        return binding!!.root
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

        val primaryMemUsage: Map<Site, Pair<Int, Long>>
        val externalMemUsage: Map<Site, Pair<Int, Long>>
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            primaryMemUsage = dao.selectPrimaryMemoryUsagePerSource()
            externalMemUsage = dao.selectExternalMemoryUsagePerSource()
        } finally {
            dao.cleanup()
        }
        val primaryUsageBytes = primaryMemUsage.map { e -> e.value.second }.sum()
        val externalUsageBytes = externalMemUsage.map { e -> e.value.second }.sum()

        binding?.apply {
            graphDevice.progress = (100 - deviceFreeBytes * 100f / deviceTotalBytes).toInt()
            graphExternal.progress = (externalUsageBytes * 100f / deviceTotalBytes).toInt()
            graphPrimary.progress = (primaryUsageBytes * 100f / deviceTotalBytes).toInt()

            memoryTotalTxt.text = resources.getString(
                R.string.memory_total,
                formatHumanReadableSize(deviceTotalBytes, resources)
            )
            memoryFreeTxt.text = resources.getString(
                R.string.memory_free, formatHumanReadableSize(deviceFreeBytes, resources)
            )

            memoryHentoidPrimaryTxt.text = resources.getString(
                R.string.memory_hentoid_main,
                formatHumanReadableSize(primaryUsageBytes, resources)
            )

            memoryHentoidExtTxt.isVisible = externalUsageBytes > 0
            memoryHentoidExtColor.isVisible = externalUsageBytes > 0

            memoryHentoidExtTxt.text = resources.getString(
                R.string.memory_hentoid_ext,
                formatHumanReadableSize(externalUsageBytes, resources)
            )

            val table = memoryDetailsTable
            addRow(
                table,
                resources.getString(R.string.memory_details_source),
                resources.getString(R.string.memory_details_books),
                resources.getString(R.string.memory_details_size)
            )

            // Sort sources by largest size
            val sitesBySize = primaryMemUsage.toList().sortedBy { e -> -e.second.second }
            sitesBySize.forEach {
                addRow(
                    memoryDetailsTable,
                    it.first.description,
                    it.second.first.toString() + "",
                    formatHumanReadableSize(it.second.second, resources)
                )
            }

            // Make details fold/unfold
            memoryDetails.setOnClickListener { onDetailsClick() }

            val dbMaxSizeKb = Preferences.getMaxDbSizeKb()
            memoryDb.text =
                resources.getString(
                    R.string.memory_database, formatHumanReadableSize(
                        dao.getDbSizeBytes(),
                        resources
                    ), dao.getDbSizeBytes() * 100 / 1024f / dbMaxSizeKb
                )
        }
    }

    private fun getStats(location: StorageLocation): Pair<Long, Long> {
        val root = Preferences.getStorageUri(location)
        if (root.isNotEmpty()) {
            val rootFolder = getDocumentFromTreeUriString(requireActivity(), root)
            if (rootFolder != null) {
                val memUsage = MemoryUsageFigures(requireContext(), rootFolder)
                return Pair(memUsage.getfreeUsageBytes(), memUsage.totalSpaceBytes)
            }
        }
        return Pair(-1, -1)
    }

    private fun onDetailsClick() {
        binding?.apply {
            if (View.VISIBLE == memoryDetailsTable.visibility) {
                memoryDetailsIcon.setImageResource(R.drawable.ic_drop_down)
                memoryDetailsTable.visibility = View.GONE
            } else {
                memoryDetailsIcon.setImageResource(R.drawable.ic_drop_up)
                memoryDetailsTable.visibility = View.VISIBLE
            }
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
}