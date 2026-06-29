package com.example.flamingoandroid.data.models

import com.google.firebase.Timestamp

data class MenuCategory(
    val id: String = "",
    val name: String = "",
    val display_order: Int = 0,
    val available: Boolean = true,
    val target_role: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
)

data class MenuItem(
    val id: String = "",
    val name: String = "",
    val category_id: String = "",
    val price: Double = 0.0,
    val is_available: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
)

data class TableOrderItem(
    val item_id: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val notes: String = "",
    val unit_price: Double = 0.0,
)

data class TableOrder(
    val id: String = "",
    val table_number: String = "",
    val server_id: String = "",
    val server_name: String = "",
    val status: String = "pending",
    val created_at: Timestamp? = null,
    val updated_at: Timestamp? = null,
    val items: List<TableOrderItem> = emptyList(),
    val total_price: Double = 0.0,
    val grandTotal: Double? = null,
    val discountPercent: Int = 0,
    val adults: Int = 0,
    val children: Int = 0,
    val clientName: String = "",
    val source: String = "",
    val scheduled_time: String? = null, // "HH:mm" — null = service immédiat
) {
    val finalTotal: Double get() = grandTotal ?: total_price
}

data class AppConfig(
    val id: String = "app_config",
    val total_tables_count: Int = 0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
)

data class OrderCartLine(
    val item_id: String,
    val name: String,
    val quantity: Int,
    val notes: String,
    val unit_price: Double,
)