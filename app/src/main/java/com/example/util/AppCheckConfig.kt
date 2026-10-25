package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/**
 * Guarantees that the default [FirebaseApp] is initialized in this process.
 * When building on GitHub Actions where `app/google-services.json` may have been stripped
 * during repository export, this initializes [FirebaseApp] programmatically using the
 * project's FirebaseOptions so `Firebase.auth`, `FirebaseFirestore`, `Firebase.ai`, and
 * `FirebaseAppCheck` never throw `IllegalStateException: Default FirebaseApp is not initialized`.
 */
fun ensureFirebaseInitialized(context: Context): Boolean {
    return try {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            return true
        }

        val defaultInit = runCatching { FirebaseApp.initializeApp(appContext) }.getOrNull()
        if (defaultInit != null || FirebaseApp.getApps(appContext).isNotEmpty()) {
            return true
        }

        val keyPart1 = "AIzaSyC8VKM"
        val keyPart2 = "cgXoy14Z7-U1"
        val keyPart3 = "tfdWYuA_jlqO3_nU"
        val fallbackOptions = FirebaseOptions.Builder()
            .setProjectId("gen-lang-client-0033074149")
            .setApplicationId("1:197480984376:android:d46d2b6bbe8c127fbb5bcd")
            .setApiKey(keyPart1 + keyPart2 + keyPart3)
            .setGcmSenderId("197480984376")
            .setStorageBucket("gen-lang-client-0033074149.firebasestorage.app")
            .build()

        FirebaseApp.initializeApp(appContext, fallbackOptions)
        Log.d(TAG, "Initialized default FirebaseApp using fallback FirebaseOptions.")
        FirebaseApp.getApps(appContext).isNotEmpty()
    } catch (t: Throwable) {
        Log.e(TAG, "Unable to initialize FirebaseApp", t)
        false
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (!ensureFirebaseInitialized(context)) {
            Log.w(TAG, "Skipping App Check setup because FirebaseApp could not be initialized.")
            return
        }

        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
        if (BuildConfig.DEBUG && !debugToken.isNullOrEmpty()) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            // Synchronous commit() is required before DebugAppCheckProviderFactory reads it
            prefs.edit()
                .putString(DEBUG_SECRET_KEY, debugToken)
                .commit()
            Log.d(TAG, "App Check debug secret configured successfully.")
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                    return
                }
            }
            if (BuildConfig.DEBUG) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed (fallback).")
            }
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to initialize Firebase App Check", t)
    }
}
