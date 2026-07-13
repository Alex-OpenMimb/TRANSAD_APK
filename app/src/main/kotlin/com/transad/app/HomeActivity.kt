package com.transad.app

import android.widget.TextView
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.transad.app.api.ApiClient
import com.transad.app.api.AppVersionInfo
import com.transad.app.api.AppVersionResponse
import com.transad.app.api.Business
import com.transad.app.api.BusinessesResponse
import com.transad.app.api.BusinessStats
import com.transad.app.api.SelectBusinessRequest
import com.transad.app.api.SelectBusinessResponse
import com.transad.app.api.StatsResponse
import com.transad.app.api.User
import com.transad.app.api.MeResponse
import com.transad.app.databinding.ActivityHomeBinding
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var refreshAckPending = 0

    private var apkUpdateInstaller: ApkUpdateInstaller? = null
    private var pendingUpdateVersion: AppVersionInfo? = null

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
            pendingUpdateVersion?.let(::beginUpdateDownload)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!ensureValidSession()) return

        checkForAppUpdate()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.menu_inicio,
            R.string.menu_ordenes
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.drawerLayout.setScrimColor(getColor(R.color.nav_drawer_scrim))

        binding.navView.menu.findItem(R.id.nav_api_logs)?.isVisible = BuildConfig.DEBUG
        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_inicio -> binding.drawerLayout.closeDrawer(GravityCompat.START)
                R.id.nav_ordenes -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, OrdersActivity::class.java))
                }
                R.id.nav_inventario -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, InventoryActivity::class.java))
                }
                R.id.nav_inspeccion -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, InspectionActivity::class.java))
                }
                R.id.nav_etiquetado -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, LabelingActivity::class.java))
                }
                R.id.nav_cambiar_empresa -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    showBusinessSelectorDialog()
                }
                R.id.nav_perfil -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show()
                }
                R.id.nav_api_logs -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, ApiLogsActivity::class.java))
                }
                R.id.nav_logout -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    cerrarSesion()
                }
            }
            true
        }

        bindUserInfoFallback()
        loadUserProfile()
        loadBusinessStats()
        loadAvailableBusinesses()
        setupQuickActions()
    }

    /** Solo tiene sentido mostrar el selector si el usuario tiene acceso a más de una empresa. */
    private fun loadAvailableBusinesses() {
        ApiClient.authApi.getBusinesses().enqueue(object : Callback<BusinessesResponse> {
            override fun onResponse(call: Call<BusinessesResponse>, response: Response<BusinessesResponse>) {
                val businesses = response.takeIf { it.isSuccessful }?.body()?.data.orEmpty()
                binding.navView.menu.findItem(R.id.nav_cambiar_empresa)?.isVisible = businesses.size > 1
            }

            override fun onFailure(call: Call<BusinessesResponse>, t: Throwable) = Unit
        })
    }

    private fun showBusinessSelectorDialog() {
        ApiClient.authApi.getBusinesses().enqueue(object : Callback<BusinessesResponse> {
            override fun onResponse(call: Call<BusinessesResponse>, response: Response<BusinessesResponse>) {
                if (!response.isSuccessful) {
                    ApiErrorUi.showHttpError(this@HomeActivity, getString(R.string.business_load_error), response)
                    return
                }
                val businesses = response.body()?.data.orEmpty()
                if (businesses.size <= 1) return
                renderBusinessSelectorDialog(businesses)
            }

            override fun onFailure(call: Call<BusinessesResponse>, t: Throwable) {
                ApiErrorUi.showNetworkError(this@HomeActivity, getString(R.string.business_load_error), t)
            }
        })
    }

    private fun renderBusinessSelectorDialog(businesses: List<Business>) {
        val labels = businesses.map { business ->
            business.nit?.trim()?.takeIf { it.isNotEmpty() }?.let { "${business.name} ($it)" } ?: business.name
        }.toTypedArray()
        val checkedIndex = businesses.indexOfFirst { it.isCurrent }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.business_selector_title)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                dialog.dismiss()
                val selected = businesses[which]
                if (!selected.isCurrent) selectBusiness(selected)
            }
            .setNegativeButton(R.string.rfid_detail_cancel, null)
            .show()
    }

    private fun selectBusiness(business: Business) {
        ApiClient.authApi.selectBusiness(SelectBusinessRequest(businessId = business.id))
            .enqueue(object : Callback<SelectBusinessResponse> {
                override fun onResponse(call: Call<SelectBusinessResponse>, response: Response<SelectBusinessResponse>) {
                    if (!response.isSuccessful) {
                        ApiErrorUi.showHttpError(this@HomeActivity, getString(R.string.business_select_error), response)
                        return
                    }
                    Toast.makeText(
                        this@HomeActivity,
                        getString(R.string.business_select_ok, business.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    recreate()
                }

                override fun onFailure(call: Call<SelectBusinessResponse>, t: Throwable) {
                    ApiErrorUi.showNetworkError(this@HomeActivity, getString(R.string.business_select_error), t)
                }
            })
    }

    override fun onDestroy() {
        apkUpdateInstaller?.unregister()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_refresh_home) {
            refreshHome()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshHome() {
        refreshAckPending = 2
        loadUserProfile(showRefreshAck = true)
        loadBusinessStats(showRefreshAck = true)
    }

    private fun onRefreshLoadFinished(showRefreshAck: Boolean) {
        if (!showRefreshAck) return
        refreshAckPending--
        if (refreshAckPending <= 0) {
            Toast.makeText(this, R.string.home_refreshed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindUserInfoFallback() {
        val username = SessionManager(this).getUsername().ifBlank { getString(R.string.home_user_guest) }
        binding.tvHomeWelcome.text = getString(R.string.home_welcome, username)
        binding.tvHomeDisplayName.text = username
        binding.tvHomeUsername.text = username
        binding.tvHomeUserInitials.text = initialsFrom(username)
        bindNavDrawerUser(username, "")
    }

    private fun bindNavDrawerUser(name: String, email: String) {
        val header = binding.navView.getHeaderView(0)
        header.findViewById<TextView>(R.id.tvNavUserName).text = name
        header.findViewById<TextView>(R.id.tvNavUserEmail).text = email
        header.findViewById<TextView>(R.id.tvNavUserEmail).visibility =
            if (email.isBlank()) View.GONE else View.VISIBLE
        header.findViewById<TextView>(R.id.tvNavAppVersion).text = getString(
            R.string.nav_app_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
    }

    private fun loadUserProfile(showRefreshAck: Boolean = false) {
        binding.progressUserProfile.visibility = View.VISIBLE
        binding.userProfileContent.alpha = 0.45f

        ApiClient.authApi.getMe().enqueue(object : Callback<MeResponse> {
            override fun onResponse(call: Call<MeResponse>, response: Response<MeResponse>) {
                binding.progressUserProfile.visibility = View.GONE
                binding.userProfileContent.alpha = 1f
                val user = response.body()?.resolveUser()
                if (response.isSuccessful && user != null) {
                    bindUserProfile(user)
                } else if (!response.isSuccessful) {
                    ApiErrorUi.showHttpError(
                        this@HomeActivity,
                        getString(R.string.home_profile_error),
                        response
                    )
                }
                onRefreshLoadFinished(showRefreshAck)
            }

            override fun onFailure(call: Call<MeResponse>, t: Throwable) {
                binding.progressUserProfile.visibility = View.GONE
                binding.userProfileContent.alpha = 1f
                ApiErrorUi.showNetworkError(
                    this@HomeActivity,
                    getString(R.string.home_profile_error),
                    t
                )
                onRefreshLoadFinished(showRefreshAck)
            }
        })
    }

    private fun bindUserProfile(user: User) {
        binding.tvHomeWelcome.text = getString(R.string.home_welcome, user.greetingName())
        binding.tvHomeDisplayName.text = user.displayName()
        binding.tvHomeUserInitials.text = initialsFrom(user.displayName())

        val usernameLabel = user.usernameLabel()
        if (usernameLabel.isNotEmpty()) {
            binding.tvHomeUsername.visibility = View.VISIBLE
            binding.tvHomeUsername.text = usernameLabel
        } else {
            binding.tvHomeUsername.visibility = View.GONE
        }

        binding.chipUserStatus.visibility = View.VISIBLE
        binding.chipUserStatus.text = if (user.status) {
            getString(R.string.home_user_status_active)
        } else {
            getString(R.string.home_user_status_inactive)
        }
        binding.chipUserStatus.setChipBackgroundColorResource(
            if (user.status) R.color.status_active else R.color.status_inactive
        )

        if (user.email.isNotBlank()) {
            binding.tvHomeEmail.visibility = View.VISIBLE
            binding.tvHomeEmail.text = user.email
        } else {
            binding.tvHomeEmail.visibility = View.GONE
        }

        val phone = user.phoneNumber?.trim().orEmpty()
        if (phone.isNotEmpty()) {
            binding.tvHomePhone.visibility = View.VISIBLE
            binding.tvHomePhone.text = getString(R.string.home_profile_phone, phone)
        } else {
            binding.tvHomePhone.visibility = View.GONE
        }

        val document = user.documentLabel()
        if (document != null) {
            binding.tvHomeDocument.visibility = View.VISIBLE
            binding.tvHomeDocument.text = document
        } else {
            binding.tvHomeDocument.visibility = View.GONE
        }

        user.username?.trim()?.takeIf { it.isNotEmpty() }?.let {
            SessionManager(this).updateUsername(it)
        }
        SessionManager(this).saveUserId(user.id)

        bindNavDrawerUser(user.displayName(), user.email)
    }

    private fun loadBusinessStats(showRefreshAck: Boolean = false) {
        binding.progressHomeStats.visibility = View.VISIBLE
        binding.statsContent.alpha = 0.45f

        ApiClient.statsApi.getStats().enqueue(object : Callback<StatsResponse> {
            override fun onResponse(call: Call<StatsResponse>, response: Response<StatsResponse>) {
                binding.progressHomeStats.visibility = View.GONE
                binding.statsContent.alpha = 1f
                if (response.isSuccessful) {
                    response.body()?.data?.let { bindBusinessStats(it) }
                } else {
                    ApiErrorUi.showHttpError(
                        this@HomeActivity,
                        getString(R.string.home_stats_error),
                        response
                    )
                }
                onRefreshLoadFinished(showRefreshAck)
            }

            override fun onFailure(call: Call<StatsResponse>, t: Throwable) {
                binding.progressHomeStats.visibility = View.GONE
                binding.statsContent.alpha = 1f
                ApiErrorUi.showNetworkError(
                    this@HomeActivity,
                    getString(R.string.home_stats_error),
                    t
                )
                onRefreshLoadFinished(showRefreshAck)
            }
        })
    }

    private fun bindBusinessStats(stats: BusinessStats) {
        bindStatTile(
            tile = binding.statVehicles,
            title = getString(R.string.home_stats_vehicles),
            value = stats.vehicles.total,
            detail = getString(
                R.string.home_stats_active_inactive,
                stats.vehicles.active,
                stats.vehicles.inactive
            )
        )
        bindStatTile(
            tile = binding.statOrders,
            title = getString(R.string.home_stats_orders),
            value = stats.orders.total,
            detail = getString(R.string.home_stats_orders_detail)
        )
        bindStatTile(
            tile = binding.statRequisitions,
            title = getString(R.string.home_stats_requisitions),
            value = stats.requisitions.total,
            detail = getString(
                R.string.home_stats_active_inactive,
                stats.requisitions.active,
                stats.requisitions.inactive
            )
        )
        bindStatTile(
            tile = binding.statTires,
            title = getString(R.string.home_stats_tires),
            value = stats.tires.total,
            detail = getString(
                R.string.home_stats_tires_detail,
                stats.tires.installed,
                stats.tires.spares
            )
        )

        binding.statVehicles.root.setOnClickListener {
            startActivity(Intent(this, InspectionActivity::class.java))
        }
        binding.statOrders.root.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
        }
    }

    private fun bindStatTile(
        tile: com.transad.app.databinding.ItemHomeStatCardBinding,
        title: String,
        value: Int,
        detail: String
    ) {
        tile.tvStatTitle.text = title
        tile.tvStatValue.text = value.toString()
        tile.tvStatDetail.text = detail
    }

    private fun initialsFrom(name: String): String {
        val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase()
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> "?"
        }
    }

    private fun setupQuickActions() {
        bindQuickAction(
            card = binding.cardOrders,
            iconRes = R.drawable.ic_quick_orders,
            title = getString(R.string.home_quick_orders_title),
            subtitle = getString(R.string.home_quick_orders_subtitle),
            onClick = { startActivity(Intent(this, OrdersActivity::class.java)) }
        )
        bindQuickAction(
            card = binding.cardInspection,
            iconRes = R.drawable.ic_quick_inspection,
            title = getString(R.string.home_quick_inspection_title),
            subtitle = getString(R.string.home_quick_inspection_subtitle),
            onClick = { startActivity(Intent(this, InspectionActivity::class.java)) }
        )
        bindQuickAction(
            card = binding.cardLabeling,
            iconRes = R.drawable.ic_quick_labeling,
            title = getString(R.string.home_quick_labeling_title),
            subtitle = getString(R.string.home_quick_labeling_subtitle),
            onClick = { startActivity(Intent(this, LabelingActivity::class.java)) }
        )
    }

    private fun bindQuickAction(
        card: com.transad.app.databinding.ItemHomeQuickActionBinding,
        iconRes: Int,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ) {
        card.ivQuickIcon.setImageResource(iconRes)
        card.tvQuickTitle.text = title
        card.tvQuickSubtitle.text = subtitle
        card.root.setOnClickListener { onClick() }
    }

    /** Consulta si hay una versión más nueva del .apk publicada; falla en silencio (no interrumpe el inicio). */
    private fun checkForAppUpdate() {
        ApiClient.appVersionApi.getLatestVersion().enqueue(object : Callback<AppVersionResponse> {
            override fun onResponse(call: Call<AppVersionResponse>, response: Response<AppVersionResponse>) {
                val info = response.takeIf { it.isSuccessful }?.body()?.data ?: return
                if (info.versionCode <= BuildConfig.VERSION_CODE) return
                if (!info.isMandatory && AppUpdatePreferences(this@HomeActivity).isDismissed(info.versionCode)) return
                showUpdateDialog(info)
            }

            override fun onFailure(call: Call<AppVersionResponse>, t: Throwable) = Unit
        })
    }

    private fun showUpdateDialog(info: AppVersionInfo) {
        val changelog = info.changelog?.trim().orEmpty().ifBlank { getString(R.string.app_update_no_changelog) }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_update_dialog_title)
            .setMessage(getString(R.string.app_update_dialog_message, info.versionName, changelog))
            .setCancelable(!info.isMandatory)
            .setPositiveButton(R.string.app_update_action_update) { _, _ -> requestInstallPermissionThenDownload(info) }

        if (!info.isMandatory) {
            dialog.setNegativeButton(R.string.app_update_action_later) { _, _ ->
                AppUpdatePreferences(this).dismiss(info.versionCode)
            }
        }
        dialog.show()
    }

    private fun requestInstallPermissionThenDownload(info: AppVersionInfo) {
        pendingUpdateVersion = info
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, R.string.app_update_install_permission_message, Toast.LENGTH_LONG).show()
            installPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
            return
        }
        beginUpdateDownload(info)
    }

    private fun beginUpdateDownload(info: AppVersionInfo) {
        Toast.makeText(this, R.string.app_update_downloading, Toast.LENGTH_SHORT).show()
        apkUpdateInstaller = ApkUpdateInstaller(this).also { it.startDownload(info) }
    }

    private fun ensureValidSession(): Boolean {
        val session = SessionManager(this)
        if (!session.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return false
        }
        if (!session.isSessionForBaseUrl(ApiClient.BASE_URL)) {
            session.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return false
        }
        return true
    }

    private fun cerrarSesion() {
        ApiClient.authApi.logout().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) = irALogin()
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) = irALogin()
        })
    }

    private fun irALogin() {
        SessionManager(this).logout()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
