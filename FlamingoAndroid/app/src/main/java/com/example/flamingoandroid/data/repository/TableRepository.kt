package com.example.flamingoandroid.data.repository

import com.example.flamingoandroid.data.models.AppConfig
import com.example.flamingoandroid.data.models.Position
import com.example.flamingoandroid.data.models.TableOrder
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TableRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    fun listenToAppConfig(): Flow<AppConfig> = callbackFlow {
        val listener = db.collection("settings")
            .document("app_config")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(AppConfig())
                    return@addSnapshotListener
                }

                val totalTablesCount = snapshot?.getLong("total_tables_count")?.toInt() ?: 0
                trySend(AppConfig(total_tables_count = totalTablesCount))
            }

        awaitClose { listener.remove() }
    }

    fun listenToPositions(): Flow<List<Position>> = callbackFlow {
        val listener = db.collection("positions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val positions = snapshot?.documents.orEmpty().map { doc ->
                    doc.toObject(Position::class.java)?.copy(id = doc.id) ?: Position(id = doc.id)
                }
                trySend(positions)
            }

        awaitClose { listener.remove() }
    }

    fun listenToActiveOrders(): Flow<List<TableOrder>> = callbackFlow {
        val listener = db.collection("table_orders")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val activeStatuses = setOf("pending", "preparing", "ready")
                val orders = snapshot?.documents.orEmpty()
                    .mapNotNull { doc ->
                        val order = doc.toObject(TableOrder::class.java)
                        order?.copy(id = doc.id)
                    }
                    .filter { it.status in activeStatuses }

                trySend(orders)
            }

        awaitClose { listener.remove() }
    }
}
