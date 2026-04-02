package com.example.swiftcause

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.swiftcause.domain.models.Campaign
import com.example.swiftcause.domain.models.KioskSession
import com.example.swiftcause.presentation.screens.CampaignDetailsScreen
import com.example.swiftcause.presentation.screens.CampaignListScreen
import com.example.swiftcause.presentation.screens.KioskLoginScreen
import com.example.swiftcause.presentation.viewmodels.CampaignListViewModel
import com.example.swiftcause.presentation.viewmodels.PaymentState
import com.example.swiftcause.presentation.viewmodels.PaymentViewModel
import com.example.swiftcause.ui.theme.SwiftCauseTheme
import com.example.swiftcause.utils.StripeConfig
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Stripe with key from local.properties (from root .env)
        StripeConfig.initialize(this)
        
        setContent {
            SwiftCauseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var kioskSession by remember { mutableStateOf<KioskSession?>(null) }
                    
                    when {
                        kioskSession == null -> {
                            KioskLoginScreen(
                                onLoginSuccess = { session ->
                                    kioskSession = session
                                }
                            )
                        }
                        else -> {
                            KioskMainContent(
                                kioskSession = kioskSession!!,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KioskMainContent(
    kioskSession: KioskSession,
    modifier: Modifier = Modifier,
    viewModel: CampaignListViewModel = viewModel(),
    paymentViewModel: PaymentViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val paymentState by paymentViewModel.paymentState.collectAsState()
    val clientSecret by paymentViewModel.clientSecret.collectAsState()
    val context = LocalContext.current
    
    // Initialize PaymentSheet
    val paymentSheet = rememberPaymentSheet { result ->
        paymentViewModel.handlePaymentResult(result)
    }
    
    // Handle payment state changes
    LaunchedEffect(paymentState) {
        when (paymentState) {
            is PaymentState.Ready -> {
                // Show PaymentSheet when payment intent is ready
                clientSecret?.let { secret ->
                    paymentSheet.presentWithPaymentIntent(
                        paymentIntentClientSecret = secret,
                        configuration = PaymentSheet.Configuration(
                            merchantDisplayName = "SwiftCause",
                            // Disable all delayed payment methods (wallets, BNPL)
                            allowsDelayedPaymentMethods = false,
                            // Don't collect any billing details - card details only
                            billingDetailsCollectionConfiguration = PaymentSheet.BillingDetailsCollectionConfiguration(
                                name = PaymentSheet.BillingDetailsCollectionConfiguration.CollectionMode.Never,
                                email = PaymentSheet.BillingDetailsCollectionConfiguration.CollectionMode.Never,
                                phone = PaymentSheet.BillingDetailsCollectionConfiguration.CollectionMode.Never,
                                address = PaymentSheet.BillingDetailsCollectionConfiguration.AddressCollectionMode.Never,
                                attachDefaultsToPaymentMethod = false
                            )
                        )
                    )
                }
            }
            is PaymentState.Success -> {
                val success = paymentState as PaymentState.Success
                Toast.makeText(
                    context,
                    "Donation successful! Thank you for your support.",
                    Toast.LENGTH_LONG
                ).show()
                // Navigate back to campaign list after success
                viewModel.clearSelectedCampaign()
                paymentViewModel.resetPayment()
            }
            is PaymentState.Error -> {
                val error = paymentState as PaymentState.Error
                Toast.makeText(
                    context,
                    "Payment failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                paymentViewModel.resetPayment()
            }
            is PaymentState.Cancelled -> {
                Toast.makeText(
                    context,
                    "Payment cancelled",
                    Toast.LENGTH_SHORT
                ).show()
                paymentViewModel.resetPayment()
            }
            else -> { /* Idle or Loading */ }
        }
    }
    
    LaunchedEffect(kioskSession) {
        viewModel.loadCampaigns(kioskSession)
    }
    
    when {
        uiState.selectedCampaign != null -> {
            val campaign = uiState.selectedCampaign!!
            CampaignDetailsScreen(
                campaign = campaign,
                onBackClick = { viewModel.clearSelectedCampaign() },
                onDonateClick = { amount, isRecurring, interval ->
                    handleDonation(
                        campaign = campaign,
                        amount = amount,
                        isRecurring = isRecurring,
                        interval = interval,
                        paymentViewModel = paymentViewModel
                    )
                }
            )
        }
        uiState.isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = uiState.error ?: "Error loading campaigns")
            }
        }
        else -> {
            CampaignListScreen(
                campaigns = uiState.campaigns,
                isLoading = false,
                onCampaignClick = { campaign ->
                    viewModel.selectCampaign(campaign)
                },
                onAmountClick = { campaign, amount ->
                    // Quick donate: directly trigger payment with clicked amount
                    handleDonation(
                        campaign = campaign,
                        amount = amount * 100, // Convert to minor units (cents/pence)
                        isRecurring = false,
                        interval = null,
                        paymentViewModel = paymentViewModel
                    )
                },
                onDonateClick = { campaign ->
                    viewModel.selectCampaign(campaign)
                },
                modifier = modifier
            )
        }
    }
}

/**
 * Handles donation by preparing payment intent
 */
private fun handleDonation(
    campaign: Campaign,
    amount: Long,
    isRecurring: Boolean,
    interval: String?,
    paymentViewModel: PaymentViewModel
) {
    android.util.Log.d("MainActivity", "=== Donation Button Clicked ===")
    android.util.Log.d("MainActivity", "Campaign: ${campaign.title}")
    android.util.Log.d("MainActivity", "Campaign ID: ${campaign.id}")
    android.util.Log.d("MainActivity", "Organization ID: ${campaign.organizationId}")
    android.util.Log.d("MainActivity", "Amount: $amount cents")
    android.util.Log.d("MainActivity", "Currency: ${campaign.currency}")
    android.util.Log.d("MainActivity", "Is Recurring: $isRecurring")
    android.util.Log.d("MainActivity", "Interval: $interval")
    
    // Get currency from campaign
    val currency = campaign.currency.lowercase()
    
    // Determine frequency for backend
    val frequency = if (isRecurring) {
        when (interval) {
            "monthly" -> "month"
            "yearly" -> "year"
            else -> "month"
        }
    } else {
        null  // One-time donation
    }
    
    android.util.Log.d("MainActivity", "Calling PaymentViewModel.preparePayment()")
    
    // Prepare payment
    paymentViewModel.preparePayment(
        amount = amount,
        currency = currency,
        campaignId = campaign.id,
        campaignTitle = campaign.title,
        organizationId = campaign.organizationId,
        isAnonymous = true,  // Kiosk donations are anonymous by default
        frequency = frequency
    )
}
