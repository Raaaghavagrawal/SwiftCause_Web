package com.example.swiftcause.utils

import android.content.Context
import com.example.swiftcause.BuildConfig
import com.stripe.android.PaymentConfiguration

/**
 * Stripe SDK Configuration
 * 
 * Publishable key is hardcoded in build.gradle.kts
 * Note: Publishable keys are safe to commit (public by design)
 */
object StripeConfig {
    
    /**
     * Stripe publishable key from BuildConfig
     */
    val STRIPE_PUBLISHABLE_KEY: String
        get() = BuildConfig.STRIPE_PUBLISHABLE_KEY
    
    // Backend Cloud Function URLs
    private const val FIREBASE_PROJECT_ID = "swiftcause"
    private const val FIREBASE_REGION = "us-central1"
    
    const val CREATE_KIOSK_PAYMENT_INTENT_URL = 
        "https://$FIREBASE_REGION-$FIREBASE_PROJECT_ID.cloudfunctions.net/createKioskPaymentIntent"
    
    /**
     * Initialize Stripe SDK
     * Should be called in MainActivity.onCreate()
     */
    fun initialize(context: Context) {
        if (STRIPE_PUBLISHABLE_KEY.isEmpty()) {
            throw IllegalStateException(
                "Stripe publishable key is not configured. " +
                "Please ensure 'stripe.publishable.key' is set in android/local.properties"
            )
        }
        PaymentConfiguration.init(context, STRIPE_PUBLISHABLE_KEY)
    }
    
    /**
     * Check if Stripe is initialized
     */
    fun isInitialized(context: Context): Boolean {
        return try {
            PaymentConfiguration.getInstance(context)
            true
        } catch (e: IllegalStateException) {
            false
        }
    }
    
    /**
     * Get current publishable key
     */
    fun getPublishableKey(context: Context): String? {
        return try {
            PaymentConfiguration.getInstance(context).publishableKey
        } catch (e: IllegalStateException) {
            null
        }
    }
}
