package com.example.flamingoandroid.data.firebase

import com.example.flamingoandroid.data.models.AppConfig
import com.example.flamingoandroid.data.models.MenuCategory
import com.example.flamingoandroid.data.models.MenuItem
import com.example.flamingoandroid.data.models.TableOrder
import com.example.flamingoandroid.data.models.TableOrderItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class MenuOrderingFirebaseService {
    private val db = FirebaseFirestore.getInstance()

    private fun resolveCategoryId(doc: com.google.firebase.firestore.DocumentSnapshot, item: MenuItem): String {
        val candidates = listOf(
            item.category_id,
            doc.getString("category_id"),
            doc.getString("categoryId"),
            doc.getString("category"),
        )

        return candidates.firstOrNull { !it.isNullOrBlank() }.orEmpty()
    }

    private fun resolveAvailability(doc: com.google.firebase.firestore.DocumentSnapshot, item: MenuItem): Boolean {
        return doc.getBoolean("is_available")
            ?: doc.getBoolean("available")
            ?: item.is_available
    }

    fun observeAppConfig(): Flow<AppConfig> = callbackFlow {
        val registration = db.collection("settings")
            .document("app_config")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(AppConfig())
                    return@addSnapshotListener
                }

                val config = snapshot?.toObject(AppConfig::class.java) ?: AppConfig()
                trySend(config.copy(id = snapshot?.id ?: "app_config"))
            }

        awaitClose { registration.remove() }
    }

    fun observeMenuCategories(): Flow<List<MenuCategory>> = callbackFlow {
        val registration = db.collection("menu_categories")
            .orderBy("display_order", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val categories = snapshot?.documents.orEmpty().map { doc ->
                    doc.toObject(MenuCategory::class.java)?.copy(id = doc.id) ?: MenuCategory(id = doc.id)
                }
                trySend(categories)
            }

        awaitClose { registration.remove() }
    }

    fun observeMenuItems(): Flow<List<MenuItem>> = callbackFlow {
        val registration = db.collection("menu_items")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val items = snapshot?.documents.orEmpty().map { doc ->
                    val item = doc.toObject(MenuItem::class.java) ?: MenuItem(id = doc.id)
                    item.copy(
                        id = doc.id,
                        category_id = resolveCategoryId(doc, item),
                        is_available = resolveAvailability(doc, item),
                    )
                }
                trySend(items)
            }

        awaitClose { registration.remove() }
    }

    private fun startOfTodayTimestamp(): Timestamp {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return Timestamp(cal.time)
    }

    fun observeActivePlanOrders(): Flow<List<TableOrder>> = callbackFlow {
        val registration = db.collection("table_orders")
            .whereGreaterThanOrEqualTo("created_at", startOfTodayTimestamp())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val orders = snapshot?.documents.orEmpty().map { doc ->
                    doc.toObject(TableOrder::class.java)?.copy(id = doc.id) ?: TableOrder(id = doc.id)
                }.filter { it.status in setOf("pending", "preparing", "ready") }
                    .sortedWith(
                        compareBy<TableOrder> { it.created_at?.toDate()?.time ?: Long.MAX_VALUE }
                            .thenBy { it.id }
                    )
                trySend(orders)
            }

        awaitClose { registration.remove() }
    }

    fun observeKitchenOrders(): Flow<List<TableOrder>> = callbackFlow {
        val registration = db.collection("table_orders")
            .whereGreaterThanOrEqualTo("created_at", startOfTodayTimestamp())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val orders = snapshot?.documents.orEmpty().map { doc ->
                    doc.toObject(TableOrder::class.java)?.copy(id = doc.id) ?: TableOrder(id = doc.id)
                }.filter { it.status in setOf("pending", "preparing", "ready") }
                    .sortedWith(
                        compareBy<TableOrder> { it.created_at?.toDate()?.time ?: Long.MAX_VALUE }
                            .thenBy { it.id }
                    )
                trySend(orders)
            }

        awaitClose { registration.remove() }
    }

    suspend fun updateAppConfig(totalTablesCount: Int): Result<Unit> = try {
        db.collection("settings")
            .document("app_config")
            .set(
                mapOf(
                    "total_tables_count" to totalTablesCount,
                    "updatedAt" to Timestamp.now(),
                    "createdAt" to Timestamp.now(),
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun upsertCategory(category: MenuCategory): Result<Unit> = try {
        val payload = mapOf(
            "name" to category.name,
            "display_order" to category.display_order,
            "updatedAt" to Timestamp.now(),
            "createdAt" to Timestamp.now(),
        )
        if (category.id.isBlank()) {
            db.collection("menu_categories").add(payload).await()
        } else {
            db.collection("menu_categories").document(category.id).set(payload).await()
        }
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> = try {
        db.collection("menu_categories").document(categoryId).delete().await()
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun upsertMenuItem(item: MenuItem): Result<Unit> = try {
        val payload = mapOf(
            "name" to item.name,
            "category_id" to item.category_id,
            "price" to item.price,
            "is_available" to item.is_available,
            "updatedAt" to Timestamp.now(),
            "createdAt" to Timestamp.now(),
        )
        if (item.id.isBlank()) {
            db.collection("menu_items").add(payload).await()
        } else {
            db.collection("menu_items").document(item.id).set(payload).await()
        }
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun deleteMenuItem(itemId: String): Result<Unit> = try {
        db.collection("menu_items").document(itemId).delete().await()
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun createTableOrder(
        tableNumber: String,
        serverId: String,
        serverName: String,
        items: List<TableOrderItem>,
        totalPrice: Double,
    ): Result<String> = try {
        val payload = mapOf(
            "table_number" to tableNumber,
            "server_id" to serverId,
            "server_name" to serverName,
            "status" to "pending",
            "created_at" to Timestamp.now(),
            "updated_at" to Timestamp.now(),
            "items" to items,
            "total_price" to totalPrice,
        )
        val docRef = db.collection("table_orders").add(payload).await()
        Result.success(docRef.id)
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun updateTableOrder(
        orderId: String,
        items: List<TableOrderItem>,
        totalPrice: Double,
        serverId: String = "",
        serverName: String = "",
    ): Result<Unit> = try {
        val payload = mutableMapOf<String, Any>(
            "items" to items,
            "total_price" to totalPrice,
            "updated_at" to Timestamp.now(),
        )
        if (serverId.isNotBlank()) payload["server_id"] = serverId
        if (serverName.isNotBlank()) payload["server_name"] = serverName
        db.collection("table_orders")
            .document(orderId)
            .update(payload)
            .await()
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun updateOrderStatus(orderId: String, status: String): Result<Unit> = try {
        db.collection("table_orders")
            .document(orderId)
            .update(
                mapOf(
                    "status" to status,
                    "updated_at" to Timestamp.now(),
                )
            )
            .await()
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }
}
