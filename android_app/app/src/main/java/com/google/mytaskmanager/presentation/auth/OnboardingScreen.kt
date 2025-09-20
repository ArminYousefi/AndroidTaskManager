package com.google.mytaskmanager.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("TaskFlow", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Organize your work and life, one task at a time", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp),)
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    FeatureRow("Organize tasks in boards and lists")
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureRow("Works offline and syncs automatically")
                }
                Spacer(modifier = Modifier.height(36.dp))
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Get Started")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("By continuing, you agree to our Terms of Service and Privacy Policy", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp),)
            }
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center) {
            Text("✓", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
