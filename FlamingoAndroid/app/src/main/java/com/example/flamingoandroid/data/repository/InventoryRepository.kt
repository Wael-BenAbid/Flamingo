package com.example.flamingoandroid.data.repository

import com.example.flamingoandroid.data.models.InventoryItem
import com.example.flamingoandroid.data.models.SaleRecord
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * InventoryRepository — single source of truth for:
 *  • inventory  collection (stock CRUD + real-time Flow)
 *  • sales      collection (sale records)
 *
 * Provides:
 *  • [observeInventory]  — Snapshot Listener that reacts to any stock change
 *  • [observeLowStock]   — filtered Flow for items at or below minStock
 *  • [addSale]           — decrements stock + records the sale atomically
 */
class InventoryRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun stockQty(item: InventoryItem) =
        if (item.stockQuantity > 0) item.stockQuantity else item.quantity

    private fun minStk(item: InventoryItem) =
        if (item.minStock > 0) item.minStock else item.minimumStock

    // ── REAL-TIME FLOWS ───────────────────────────────────────────────

    /** Full inventory list, updated in real-time. */
    fun observeInventory(): Flow<List<InventoryItem>> = callbackFlow {
        val listener = db.collection("inventory")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(
                    snapshot?.documents.orEmpty().mapNotNull { doc ->
                        doc.toObject(InventoryItem::class.java)?.copy(id = doc.id)
                    }
                )
            }
        awaitClose { listener.remove() }
    }

    /** Filtered Flow emitting only items at or below their minStock threshold. */
    fun observeLowStock(): Flow<List<InventoryItem>> = callbackFlow {
        val listener = db.collection("inventory")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { doc -> doc.toObject(InventoryItem::class.java)?.copy(id = doc.id) }
                        .filter { stockQty(it) <= minStk(it) }
                )
            }
        awaitClose { listener.remove() }
    }

    // ── ONE-SHOT READS ─────────────────────────────────────────────────

    suspend fun getInventoryItems(): List<InventoryItem> = try {
        db.collection("inventory").get().await().documents
            .mapNotNull { doc -> doc.toObject(InventoryItem::class.java)?.copy(id = doc.id) }
    } catch (e: Exception) { emptyList() }

    suspend fun getLowStockItems(): List<InventoryItem> =
        getInventoryItems().filter { stockQty(it) <= minStk(it) }

    suspend fun getItemsByCategory(category: String): List<InventoryItem> = try {
        db.collection("inventory").whereEqualTo("category", category).get().await().documents
            .mapNotNull { doc -> doc.toObject(InventoryItem::class.java)?.copy(id = doc.id) }
    } catch (e: Exception) { emptyList() }

    suspend fun getSales(): List<SaleRecord> = try {
        db.collection("sales").get().await().documents
            .mapNotNull { doc -> doc.toObject(SaleRecord::class.java)?.copy(id = doc.id) }
    } catch (e: Exception) { emptyList() }

    suspend fun getSalesByDate(date: String): List<SaleRecord> = try {
        db.collection("sales").whereEqualTo("date", date).get().await().documents
            .mapNotNull { doc -> doc.toObject(SaleRecord::class.java)?.copy(id = doc.id) }
    } catch (e: Exception) { emptyList() }

    // ── INVENTORY MUTATIONS ────────────────────────────────────────────

    suspend fun addInventoryItem(item: InventoryItem): Result<String> = try {
        val ref = db.collection("inventory").add(item).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updateInventoryItem(id: String, item: InventoryItem): Result<Unit> = try {
        db.collection("inventory").document(id).set(item.copy(id = id)).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updateStock(itemId: String, newQuantity: Int): Result<Unit> = try {
        db.collection("inventory").document(itemId)
            .update(mapOf("stockQuantity" to newQuantity, "quantity" to newQuantity)).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun deleteInventoryItem(id: String): Result<Unit> = try {
        db.collection("inventory").document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // ── SALES ──────────────────────────────────────────────────────────

    /**
     * Records a sale and decrements stock in a single logical operation.
     * Uses two separate writes (not a batch) to keep it simple; a Firestore
     * batch could be added if strict atomicity is required.
     */
    suspend fun addSale(product: InventoryItem, quantity: Int): Result<String> = try {
        val now      = Timestamp.now()
        val qty      = quantity.coerceAtLeast(1)
        val buyPrice = if (product.buyPrice  > 0) product.buyPrice  else product.unitPrice
        val selPrice = if (product.sellPrice > 0) product.sellPrice else product.unitPrice

        val ref = db.collection("sales").add(
            SaleRecord(
                productId     = product.id,
                productName   = product.name,
                quantity      = qty,
                unitBuyPrice  = buyPrice,
                unitSellPrice = selPrice,
                totalCost     = buyPrice * qty,
                totalPrice    = selPrice * qty,
                date          = today(),
                timestamp     = System.currentTimeMillis(),
                createdAt     = now,
                updatedAt     = now
            )
        ).await()

        // Decrement stock
        val newStock = (stockQty(product) - qty).coerceAtLeast(0)
        db.collection("inventory").document(product.id)
            .update("stockQuantity", newStock).await()

        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }
}
