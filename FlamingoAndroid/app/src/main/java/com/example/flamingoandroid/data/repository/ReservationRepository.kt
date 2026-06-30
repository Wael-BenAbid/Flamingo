package com.example.flamingoandroid.data.repository

import com.example.flamingoandroid.data.models.Arrival
import com.example.flamingoandroid.data.models.Position
import com.example.flamingoandroid.data.models.Reservation
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ReservationRepository — single source of truth for:
 *  • reservations   collection (full CRUD + real-time Flow)
 *  • positions      collection (capacity types — Terrasse, Parasol, etc.)
 *  • arrivals       logic (today's confirmed/pending reservations)
 *
 * All Firestore calls that were scattered across FirebaseService and
 * ReservationsFragment are centralised here.
 */
class ReservationRepository {

    private val db = FirebaseFirestore.getInstance()

    // ── REAL-TIME FLOWS ──────────────────────────────────────────────

    /** Emits reservations from the last 90 days + future (pagination to avoid loading all history). */
    fun observeReservations(): Flow<List<Reservation>> = callbackFlow {
        val cutoff = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
            java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, -90)
            }.time
        )
        val listener = db.collection("reservations")
            .whereGreaterThanOrEqualTo("date", cutoff)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1000)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(
                    snapshot?.documents.orEmpty().mapNotNull { doc ->
                        doc.toObject(Reservation::class.java)?.copy(id = doc.id)
                    }
                )
            }
        awaitClose { listener.remove() }
    }

    /** Emits the positions list whenever Firestore changes. */
    fun observePositions(): Flow<List<Position>> = callbackFlow {
        val listener = db.collection("positions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(
                    snapshot?.documents.orEmpty().mapNotNull { doc ->
                        doc.toObject(Position::class.java)?.copy(id = doc.id)
                    }
                )
            }
        awaitClose { listener.remove() }
    }

    /** Emits today's pending reservations (= arrivals dashboard). */
    fun observeTodayArrivals(): Flow<List<Reservation>> = callbackFlow {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val listener = db.collection("reservations")
            .whereEqualTo("date", today)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val pending = snapshot?.documents.orEmpty()
                    .mapNotNull { doc -> doc.toObject(Reservation::class.java)?.copy(id = doc.id) }
                    .filter { it.status.equals("pending", ignoreCase = true) }
                trySend(pending)
            }
        awaitClose { listener.remove() }
    }

    /** Emits ALL of today's reservations (pending + confirmed + absent + cancelled). */
    fun observeTodayAllArrivals(): Flow<List<Reservation>> = callbackFlow {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val listener = db.collection("reservations")
            .whereEqualTo("date", today)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { doc -> doc.toObject(Reservation::class.java)?.copy(id = doc.id) }
                )
            }
        awaitClose { listener.remove() }
    }

    // ── ONE-SHOT READS ────────────────────────────────────────────────

    suspend fun getReservations(): List<Reservation> = try {
        db.collection("reservations")
            .orderBy("date", Query.Direction.DESCENDING)
            .get().await()
            .documents
            .mapNotNull { it.toObject(Reservation::class.java)?.copy(id = it.id) }
    } catch (e: Exception) { emptyList() }

    suspend fun getPositions(): List<Position> = try {
        db.collection("positions").get().await()
            .documents
            .mapNotNull { it.toObject(Position::class.java)?.copy(id = it.id) }
    } catch (e: Exception) { emptyList() }

    suspend fun getReservationsByDate(date: String): List<Reservation> = try {
        db.collection("reservations")
            .whereEqualTo("date", date)
            .get().await()
            .documents
            .mapNotNull { it.toObject(Reservation::class.java)?.copy(id = it.id) }
    } catch (e: Exception) { emptyList() }

    // ── RESERVATION MUTATIONS ─────────────────────────────────────────

    suspend fun addReservation(reservation: Reservation): Result<String> {
        return try {
            // Conflict check: block double-booking for same position on same day
            if (reservation.positionType.isNotBlank() && !reservation.positionNumber.isNullOrBlank()) {
                val conflict = db.collection("reservations")
                    .whereEqualTo("date", reservation.date)
                    .whereEqualTo("positionType", reservation.positionType)
                    .whereEqualTo("positionNumber", reservation.positionNumber!!)
                    .get().await()
                    .documents
                    .any { doc -> doc.getString("status") !in listOf("cancelled", "absent") }
                if (conflict) {
                    return Result.failure(
                        Exception("${reservation.positionType} N°${reservation.positionNumber} est déjà réservé(e) pour le ${reservation.date}")
                    )
                }
            }
            val now = Timestamp.now()
            val ref = db.collection("reservations")
                .add(reservation.copy(createdAt = now, updatedAt = now)).await()
            Result.success(ref.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun updateReservation(id: String, reservation: Reservation): Result<Unit> = try {
        db.collection("reservations").document(id)
            .set(reservation.copy(updatedAt = Timestamp.now())).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updateReservationStatus(id: String, status: String): Result<Unit> = try {
        db.collection("reservations").document(id)
            .update(mapOf("status" to status, "updatedAt" to Timestamp.now())).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun deleteReservation(id: String): Result<Unit> = try {
        db.collection("reservations").document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // Auto-cancel stale pending reservations from past dates.
    suspend fun cancelStaleReservations(beforeDate: String): Int {
        var cancelled = 0
        try {
            val stale = db.collection("reservations")
                .whereLessThan("date", beforeDate)
                .whereEqualTo("status", "pending")
                .get().await().documents
            stale.forEach { doc ->
                doc.reference.update("status", "cancelled").await()
                cancelled++
            }
        } catch (_: Exception) { /* best-effort */ }
        return cancelled
    }

    // ── POSITION MUTATIONS ────────────────────────────────────────────

    suspend fun addPosition(position: Position): Result<String> = try {
        val ref = db.collection("positions").add(position).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updatePosition(id: String, count: Int, price: Double, childPrice: Double): Result<Unit> = try {
        db.collection("positions").document(id).update(
            mapOf("count" to count, "price" to price, "childPrice" to childPrice)
        ).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun deletePosition(id: String): Result<Unit> = try {
        db.collection("positions").document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // ── ARRIVAL MUTATIONS ──────────────────────────────────────────────

    suspend fun confirmArrival(
        reservationId: String,
        positionType: String,
        positionNumber: String,
        adults: Int,
        children: Int,
    ): Result<Unit> = try {
        db.collection("reservations").document(reservationId)
            .update(
                mapOf(
                    "status"         to "confirmed",
                    "positionType"   to positionType,
                    "positionNumber" to positionNumber,
                    "adults"         to adults,
                    "children"       to children,
                    "checkIn"        to Timestamp.now(),
                    "updatedAt"      to Timestamp.now(),
                )
            ).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun markArrivalAbsent(reservationId: String): Result<Unit> =
        updateReservationStatus(reservationId, "absent")
}
