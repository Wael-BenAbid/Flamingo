package com.example.flamingoandroid.data.repository

import com.example.flamingoandroid.data.models.AdvanceRecord
import com.example.flamingoandroid.data.models.DailyReport
import com.example.flamingoandroid.data.models.DashboardStats
import com.example.flamingoandroid.data.models.PaymentRecord
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ReportRepository — single source of truth for:
 *  • Daily report calculation and persistence
 *  • Dashboard KPI stats
 *
 * Business logic (previously inline in FirebaseService.calculateDailyReport)
 * is now isolated here, making it independently testable.
 */
class ReportRepository(
    private val reservationRepo: ReservationRepository = ReservationRepository(),
    private val workerRepo:      WorkerRepository      = WorkerRepository(),
    private val inventoryRepo:   InventoryRepository   = InventoryRepository(),
) {

    private val db = FirebaseFirestore.getInstance()

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    // ── DAILY REPORT ──────────────────────────────────────────────────

    /** Builds a DailyReport for [date] by aggregating data from all domains. */
    suspend fun calculateDailyReport(date: String): DailyReport = coroutineScope {
        try {
            // Fan-out: all reads run concurrently
            val positionsDeferred     = async { reservationRepo.getPositions() }
            val workersDeferred       = async { workerRepo.getWorkers() }
            val reservationsDeferred  = async { reservationRepo.getReservationsByDate(date) }
            val salesDeferred         = async { inventoryRepo.getSalesByDate(date) }
            val advancesDeferred      = async { getAdvancesByDate(date) }
            val paymentsDeferred      = async { getPaymentsByDate(date) }

            val positions    = positionsDeferred.await()
            val workers      = workersDeferred.await()
            val reservations = reservationsDeferred.await()
            val sales        = salesDeferred.await()
            val advances     = advancesDeferred.await()
            val payments     = paymentsDeferred.await()

            val positionMap = positions.associateBy { it.type.trim().lowercase(Locale.getDefault()) }

            val confirmed = reservations.filter {
                it.status.equals("confirmed", ignoreCase = true) ||
                it.status.equals("checked-in", ignoreCase = true)
            }

            val reservationRevenue = confirmed.sumOf { r ->
                if (r.totalPrice > 0) {
                    r.totalPrice
                } else {
                    val p         = positionMap[r.positionType.trim().lowercase(Locale.getDefault())]
                    val adultPx   = p?.price      ?: 0.0
                    val childPx   = p?.childPrice ?: (adultPx * 0.5)
                    (r.adults * adultPx) + (r.children * childPx)
                }
            }

            val totalSalesRevenue  = sales.sumOf { it.totalPrice }
            val totalSalesCost     = sales.sumOf { it.totalCost }
            val totalAdvances      = advances.sumOf { it.amount }
            val totalPayments      = payments.sumOf { it.amount }
            val totalRevenue       = reservationRevenue + totalSalesRevenue
            val totalExpenses      = totalSalesCost + totalAdvances + totalPayments
            val totalClients       = confirmed.sumOf { it.adults + it.children }
            val totalUnitsSold     = sales.sumOf { it.quantity }

            DailyReport(
                date                  = date,
                totalRevenue          = totalRevenue,
                totalExpenses         = totalExpenses,
                totalReservations     = reservations.size,
                totalArrivals         = confirmed.size,
                totalClients          = totalClients,
                totalProductSales     = totalSalesRevenue,
                totalWorkerAdvances   = totalAdvances,
                totalWorkerPayments   = totalPayments,
                totalProductCost      = totalSalesCost,
                activeWorkers         = workers.count { it.isActive },
                totalDepartures       = 0,
                totalOccupiedRooms    = confirmed.size,
                staffPresent          = workers.count { it.currentPresence.equals("present", ignoreCase = true) },
                totalProductUnitsSold = totalUnitsSold,
                netProfit             = totalRevenue - totalExpenses,
            )
        } catch (e: Exception) {
            DailyReport(date = date)
        }
    }

    suspend fun getTodayReport(): DailyReport = calculateDailyReport(today())

    suspend fun saveDailyReport(report: DailyReport): Result<String> = try {
        val ref = db.collection("dailyReports").add(report).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updateDailyReport(id: String, report: DailyReport): Result<Unit> = try {
        db.collection("dailyReports").document(id).set(report).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // ── DASHBOARD STATS ───────────────────────────────────────────────

    /** Calculates live KPI stats for the Dashboard screen. */
    suspend fun getDashboardStats(): DashboardStats = coroutineScope {
        try {
            val date = today()

            val workersSnapDeferred   = async { db.collection("workers").get().await() }
            val reservationsDeferred  = async { reservationRepo.getReservationsByDate(date) }
            val lowStockCountDeferred = async { inventoryRepo.getLowStockItems().size }
            val tableOrdersDeferred   = async {
                val todayStart = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                db.collection("table_orders")
                    .whereGreaterThanOrEqualTo("created_at", com.google.firebase.Timestamp(todayStart.time))
                    .get().await().documents
            }

            val workersSnap   = workersSnapDeferred.await()
            val todayRes      = reservationsDeferred.await()
            val lowStockCount = lowStockCountDeferred.await()
            val orderDocs     = tableOrdersDeferred.await()

            val confirmedRes = todayRes.filter {
                it.status.equals("confirmed", ignoreCase = true) ||
                it.status.equals("checked-in", ignoreCase = true)
            }
            val dailyRevenue  = confirmedRes.sumOf { it.totalPrice }
            val totalAdults   = confirmedRes.sumOf { it.adults }
            val totalChildren = confirmedRes.sumOf { it.children }
            val staffPresent  = workersSnap.documents.count {
                it.getString("currentPresence").equals("present", ignoreCase = true)
            }
            val paidOrders    = orderDocs.filter { it.getString("status") == "paid" }
            val tablesServed  = paidOrders.size
            val ordersRevenue = paidOrders.sumOf {
                it.getDouble("grandTotal") ?: it.getDouble("total_price") ?: 0.0
            }

            DashboardStats(
                totalGuests     = reservationRepo.getReservations().size,
                occupiedRooms   = confirmedRes.size,
                totalStaff      = workersSnap.size(),
                dailyRevenue    = dailyRevenue,
                lowStockItems   = lowStockCount,
                pendingArrivals = todayRes.count { it.status.equals("pending", ignoreCase = true) },
                totalAdults     = totalAdults,
                totalChildren   = totalChildren,
                tablesServed    = tablesServed,
                staffPresent    = staffPresent,
                ordersRevenue   = ordersRevenue,
            )
        } catch (e: Exception) {
            DashboardStats()
        }
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────

    private suspend fun getAdvancesByDate(date: String): List<AdvanceRecord> = try {
        db.collection("advances").whereEqualTo("date", date).get().await().documents
            .mapNotNull { it.toObject(AdvanceRecord::class.java) }
    } catch (e: Exception) { emptyList() }

    private suspend fun getPaymentsByDate(date: String): List<PaymentRecord> = try {
        db.collection("payments").whereEqualTo("date", date).get().await().documents
            .mapNotNull { it.toObject(PaymentRecord::class.java) }
    } catch (e: Exception) { emptyList() }
}
