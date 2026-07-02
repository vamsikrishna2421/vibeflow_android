package com.vibeflow.mobile.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibeflow.mobile.auth.SupabaseAuth
import com.vibeflow.mobile.ui.components.GradientIcon
import com.vibeflow.mobile.ui.components.brandBrush

/** A plan: id, title, price line, sub-line, and whether it's the highlighted "best value". */
private data class Plan(val id: String, val title: String, val price: String, val note: String, val best: Boolean)

private val PLANS = listOf(
    Plan("monthly", "Monthly", "$4.99", "billed monthly", best = false),
    Plan("annual", "Annual", "$39.99", "$3.33/mo · save 33%", best = true),
)

private val PRO_FEATURES = listOf(
    Icons.Filled.AutoAwesome to "Unlimited AI polishes — no weekly cap",
    Icons.Filled.Bolt to "Priority, higher-quality formatting models",
    Icons.Filled.Devices to "Use VibeFlow on all your devices",
    Icons.Filled.Language to "Every voice language & on-device packs",
    Icons.Filled.Tune to "Early access to new features",
)

/**
 * VibeFlow Pro — the plans / subscription area. Explains Free vs Pro, the plans, multi-device,
 * and connects to Google Play to subscribe (placeholder until Play Billing is wired).
 */
@Composable
fun SubscriptionScreen(quota: SupabaseAuth.Quota?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isPro = quota?.isPro == true
    var selectedPlan by remember { mutableStateOf("annual") }

    fun connectToPlay() {
        Toast.makeText(context, "Connecting to Google Play…", Toast.LENGTH_SHORT).show()
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface) }
            Text("VibeFlow Pro", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            // Hero
            GradientIcon(Icons.Filled.AutoAwesome, boxSize = 58.dp, iconSize = 32.dp, radius = 17.dp)
            Spacer(Modifier.height(14.dp))
            Text("Go unlimited.", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                if (isPro) "You're on Pro — thank you. Enjoy unlimited polishes on every device."
                else "Unlimited AI polish, priority models, and VibeFlow on all your devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            // What Pro unlocks
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    PRO_FEATURES.forEachIndexed { i, (icon, text) ->
                        FeatureRow(icon, text)
                        if (i != PRO_FEATURES.lastIndex) Spacer(Modifier.height(14.dp))
                    }
                }
            }

            if (!isPro) {
                Spacer(Modifier.height(22.dp))
                Text("Choose a plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PLANS.forEach { plan ->
                        PlanCard(plan, selected = selectedPlan == plan.id, modifier = Modifier.weight(1f)) { selectedPlan = plan.id }
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Upgrade CTA → Google Play
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(brandBrush())
                        .clickable { connectToPlay() }.padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Upgrade with Google Play", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Billed through Google Play. Cancel anytime. Prices shown are indicative until launch.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { connectToPlay() }, contentPadding = PaddingValues(0.dp)) {
                    Text("Restore a purchase", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(22.dp))
            // Devices
            Text("Your devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    DeviceLine("Free", "1 device at a time — signing in on a new phone moves your account to it.")
                    Spacer(Modifier.height(12.dp))
                    DeviceLine("Pro", "Stay signed in on all your devices — phone, tablet and more.")
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "To add a device: install VibeFlow on it and sign in with the same Google account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

@Composable
private fun PlanCard(plan: Plan, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
                if (plan.best) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(brandBrush()).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("BEST", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(plan.price, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(plan.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeviceLine(tag: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 3.dp),
        ) { Text(tag, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
    }
}
