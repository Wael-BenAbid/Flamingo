package com.example.flamingoandroid.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flamingoandroid.data.models.MenuCategory
import com.example.flamingoandroid.data.models.MenuItem
import com.example.flamingoandroid.data.models.Position
import com.example.flamingoandroid.data.models.Reservation
import com.example.flamingoandroid.data.models.TableOrder
import com.example.flamingoandroid.data.models.TableOrderItem
import com.example.flamingoandroid.data.repository.AuthRepository
import com.example.flamingoandroid.data.repository.ReservationRepository
import com.example.flamingoandroid.data.repository.TableRepository
import com.google.firebase.auth.FirebaseAuth
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
    val paidTables: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val userRole: String = "",
)

sealed class PaymentAction {
    data class Success(val tableLabel: String) : PaymentAction()
    data class Error(val message: String) : PaymentAction()
}

class PaymentViewModel(
    private val tableRepo: TableRepository = TableRepository(),
    private val reservationRepo: ReservationRepository = ReservationRepository(),
    private val authRepo: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val _paidTables = MutableStateFlow<Set<String>>(emptySet())
    private val _action     = MutableStateFlow<PaymentAction?>(null)
    private val _userRole   = MutableStateFlow("")
    val action: StateFlow<PaymentAction?> = _action.asStateFlow()

    init {
        viewModelScope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            _userRole.value = authRepo.getCurrentUserRole(user) ?: ""
        }
    }

    val uiState: StateFlow<PaymentUiState> = combine(
        tableRepo.listenToPositions(),
        tableRepo.listenToActiveOrders(),
        reservationRepo.observeTodayAllArrivals(),
        _paidTables,
        _userRole,
    ) { positions: List<Position>,
        orders: List<TableOrder>,
        arrivals: List<Reservation>,
        paidTables: Set<String>,
        userRole: String ->

        val confirmed = arrivals.filter {
            it.status.equals("confirmed", ignoreCase = true) &&
            it.positionType.isNotBlank() &&
            !it.positionNumber.isNullOrBlank()
        }
        val reservationByTable = confirmed.associate { r ->
            "${r.positionType.trim()} ${r.positionNumber?.trim() ?: ""}".trim() to r
        }
        val orderByTable = orders.associate { it.table_number to it }

        PaymentUiState(
            positions          = positions.sortedBy { it.type },
            reservationByTable = reservationByTable,
            orderByTable       = orderByTable,
            paidTables         = paidTables,
            isLoading          = false,
            userRole           = userRole,
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
        /** Items with per-line prices possibly edited at payment time (overrides order.items prices). */
        adjustedItems: List<TableOrderItem>? = null,
    ) {
        viewModelScope.launch {
            try {
                withTimeout(20_000) {
                val now      = Timestamp.now()
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val batch    = db.batch()
                val itemsToSell = adjustedItems ?: order?.items ?: emptyList()

                // 1 — sales records per item, using the ACTUAL price charged at payment
                itemsToSell.forEach { item ->
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
                            "tableOrderId"  to (order?.id ?: ""),
                            "createdAt"     to now,
                        ))
                    }
                }

                // 1b — residual adjustment record (covers any gap between UI total and sold items, e.g. rounding)
                val soldTotal = itemsToSell.sumOf { it.unit_price * it.quantity }
                val orderAdjustment = adjustedOrderTotal - soldTotal
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

                // 2 — mark table_order paid (persist the adjusted item prices on the order itself)
                order?.id?.takeIf { it.isNotBlank() }?.let { orderId ->
                    val updateMap = mutableMapOf<String, Any>(
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
                    if (adjustedItems != null) {
                        updateMap["items"] = adjustedItems.map {
                            mapOf(
                                "item_id" to it.item_id, "name" to it.name,
                                "quantity" to it.quantity, "unit_price" to it.unit_price,
                                "notes" to it.notes,
                            )
                        }
                    }
                    batch.update(db.collection("table_orders").document(orderId), updateMap)
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

    /** Annuler un paiement — visible admin/responsable uniquement. Remet la table en état "ready". */
    fun cancelPayment(tableLabel: String, order: TableOrder?) {
        viewModelScope.launch {
            try {
                withTimeout(10_000) {
                    order?.id?.takeIf { it.isNotBlank() }?.let { orderId ->
                        db.collection("table_orders").document(orderId).update(
                            mapOf(
                                "status"    to "ready",
                                "paidAt"    to null,
                                "voidedAt"  to Timestamp.now(),
                                "updatedAt" to Timestamp.now(),
                            )
                        ).await()
                    }
                    _paidTables.value = _paidTables.value - tableLabel
                    _action.value = PaymentAction.Success(tableLabel)
                }
            } catch (e: Exception) {
                _action.value = PaymentAction.Error("Annulation échouée : ${e.message}")
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
                val batch = db.batch()

                // Menu item sales (original prices for product tracking)
                cartItems.forEach { item ->
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
                            "source"        to "walkin_payment",
                            "tableLabel"    to tableLabel,
                            "createdAt"     to now,
                        ))
                    }
                }

                // Entry fee — stored at undiscounted amount; adjustment below corrects bilan
                if (reservationTotal > 0) {
                    val ref = db.collection("sales").document()
                    batch.set(ref, mapOf(
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
                        "createdAt"     to now,
                    ))
                }

                // Discount adjustment record — ensures bilan deducts walk-in discounts correctly
                if (discountAmount > 0.009) {
                    val ref = db.collection("sales").document()
                    batch.set(ref, mapOf(
                        "productName"   to "Remise $discountPercent% ($tableLabel)",
                        "productId"     to "table-adjustment",
                        "quantity"      to 1,
                        "unitSellPrice" to -discountAmount,
                        "unitBuyPrice"  to 0.0,
                        "totalPrice"    to -discountAmount,
                        "totalCost"     to 0.0,
                        "date"          to todayStr,
                        "source"        to "table_adjustment",
                        "tableLabel"    to tableLabel,
                        "createdAt"     to now,
                    ))
                }

                // Mark tile as paid — atomic with all sales records above
                val orderRef = db.collection("table_orders").document()
                batch.set(orderRef, mapOf(
                    "table_number"    to tableLabel,
                    "server_id"       to "",
                    "server_name"     to clientName.ifBlank { "Walk-in" },
                    "status"          to "paid",
                    "items"           to cartItems.map {
                        mapOf("item_id" to it.item_id, "name" to it.name,
                              "quantity" to it.quantity, "unit_price" to it.unit_price, "notes" to it.notes)
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
                ))

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
}
