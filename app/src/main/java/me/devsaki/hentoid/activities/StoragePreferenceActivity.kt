package me.devsaki.hentoid.activities

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import me.devsaki.hentoid.databinding.ActivityRulesBinding
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.viewmodels.RulesEditViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory

class StoragePreferenceActivity : BaseActivity() {

    // == UI
    private var binding: ActivityRulesBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)

        binding = ActivityRulesBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        /*
        val vmFactory = ViewModelFactory(application)
        viewModel = ViewModelProvider(this, vmFactory)[RulesEditViewModel::class.java]

        bindUI()
        bindInteractions()

        viewModel.getRules().observe(this) { this.onRulesChanged(it) }
        viewModel.getAttributeTypeFilter().observe(this) { attributeTypeFilter = it }
         */
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }
}