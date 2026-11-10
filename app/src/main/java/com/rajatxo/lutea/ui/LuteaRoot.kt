package com.rajatxo.lutea.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rajatxo.lutea.data.prefs.UserNameManager
import com.rajatxo.lutea.ui.screens.NameCardScreen
import com.rajatxo.lutea.ui.screens.QuickPicksScreen

/**
 * Lutea — root composable.
 *
 * Routes between:
 *   - NameCardScreen (onboarding — shows ONLY on first ever launch)
 *   - QuickPicksScreen (first page of the app — shows on every subsequent launch)
 *
 * Onboarding completion is persisted in SharedPreferences so the onboarding
 * only shows once. After that, the app always opens directly to Quick Picks.
 */
@Composable
fun LuteaRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lutea_prefs", Context.MODE_PRIVATE) }

    // Read the persisted flag ONCE on first composition.
    // If onboarding was already completed (saved in prefs), skip straight to Quick Picks.
    var onboardingComplete by remember {
        mutableStateOf(prefs.getBoolean("onboarding_complete", false))
    }

    if (!onboardingComplete) {
        NameCardScreen(
            onComplete = { displayName ->
                // Persist the user's name so it can be read anywhere via UserNameManager.
                UserNameManager.setName(displayName, context)
                // Persist the flag so onboarding never shows again.
                prefs.edit().putBoolean("onboarding_complete", true).apply()
                onboardingComplete = true
            },
        )
    } else {
        QuickPicksScreen()
    }
}
