package com.example.flamingoandroid.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flamingoandroid.data.models.MenuCategory
import com.example.flamingoandroid.data.models.MenuItem
import com.example.flamingoandroid.data.models.Position
import com.example.flamingoandroid.data.models.Reservation
import com.example.flamingoandroid.data.models.TableOrder
import com.example.flamingoandroid.data.models.TableOrderItem
import com.example.flamingoandroid.data.repository.ReservationRepository
import com.example.flamingoandroid.data.repository.TableRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PaymentUiState(
    val positions: List<Position> = emptyList(),
    val reservationByTable: Map<String, Reservation> = emptyMap(),
    val orderByTable: Map<String, TableOrder> = emptyMap(),
    /** Tables paid in this session (persists even after order leaves active flow) */
    val paidTables: Set<String> = emptySet(),
    val isLoading: Boolean = true,
)

sealed class PaymentAction {
    data class Success(val tableLabel: String) : PaymentAction()
    data class Error(val message: String) : PaymentAction()
}

class PaymentViewModel(
    private val tableRepo: TableRepository = TableRepository(),
    private val reservationRepo: ReservationRepository = ReservationRepository(),
) : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val _paidTables = MutableStateFlow<Set<String>>(emptySet())
    private val _action = MutableStateFlow<PaymentAction?>(null)
    val action: StateFlow<PaymentAction?> = _action.asStateFlow()

    val uiState: StateFlow<PaymentUiState> = combine(
        tableRepo.listenToPositions(),
        tableRepo.listenToActiveOrders(),
        reservationRepo.observeTodayAllArrivals(),
        _paidTables,
    ) { positions: List<Position>,
        orders: List<TableOrder>,
        arrivals: List<Reservation>,
        paidTables: Set<String> ->

        val confirmed = arrivals.filter {
            it.status.equals("confirmed", ignoreCase = true) &&
            it.positionType.isNotBlank() &&
            !it.positionNumber.isNullOrBlank()
        }
        val reservationByTable = confirmed.associate { r ->
            "${r.positionType.trim()} ${r.positionNumber!!.trim()}" to r
        }
        val orderByTable = orders.associate { it.table_number to it }

        PaymentUiState(
            positions      = positions.sortedBy { it.type },
            reservationByTable = reservationByTable,
            orderByTable   = orderByTable,
            paidTables     = paidTables,
            isLoading      = false,
        )
    }.stateIn(
        scope          = viewModelScope,
        started        = SharingStarted.WhileSubscribed(5_000),
        initialValue   = PaymentUiState(),
    )

    fun payTable(
        tableLabel: String,
        reservation: Reservation?,
        order: TableOrder?,
        discountPercent: Int,
        discountAmount: Double,
        finalTotal: Double,
        remarque: String,
        customAdultPrice: Double = 0.0,
        customChildPrice: Double = 0.0,
        adjustedOrderTotal: Double = 0.0,
    ) {
        viewModelScope.launch {
            try {
                withTimeout(20_000) {
                val now      = Timestamp.now()
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val batch    = db.batch()

                // 1 — sales records per item (original prices for product tracking)
                order?.items?.forEach { item ->
                    if (item.quantity > 0) {
                        val ref = db.collection("sales").document()
                        batch.set(ref, mapOf(
                            "productName"   to item.name,
                            "productId"     to item.item_id,
                            "quantity"      to item.quantity,
                            "unitSellPrice" to item.unit_price,
                            "unitBuyPrice"  to 0.0,
                            "totalPrice"    to item.unit_price * item.quantity,
                            "totalCost"     to 0.0,
                            "date"          to todayStr,
                            "source"        to "table_payment",
                            "tableLabel"    to tableLabel,
                            "tableOrderId"  to (order.id),
                            "createdAt"     to now,
                        ))
                    }
                }

                // 1b — adjustment record when order total was manually changed
                val computedOrderTotal = order?.items?.sumOf { it.unit_price * it.quantity } ?: 0.0
                val orderAdjustment = adjustedOrderTotal - computedOrderTotal
                if (order != null && Math.abs(orderAdjustment) > 0.009) {
                    val ref = db.collection("sales").document()
                    batch.set(ref, mapOf(
                        "productName"   to "Ajustement commande ($tableLabel)",
                        "productId"     to "table-adjustment",
                        "quantity"      to 1,
                        "unitSellPrice" to orderAdjustment,
                        "unitBuyPrice"  to 0.0,
                        "totalPrice"    to orderAdjustment,
                        "totalCost"     to 0.0,
                        "date"          to todayStr,
                        "source"        to "table_adjustment",
                        "tableLabel"    to tableLabel,
                        "tableOrderId"  to order.id,
                        "createdAt"     to now,
                    ))
                }

                // 2 — mark table_order paid
                order?.id?.takeIf { it.isNotBlank() }?.let { orderId ->
                    batch.update(
                        db.collection("table_orders").document(orderId),
                        mapOf(
                            "status"             to "paid",
                            "paidAt"             to now,
                            "grandTotal"         to finalTotal,
                            "discountPercent"    to discountPercent,
                            "discountAmount"     to discountAmount,
                            "remarque"           to remarque.trim(),
                            "customAdultPrice"   to customAdultPrice,
                            "customChildPrice"   to customChildPrice,
                            "adjustedOrderTotal" to adjustedOrderTotal,
                        )
                    )
                }

                // 3 — mark reservation paid
                reservation?.id?.takeIf { it.isNotBlank() }?.let { resId ->
                    batch.update(
                        db.collection("reservations").document(resId),
                        mapOf(
                            "paidAt"     to now,
                            "grandTotal" to finalTotal,
                        )
                    )
                }

                // Commit all writes atomically — all succeed or all fail
                batch.commit().await()

                _paidTables.value = _paidTables.value + tableLabel
                _action.value = PaymentAction.Success(tableLabel)
                } // end withTimeout
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _action.value = PaymentAction.Error("Délai dépassé — vérifiez la connexion réseau")
            } catch (e: Exception) {
                _action.value = PaymentAction.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun clearAction() { _action.value = null }

    // ── Walk-in menu loading ───────────────────────────────────────────────

    private val _walkInCategories = MutableStateFlow<List<MenuCategory>>(emptyList())
    val walkInCategories: StateFlow<List<MenuCategory>> = _walkInCategories.asStateFlow()

    private val _walkInItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val walkInItems: StateFlow<List<MenuItem>> = _walkInItems.asStateFlow()

    private val _walkInMenuLoading = MutableStateFlow(false)
    val walkInMenuLoading: StateFlow<Boolean> = _walkInMenuLoading.asStateFlow()

    fun loadMenuForWalkIn() {
        if (_walkInCategories.value.isNotEmpty()) return
        viewModelScope.launch {
            _walkInMenuLoading.value = true
            try {
                val catSnap  = db.collection("menu_categories").get().await()
                val itemSnap = db.collection("menu_items").get().await()

                _walkInCategories.value = catSnap.documents
                    .mapNotNull { doc ->
                        doc.toObject(MenuCategory::class.java)?.copy(id = doc.id)
                    }
                    .filter { it.available }
                    .sortedBy { it.display_order }

                _walkInItems.value = itemSnap.documents
                    .mapNotNull { doc ->
                        doc.toObject(MenuItem::class.java)?.copy(id = doc.id)
                    }
                    .filter { it.is_available }
            } catch (e: Exception) {
                _action.value = PaymentAction.Error("Menu: ${e.message}")
            } finally {
                _walkInMenuLoading.value = false
            }
        }
    }

    fun payWalkIn(
        tableLabel: String,
        clientName: String,
        adults: Int,
        children: Int,
        adultUnitPrice: Double,
        childUnitPrice: Double,
        cartItems: List<TableOrderItem>,
        discountPercent: Int,
        discountAmount: Double,
        finalTotal: Double,
        remarque: String,
    ) {
        viewModelScope.launch {
            try {
                withTimeout(20_000) {
                val now      = Timestamp.now()
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val reservationTotal = adultUnitPrice * adults + childUnitPrice * children

                // Sales records for each menu item
                cartItems.forEach { item ->
                    if (item.quantity > 0) {
                        db.collection("sales").add(
                            mapOf(
                                "productName"   to item.name,
                                "productId"     to item.item_id,
                                "quantity"      to item.quantity,
                                "unitSellPrice" to item.unit_price,
                                "unitBuyPrice"  to 0.0,
                                "totalPrice"    to item.unit_price * item.quantity,
                                "totalCost"     to 0.0,
                                "date"          to todayStr,
                                "source"        to "walkin_payment",
                                "tableLabel"    to tableLabel,
                                "createdAt"     to now,
                            )
                        ).await()
                    }
                }

                // Entry fee sale record
                if (reservationTotal > 0) {
                    db.collection("sales").add(
                        mapOf(
                            "productName"   to "Entrée ($tableLabel)",
                            "productId"     to "walkin-entry",
                            "quantity"      to 1,
                            "unitSellPrice" to reservationTotal,
                            "unitBuyPrice"  to 0.0,
                            "totalPrice"    to reservationTotal,
                            "totalCost"     to 0.0,
                            "date"          to todayStr,
                            "source"        to "walkin_entry",
                            "tableLabel"    to tableLabel,
                            "adults"        to adults,
                            "children"      to children,
                            "clientName"    to clientName.ifBlank { "—" },
                            "discountPercent" to discountPercent,
                            "discountAmount"  to discountAmount,
                            "finalTotal"      to finalTotal,
                            "remarque"        to remarque.trim(),
                            "createdAt"       to now,
                        )
                    ).await()
                }

                // Mark tile as paid in the payment grid
                db.collection("table_orders").add(
                    mapOf(
                        "table_number"    to tableLabel,
                        "server_id"       to "",
                        "server_name"     to clientName.ifBlank { "Walk-in" },
                        "status"          to "paid",
                        "items"           to cartItems.map {
                            mapOf(
                                "item_id"    to it.item_id,
                                "name"       to it.name,
                                "quantity"   to it.quantity,
                                "unit_price" to it.unit_price,
                                "notes"      to it.notes,
                            )
                        },
                        "total_price"     to finalTotal,
                        "created_at"      to now,
                        "updated_at"      to now,
                        "paidAt"          to now,
                        "grandTotal"      to finalTotal,
                        "discountPercent" to discountPercent,
                        "discountAmount"  to discountAmount,
                        "remarque"        to remarque.trim(),
                        "clientName"      to clientName.ifBlank { "—" },
                        "adults"          to adults,
                        "children"        to children,
                        "source"          to "walkin",
                    )
                ).await()

                _paidTables.value = _paidTables.value + tableLabel
                _action.value = PaymentAction.Success(tableLabel)
                } // end withTimeout
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _action.value = PaymentAction.Error("Délai dépassé — vérifiez la connexion réseau")
            } catch (e: Exception) {
                _action.value = PaymentAction.Error(e.message ?: "Erreur inconnue")
            }
        }
    }
}
