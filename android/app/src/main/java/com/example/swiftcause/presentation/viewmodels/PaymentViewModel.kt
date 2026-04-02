package com.example.swiftcause.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swiftcause.data.models.*
import com.example.swiftcause.data.repository.PaymentRepository
import com.stripe.android.paymentsheet.PaymentSheetResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing payment flow and state
 */
class PaymentViewModel(
    private val paymentRepository: PaymentRepository = PaymentRepository()
) : ViewModel() {

    companion object {
        private const val TAG = "PaymentViewModel"
    }

    // Payment state
    private val _paymentState = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val paymentState: StateFlow<PaymentState> = _paymentState.asStateFlow()

    // Client secret for PaymentSheet
    private val _clientSecret = MutableStateFlow<String?>(null)
    val clientSecret: StateFlow<String?> = _clientSecret.asStateFlow()

    /**
     * Creates a payment intent and prepares PaymentSheet
     */
    fun preparePayment(
        amount: Long,
        currency: String,
        campaignId: String,
        campaignTitle: String,
        organizationId: String,
        donorName: String? = null,
        donorEmail: String? = null,
        isAnonymous: Boolean = false,
        frequency: String? = null  // null = one-time, "month", "year" = recurring
    ) {
        viewModelScope.launch {
            try {
                _paymentState.value = PaymentState.Loading
                Log.d(TAG, "=== Preparing Payment ===")
                Log.d(TAG, "Amount: $amount cents (${amount / 100.0})")
                Log.d(TAG, "Currency: $currency")
                Log.d(TAG, "Campaign: $campaignTitle ($campaignId)")
                Log.d(TAG, "Organization ID: $organizationId")
                Log.d(TAG, "Is Recurring: ${frequency != null}")
                Log.d(TAG, "Frequency: $frequency")
                Log.d(TAG, "Is Anonymous: $isAnonymous")

                // Create payment intent request
                val request = CreatePaymentIntentRequest(
                    amount = amount,
                    currency = currency.lowercase(),
                    metadata = PaymentMetadata(
                        campaignId = campaignId,
                        campaignTitle = campaignTitle,
                        organizationId = organizationId,
                        platform = "android_kiosk",
                        donorName = donorName,
                        donorEmail = donorEmail,
                        isAnonymous = isAnonymous
                    ),
                    frequency = frequency,
                    donor = if (!donorEmail.isNullOrBlank() && !donorName.isNullOrBlank()) {
                        DonorInfo(
                            email = donorEmail,
                            name = donorName
                        )
                    } else null
                )

                Log.d(TAG, "Calling PaymentRepository.createPaymentIntent()")
                
                // Call backend to create payment intent
                val result = paymentRepository.createPaymentIntent(request)

                result.fold(
                    onSuccess = { response ->
                        Log.d(TAG, "Repository returned success")
                        if (response.clientSecret != null) {
                            _clientSecret.value = response.clientSecret
                            _paymentState.value = PaymentState.Ready
                            Log.d(TAG, "Payment prepared - State: Ready")
                            Log.d(TAG, "Client secret length: ${response.clientSecret.length}")
                        } else if (response.success == true) {
                            // Recurring payment succeeded immediately
                            Log.d(TAG, "Recurring payment succeeded immediately")
                            _paymentState.value = PaymentState.Success(
                                transactionId = response.subscriptionId ?: "",
                                amount = amount,
                                currency = currency
                            )
                        } else {
                            Log.e(TAG, "No client secret and not success: ${response.message}")
                            _paymentState.value = PaymentState.Error(
                                message = response.message ?: "Failed to prepare payment"
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Repository returned failure")
                        Log.e(TAG, "Error: ${error.message}", error)
                        _paymentState.value = PaymentState.Error(
                            message = error.message ?: "Failed to prepare payment"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception in preparePayment", e)
                _paymentState.value = PaymentState.Error(
                    message = e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    /**
     * Handles PaymentSheet result after user completes payment
     */
    fun handlePaymentResult(result: PaymentSheetResult) {
        when (result) {
            is PaymentSheetResult.Completed -> {
                Log.d(TAG, "Payment completed successfully")
                _paymentState.value = PaymentState.Success(
                    transactionId = _clientSecret.value ?: "",
                    amount = 0, // Will be populated from campaign data
                    currency = ""
                )
            }
            is PaymentSheetResult.Canceled -> {
                Log.d(TAG, "Payment cancelled by user")
                _paymentState.value = PaymentState.Cancelled
            }
            is PaymentSheetResult.Failed -> {
                Log.e(TAG, "Payment failed: ${result.error.message}")
                _paymentState.value = PaymentState.Error(
                    message = result.error.message ?: "Payment failed"
                )
            }
        }
    }

    /**
     * Resets payment state to idle
     */
    fun resetPayment() {
        _paymentState.value = PaymentState.Idle
        _clientSecret.value = null
    }
}

/**
 * Payment states
 */
sealed class PaymentState {
    object Idle : PaymentState()
    object Loading : PaymentState()
    object Ready : PaymentState()  // Payment intent created, ready to show PaymentSheet
    
    data class Success(
        val transactionId: String,
        val amount: Long,
        val currency: String
    ) : PaymentState()
    
    data class Error(
        val message: String
    ) : PaymentState()
    
    object Cancelled : PaymentState()
}
