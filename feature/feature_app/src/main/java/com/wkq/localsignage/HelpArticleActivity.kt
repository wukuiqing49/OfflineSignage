package com.wkq.localsignage

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.Toast
import com.wkq.base.activity.BaseActivity
import com.wkq.localsignage.feature.app.R
import com.wkq.localsignage.feature.app.databinding.ActivityLegalDocumentBinding

class HelpArticleActivity : BaseActivity<ActivityLegalDocumentBinding>() {
    override fun initView() {
        enableEdgeToEdgeSystemBars(binding.toolbarContainer)
        binding.toolbarContainer.applySystemBarPadding(horizontal = true)
        binding.documentScroll.applySystemBarPadding(bottom = true, horizontal = true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        val articleType = intent.getStringExtra(EXTRA_ARTICLE)
        val article = articleResources(articleType)
        binding.toolbar.setTitle(article.title)
        binding.toolbar.wrapTitle()
        binding.documentBody.setText(article.body)
        binding.documentVisualCard.visibility = View.VISIBLE
        binding.documentVisual.setImageResource(article.visual)
        binding.documentVisualCaption.setText(article.visualCaption)

        when (articleType) {
            ARTICLE_AUTOSTART -> {
                binding.documentActionButton.visibility = View.VISIBLE
                binding.documentActionButton.setText(R.string.help_open_autostart_settings)
                binding.documentActionButton.setOnClickListener { openSystemAutoStartSettings() }
            }
            ARTICLE_ENTERPRISE_DEPLOYMENT -> {
                binding.documentActionButton.visibility = View.VISIBLE
                binding.documentActionButton.setText(R.string.enterprise_contact_action)
                binding.documentActionButton.setOnClickListener { contactDeveloper() }
            }
            else -> {
                binding.documentActionButton.visibility = View.GONE
            }
        }
        if (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
        ) {
            binding.documentScroll.isFocusable = true
            binding.documentScroll.isFocusableInTouchMode = true
            binding.documentActionButton.post {
                if (binding.documentActionButton.visibility == View.VISIBLE) {
                    binding.documentActionButton.requestFocus()
                } else {
                    binding.documentScroll.requestFocus()
                }
            }
        }
    }

    override fun initData() = Unit

    private fun openSystemAutoStartSettings() {
        val pkg = packageName
        val intents = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", pkg, null)),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            val launched = runCatching {
                startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return
        }
    }

    private fun contactDeveloper() {
        val email = getString(R.string.enterprise_contact_email)
        val contactIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.enterprise_contact_subject))
        }
        if (contactIntent.resolveActivity(packageManager) == null) {
            Toast.makeText(
                this,
                getString(R.string.enterprise_contact_unavailable, email),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        startActivity(contactIntent)
    }

    private fun articleResources(article: String?): HelpArticle = when (article) {
        ARTICLE_CONTENT -> HelpArticle(
            R.string.help_content_title,
            R.string.help_content_body,
            R.drawable.illustration_help_content,
            R.string.help_visual_content_caption
        )
        ARTICLE_HTML -> HelpArticle(
            R.string.help_html_title,
            R.string.help_html_body,
            R.drawable.illustration_help_html,
            R.string.help_visual_html_caption
        )
        ARTICLE_PLAYBACK -> HelpArticle(
            R.string.help_playback_title,
            R.string.help_playback_body,
            R.drawable.illustration_help_content,
            R.string.help_visual_content_caption
        )
        ARTICLE_MULTI_DEVICE -> HelpArticle(
            R.string.help_multi_device_title,
            R.string.help_multi_device_body,
            R.drawable.illustration_help_devices,
            R.string.help_visual_devices_caption
        )
        ARTICLE_AUTOSTART -> HelpArticle(
            R.string.help_autostart_title,
            R.string.help_autostart_body_android15,
            R.drawable.illustration_help_devices,
            R.string.help_visual_autostart_caption
        )
        ARTICLE_TROUBLESHOOTING -> HelpArticle(
            R.string.help_troubleshooting_title,
            R.string.help_troubleshooting_body,
            R.drawable.illustration_help_pairing,
            R.string.help_visual_pairing_caption
        )
        ARTICLE_PURCHASE -> HelpArticle(
            R.string.help_purchase_title,
            R.string.help_purchase_body,
            R.drawable.illustration_help_billing,
            R.string.help_visual_billing_caption
        )
        ARTICLE_ENTERPRISE_DEPLOYMENT -> HelpArticle(
            R.string.enterprise_contact_title,
            R.string.help_enterprise_deployment_body,
            R.drawable.illustration_help_devices,
            R.string.help_visual_enterprise_caption
        )
        else -> HelpArticle(
            R.string.help_quick_start_title,
            R.string.help_quick_start_body,
            R.drawable.illustration_help_pairing,
            R.string.help_visual_pairing_caption
        )
    }

    private data class HelpArticle(
        val title: Int,
        val body: Int,
        val visual: Int,
        val visualCaption: Int
    )

    companion object {
        const val ARTICLE_QUICK_START = "quick_start"
        const val ARTICLE_CONTENT = "content"
        const val ARTICLE_HTML = "html"
        const val ARTICLE_PLAYBACK = "playback"
        const val ARTICLE_MULTI_DEVICE = "multi_device"
        const val ARTICLE_AUTOSTART = "autostart"
        const val ARTICLE_TROUBLESHOOTING = "troubleshooting"
        const val ARTICLE_PURCHASE = "purchase"
        const val ARTICLE_ENTERPRISE_DEPLOYMENT = "enterprise_deployment"
        private const val EXTRA_ARTICLE = "article"

        fun intent(context: Context, article: String): Intent =
            Intent(context, HelpArticleActivity::class.java).putExtra(EXTRA_ARTICLE, article)
    }
}
