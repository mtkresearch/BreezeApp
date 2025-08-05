package com.mtkresearch.breezeapp.presentation.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.databinding.ActivitySettingsBinding
import com.mtkresearch.breezeapp.presentation.common.base.BaseActivity
import com.mtkresearch.breezeapp.presentation.settings.fragment.AppSettingsFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, AppSettingsFragment.newInstance())
                .commitNowAllowingStateLoss()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.title_settings)
        }
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }
}
