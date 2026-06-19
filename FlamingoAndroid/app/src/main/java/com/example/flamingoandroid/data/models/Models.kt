package com.example.flamingoandroid.data.models

import com.google.firebase.Timestamp

data class CreatedStaffAccount(
    val uid: String,
    val email: String,
    val password: String,
    val role: String
)

data class Worker(
    val id: String = "",
    val uid: String = "",
    val fullName: String = "",
    val category: String = "",
    val role: String = "",
    val dailyWage: Double = 0.0,
    val totalAdvances: Double = 0.0,
    val totalPenalties: Double = 0.0,
    val currentPresence: String = "absent",
    val attendanceCount: Double = 0.0,
    val totalEarned: Double = 0.0,
    val totalPaid: Double = 0.0,
    val lastPresenceDate: String? = null,
    val startDate: String? = null,
    // Any? accepte Timestamp, String ou null sans planter la désérialisation
    @get:com.google.firebase.firestore.Exclude val createdAt: Any? = null,
    @get:com.google.firebase.firestore.Exclude val updatedAt: Any? = null,
    val email: String = "",
    val phone: String = "",
    val position: String = "",
    val salary: Double = 0.0,
    @get:com.google.firebase.firestore.Exclude val joinDate: Any? = null,
    val isActive: Boolean = true
)

data class Reservation(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String? = null,
    val adults: Int = 0,
    val children: Int = 0,
    val date: String = "",
    val time: String = "",
    val positionType: String = "",
    val positionNumber: String? = null,
    val status: String = "pending",
    val notes: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val guestName: String = "",
    val roomNumber: String = "",
    val numberOfNights: Int = 0,
    val totalPrice: Double = 0.0,
    val checkIn: Timestamp? = null,
    val checkOut: Timestamp? = null
)

data class Position(
    val id: String = "",
    val type: String = "",
    val count: Int = 0,
    val price: Double = 0.0,
    val childPrice: Double = 0.0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class InventoryItem(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val stockQuantity: Int = 0,
    val minStock: Int = 0,
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val unit: String = "",
    val quantity: Int = 0,
    val minimumStock: Int = 0,
    val unitPrice: Double = 0.0,
    val supplier: String = "",
    val location: String = "",
    val lastUpdated: Timestamp? = null,
    val lastRestocked: Timestamp? = null,
)

data class Arrival(
    val id: String = "",
    val reservationId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val adults: Int = 0,
    val children: Int = 0,
    val date: String = "",
    val time: String = "",
    val positionType: String = "",
    val positionNumber: String? = null,
    val notes: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val guestName: String = "",
    val roomNumber: String = "",
    val checkInTime: Timestamp? = null,
    val status: String = "pending"
)

data class DailyReport(
    val id: String = "",
    val date: String = "",
    val totalRevenue: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalReservations: Int = 0,
    val totalArrivals: Int = 0,
    val totalClients: Int = 0,
    val totalProductSales: Double = 0.0,
    val totalWorkerAdvances: Double = 0.0,
    val totalWorkerPayments: Double = 0.0,
    val totalProductCost: Double = 0.0,
    val activeWorkers: Int = 0,
    val totalDepartures: Int = 0,
    val totalOccupiedRooms: Int = 0,
    val staffPresent: Int = 0,
    val totalProductUnitsSold: Int = 0,
    val netProfit: Double = 0.0,
    val notes: String = "",
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class SaleRecord(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val quantity: Int = 0,
    val unitBuyPrice: Double = 0.0,
    val unitSellPrice: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalPrice: Double = 0.0,
    val date: String = "",
    val timestamp: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class AdvanceRecord(
    val id: String = "",
    val workerId: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val reason: String = "",
    val timestamp: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class PenaltyRecord(
    val id: String = "",
    val workerId: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val reason: String = "",
    val timestamp: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class PaymentRecord(
    val id: String = "",
    val workerId: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val method: String = "cash",
    val timestamp: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class AttendanceRecord(
    val id: String = "",
    val workerId: String = "",
    val date: String = "",
    val time: String = "",
    val status: String = "present",
    val timestamp: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class AttendanceMonthRecord(
    val id: String = "",
    val workerId: String = "",
    val month: String = "",
    val days: Map<String, String> = emptyMap(),
    val updatedAt: Timestamp? = null
)

data class DashboardStats(
    val totalGuests: Int = 0,
    val occupiedRooms: Int = 0,
    val totalStaff: Int = 0,
    val dailyRevenue: Double = 0.0,
    val lowStockItems: Int = 0,
    val pendingArrivals: Int = 0,
    val totalAdults: Int = 0,
    val totalChildren: Int = 0,
    val tablesServed: Int = 0,
    val staffPresent: Int = 0,
    val ordersRevenue: Double = 0.0,
)
