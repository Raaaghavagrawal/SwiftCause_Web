package com.example.swiftcause

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    
    // Show loading overlay when payment intent is being created
    Box(modifier = modifier.fillMaxSize()) {
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
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                    }
                )
            }
        }
        
        // Modern loading overlay when preparing payment intent
        if (paymentState is PaymentState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = false) {}, // Block interactions
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .padding(32.dp)
                        .width(280.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    shadowElevation = 8.dp,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 40.dp, horizontal = 24.dp)
                    ) {
                        // Modern circular progress with custom size
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Preparing Payment",
                            fontSize = 18.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Please wait...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
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
