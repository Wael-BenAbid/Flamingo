package com.example.flamingoandroid.presentation.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.flamingoandroid.MainActivity
import com.example.flamingoandroid.R
import com.example.flamingoandroid.data.firebase.FirebaseService
import com.example.flamingoandroid.databinding.ActivityHomeBinding
import com.example.flamingoandroid.presentation.fragments.DashboardFragment
import com.example.flamingoandroid.presentation.fragments.DailyCheckFragment
import com.example.flamingoandroid.presentation.fragments.InventoryFragment
import com.example.flamingoandroid.presentation.fragments.DailyOrdersFragment
import com.example.flamingoandroid.presentation.fragments.KitchenOrdersFragment
import com.example.flamingoandroid.presentation.fragments.ReportsFragment
import com.example.flamingoandroid.presentation.fragments.ReservationsFragment
import com.example.flamingoandroid.presentation.fragments.SettingsFragment
import com.example.flamingoandroid.presentation.fragments.WorkersFragment
import com.example.flamingoandroid.presentation.activities.TableOrderingActivity
import com.example.flamingoandroid.presentation.access.StaffAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val firebaseService = FirebaseService()
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var currentRole: String = StaffAccess.ROLE_NONE
    private var currentSection: AdminSection = AdminSection.DASHBOARD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.app_name,
            R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        setupDrawerHeader()
        setupNavigation()
        applyRolePermissions()

        // Ne pas ouvrir de section avant que le rôle soit résolu (évite le flash Dashboard).
        // applyRolePermissions() ouvre la section correcte une fois le rôle connu.
    }

    private fun setupDrawerHeader() {
        val header = binding.navigationView.getHeaderView(0)
        val user = firebaseService.getCurrentUser()
        val nameView = header.findViewById<android.widget.TextView>(R.id.tvDrawerUserName)
        val emailView = header.findViewById<android.widget.TextView>(R.id.tvDrawerUserEmail)
        val roleView = header.findViewById<android.widget.TextView>(R.id.tvDrawerUserRole)

        nameView.text = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")
            ?: "Admin"
        emailView.text = user?.email ?: "admin@flamingo.com"
        roleView.text = StaffAccess.roleLabel(currentRole)
    }

    private fun setupNavigation() {
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_dashboard -> openSection(AdminSection.DASHBOARD)
                R.id.nav_reservations -> openSection(AdminSection.RESERVATIONS)
                R.id.nav_daily_check -> openSection(AdminSection.DAILY_CHECK)
                R.id.nav_workers -> openSection(AdminSection.WORKERS)
                R.id.nav_inventory -> openSection(AdminSection.INVENTORY)
                R.id.nav_menu_orders -> startActivity(
                    Intent(this, TableOrderingActivity::class.java)
                        .putExtra(TableOrderingActivity.EXTRA_SERVER_ID, firebaseService.getCurrentUser()?.uid.orEmpty())
                        .putExtra(TableOrderingActivity.EXTRA_SERVER_NAME, firebaseService.getCurrentUser()?.displayName ?: "Personnel")
                )
                R.id.nav_payment -> startActivity(Intent(this, PaymentActivity::class.java))
                R.id.nav_kitchen_orders -> openSection(AdminSection.KITCHEN_ORDERS)
                R.id.nav_daily_orders -> openSection(AdminSection.DAILY_ORDERS)
                R.id.nav_reports -> openSection(AdminSection.REPORTS)
                R.id.nav_settings -> openSection(AdminSection.SETTINGS)
                R.id.nav_logout -> logout()
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun applyRolePermissions() {
        lifecycleScope.launch {
            val role = withContext(Dispatchers.IO) {
                firebaseService.getCurrentUserRole(firebaseService.getCurrentUser()) ?: "admin"
            }.normalizeStaffRole()
            currentRole = role
            updateDrawerHeaderRole(role)

            val menu = binding.navigationView.menu
            val canViewDashboard = StaffAccess.hasFeature(role, StaffAccess.FEATURE_DASHBOARD)
            val canViewReservations = StaffAccess.hasFeature(role, StaffAccess.FEATURE_RESERVATIONS)
            val canViewArrivals = StaffAccess.hasFeature(role, StaffAccess.FEATURE_ARRIVALS)
            val canViewWorkers = StaffAccess.hasFeature(role, StaffAccess.FEATURE_WORKERS)
            val canViewStock = StaffAccess.hasFeature(role, StaffAccess.FEATURE_STOCK)
            val canViewMenuOrders = StaffAccess.hasFeature(role, StaffAccess.FEATURE_PLACE_ORDER)
            val canViewPayment    = StaffAccess.hasFeature(role, StaffAccess.FEATURE_PAYMENT)
            val canViewKitchenOrders = StaffAccess.hasFeature(role, StaffAccess.FEATURE_KITCHEN_ORDERS)
            val canViewReports = StaffAccess.hasFeature(role, StaffAccess.FEATURE_REPORTS)
            val canViewDailyOrders = StaffAccess.hasFeature(role, StaffAccess.FEATURE_DAILY_ORDERS)
            val canViewSettings = StaffAccess.hasFeature(role, StaffAccess.FEATURE_MENU_TABLES) || StaffAccess.hasFeature(role, StaffAccess.FEATURE_SETTINGS)

            menu.findItem(R.id.nav_dashboard)?.isVisible = canViewDashboard
            menu.findItem(R.id.nav_reservations)?.isVisible = canViewReservations
            menu.findItem(R.id.nav_daily_check)?.isVisible = canViewArrivals
            menu.findItem(R.id.nav_workers)?.isVisible = canViewWorkers
            menu.findItem(R.id.nav_inventory)?.isVisible = canViewStock
            menu.findItem(R.id.nav_menu_orders)?.isVisible = canViewMenuOrders
            menu.findItem(R.id.nav_payment)?.isVisible = canViewPayment
            menu.findItem(R.id.nav_kitchen_orders)?.isVisible = canViewKitchenOrders
            menu.findItem(R.id.nav_daily_orders)?.isVisible = canViewDailyOrders
            menu.findItem(R.id.nav_reports)?.isVisible = canViewReports
            menu.findItem(R.id.nav_settings)?.isVisible = canViewSettings

            val defaultSection = StaffAccess.defaultSection(role)
            openSection(defaultSection)
            binding.navigationView.setCheckedItem(
                when (defaultSection) {
                    AdminSection.DASHBOARD -> R.id.nav_dashboard
                    AdminSection.RESERVATIONS -> R.id.nav_reservations
                    AdminSection.DAILY_CHECK -> R.id.nav_daily_check
                    AdminSection.WORKERS -> R.id.nav_workers
                    AdminSection.INVENTORY -> R.id.nav_inventory
                    AdminSection.REPORTS -> R.id.nav_reports
                    AdminSection.DAILY_ORDERS -> R.id.nav_daily_orders
                    AdminSection.SETTINGS -> R.id.nav_settings
                    AdminSection.KITCHEN_ORDERS -> R.id.nav_kitchen_orders
                }
            )
        }
    }

    private fun String.normalizeStaffRole(): String {
        return StaffAccess.normalize(this)
    }

    private fun updateDrawerHeaderRole(role: String) {
        val header = binding.navigationView.getHeaderView(0)
        val roleView = header.findViewById<android.widget.TextView>(R.id.tvDrawerUserRole)
        roleView.text = StaffAccess.roleLabel(role)
    }

    fun openSection(section: AdminSection) {
        val fragment = when (section) {
            AdminSection.DASHBOARD -> DashboardFragment()
            AdminSection.RESERVATIONS -> ReservationsFragment()
            AdminSection.DAILY_CHECK -> DailyCheckFragment()
            AdminSection.WORKERS -> WorkersFragment()
            AdminSection.INVENTORY -> InventoryFragment()
            AdminSection.REPORTS -> ReportsFragment()
            AdminSection.DAILY_ORDERS -> DailyOrdersFragment()
            AdminSection.SETTINGS -> SettingsFragment()
            AdminSection.KITCHEN_ORDERS -> KitchenOrdersFragment()
        }

        if (section == AdminSection.DASHBOARD) {
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
        if (section != AdminSection.DASHBOARD) {
            transaction.addToBackStack(section.name)
        }
        transaction.commit()

        currentSection = section

        supportActionBar?.title = when (section) {
            AdminSection.DASHBOARD -> "Dashboard"
            AdminSection.RESERVATIONS -> "Réservations"
            AdminSection.DAILY_CHECK -> "Arrivées"
            AdminSection.WORKERS -> "Travailleurs"
            AdminSection.INVENTORY -> "Gestion Stock"
            AdminSection.REPORTS -> "Bilans Journaliers"
            AdminSection.DAILY_ORDERS -> "Commandes du Jour"
            AdminSection.SETTINGS -> "Menus & Tables"
            AdminSection.KITCHEN_ORDERS -> "Commandes Cuisine"
        }
    }

    fun logout() {
        firebaseService.signOut()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else if (currentSection != AdminSection.DASHBOARD) {
            openSection(AdminSection.DASHBOARD)
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("Quitter l'application ?")
                .setMessage("Voulez-vous vraiment quitter Flamingo Android ?")
                .setPositiveButton("Quitter") { _, _ ->
                    finishAffinity()
                }
                .setNegativeButton("Rester", null)
                .show()
        }
    }
}
