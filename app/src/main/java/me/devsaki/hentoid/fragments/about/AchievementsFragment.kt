package me.devsaki.hentoid.fragments.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.FragmentAboutAchievementsBinding
import me.devsaki.hentoid.util.AchievementsManager
import me.devsaki.hentoid.viewholders.AchievementItem

class AchievementsFragment : Fragment(R.layout.fragment_about_achievements) {

    private var binding: FragmentAboutAchievementsBinding? = null

    private val itemAdapter = ItemAdapter<AchievementItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAboutAchievementsBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.apply {
            recyclerView.setHasFixedSize(true)

            AchievementsManager.masterdata.values.forEach { ac ->
                itemAdapter.add(AchievementItem(ac, AchievementsManager.isRegistered(ac.id)))
            }

            recyclerView.adapter = fastAdapter
        }
    }
}