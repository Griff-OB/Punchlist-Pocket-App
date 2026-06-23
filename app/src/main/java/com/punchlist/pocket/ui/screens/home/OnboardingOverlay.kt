package com.punchlist.pocket.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * One step of the first-launch tutorial: an icon, a short title, and a one-line
 * description of what that part of the app does.
 */
private data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val description: String
)

/**
 * The ordered tutorial steps. Each maps to a real feature on the Home screen so
 * the user learns to navigate the app: how to search, open the drawer, filter,
 * generate a PDF, add a job, attach/annotate photos, and tap into a job.
 */
private val onboardingSteps = listOf(
    OnboardingStep(
        icon = Icons.Default.Search,
        title = "Search",
        description = "Find any job fast by name, client, or site address."
    ),
    OnboardingStep(
        icon = Icons.Default.Menu,
        title = "Menu",
        description = "Open the menu to switch between Light, Dark, and System themes, or open Templates."
    ),
    OnboardingStep(
        icon = Icons.Default.FilterList,
        title = "Filter",
        description = "Slice your jobs by All, Active, Due Soon, or Completed."
    ),
    OnboardingStep(
        icon = Icons.Default.Description,
        title = "PDF Reports",
        description = "Tap \u201CView PDF report\u201D on a job card to build a client-ready report you can share or print."
    ),
    OnboardingStep(
        icon = Icons.Default.AddCircle,
        title = "Add a Job",
        description = "Tap the + button to create a new job, then add punch items to it."
    ),
    OnboardingStep(
        icon = Icons.Default.AddAPhoto,
        title = "Photos & Markup",
        description = "On a punch item, snap a photo or pick from your gallery, then annotate it with drawings."
    ),
    OnboardingStep(
        icon = Icons.Default.TouchApp,
        title = "Tap a Job",
        description = "Tap any job card to open its detail, manage items, and track progress."
    )
)

/**
 * The full-screen first-launch tutorial. A semi-transparent scrim dims the app
 * behind a centered card that walks through each feature step by step. Shown
 * once per device (gated by the `onboardingCompleted` DataStore flag); the host
 * calls [onFinished] when the user reaches the end or taps Skip so the flag is
 * persisted.
 *
 * Uses a centered card rather than spotlight-style pointers: spotlight
 * coordinates are fragile across screen sizes/densities, while a clear card
 * sequence reliably teaches the same navigation on every device.
 */
@Composable
fun OnboardingOverlay(onFinished: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val total = onboardingSteps.size
    val current = onboardingSteps[step]
    val isLast = step == total - 1

    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.45f))
                    )
                )
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Progress dots — one per step, current one filled/brand.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        repeat(total) { i ->
                            val active = i == step
                            Box(
                                modifier = Modifier
                                    .size(if (active) 10.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                            )
                        }
                    }

                    // Feature icon in a brand-tinted circle.
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = current.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Spacer(Modifier.size(16.dp))
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = current.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.size(24.dp))

                    // Skip is always available; the right-side button advances
                    // or, on the last step, finishes the tutorial.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onFinished) {
                            Text(
                                text = if (isLast) "" else "Skip",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = {
                            if (isLast) onFinished() else step++
                        }) {
                            Text(if (isLast) "Get Started" else "Next")
                        }
                    }
                }
            }
        }
    }
}
