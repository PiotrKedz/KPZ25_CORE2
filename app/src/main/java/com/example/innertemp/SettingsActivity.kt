package com.example.innertemp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.innertemp.ui.theme.InnerTempTheme
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedTheme = sharedPref.getString("theme", "Light") ?: "Light"
        val useDarkTheme = savedTheme == "Dark"

        setContent {
            InnerTempTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        onBack = { finish() },
                        onThemeChanged = {
                            finish()
                            startActivity(intent)
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        },
                        onNavigateToAccount = {
                            val accountIntent = Intent(this, AccountActivity::class.java)
                            startActivity(accountIntent)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: () -> Unit,
    onNavigateToAccount: () -> Unit
) {
    val context = LocalContext.current
    var isThemeExpanded by remember { mutableStateOf(false) }
    var isUserSignedIn by remember { mutableStateOf(false) }
    var userEmail by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val authPref = context.getSharedPreferences("user_auth", Context.MODE_PRIVATE)
        isUserSignedIn = authPref.getBoolean("is_signed_in", false)
        userEmail = authPref.getString("user_email", "") ?: ""

        FirebaseAuth.getInstance().currentUser?.let { user ->
            userEmail = user.email ?: ""
        }
    }

    val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    var selectedTheme by remember {
        mutableStateOf(sharedPref.getString("theme", "Light") ?: "Light")
    }

    var pushNotificationsEnabled by remember {
        mutableStateOf(sharedPref.getBoolean("push_notifications", true))
    }

    fun saveThemePreference(theme: String) {
        with(sharedPref.edit()) {
            putString("theme", theme)
            apply()
        }
        selectedTheme = theme
        onThemeChanged()
    }

    fun savePushNotificationPreference(enabled: Boolean) {
        with(sharedPref.edit()) {
            putBoolean("push_notifications", enabled)
            apply()
        }
        pushNotificationsEnabled = enabled
    }

    fun signOut(context: Context) {
        val sharedPref = context.getSharedPreferences("user_auth", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("is_signed_in", false)
            apply()
        }
        FirebaseAuth.getInstance().signOut()
        isUserSignedIn = false
    }

    var showEmailChangeDialog by remember { mutableStateOf(false) }
    var newEmailText by remember { mutableStateOf("") }

    fun changeEmail() {
        showEmailChangeDialog = true
    }

    fun updateEmail(newEmail: String) {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { user ->
            user.updateEmail(newEmail)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val authPref = context.getSharedPreferences("user_auth", Context.MODE_PRIVATE)
                        with(authPref.edit()) {
                            putString("user_email", newEmail)
                            apply()
                        }
                        userEmail = newEmail

                        Toast.makeText(
                            context,
                            "Email updated successfully",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Failed to update email. You may need to re-authenticate.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    fun changePassword() {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { user ->
            auth.sendPasswordResetEmail(user.email ?: "")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(
                            context,
                            "Password reset email sent to your email address",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Failed to send password reset email. Please try again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    fun deleteAccount() {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { user ->
            user.delete()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(
                            context,
                            "Your account has been deleted",
                            Toast.LENGTH_LONG
                        ).show()
                        signOut(context)
                    } else {
                        Toast.makeText(
                            context,
                            "Failed to delete account. You may need to re-authenticate.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to delete your account? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteAccount()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEmailChangeDialog) {
        var emailInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showEmailChangeDialog = false },
            title = { Text("Change Email") },
            text = {
                Column {
                    Text("Enter your new email address:")
                    androidx.compose.material3.TextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        placeholder = { Text("New email address") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (emailInput.isNotEmpty()) {
                            updateEmail(emailInput)
                            showEmailChangeDialog = false
                        }
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEmailChangeDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.background
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isThemeExpanded = !isThemeExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = selectedTheme,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Icon(
                            imageVector = if (isThemeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isThemeExpanded) "Collapse" else "Expand"
                        )
                    }

                    if (isThemeExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable {
                                    saveThemePreference("Light")
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTheme == "Light",
                                onClick = {
                                    saveThemePreference("Light")
                                }
                            )
                            Text(
                                text = "Light",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable {
                                    saveThemePreference("Dark")
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTheme == "Dark",
                                onClick = {
                                    saveThemePreference("Dark")
                                }
                            )
                            Text(
                                text = "Dark",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Push Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    Switch(
                        checked = pushNotificationsEnabled,
                        onCheckedChange = { isChecked ->
                            savePushNotificationPreference(isChecked)
                        }
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (isUserSignedIn) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Email",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            Text(
                                text = "Change",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { changeEmail() }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Password",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = "Change",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { changePassword() }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                        ) {
                            Text(
                                text = "Delete Account",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { showDeleteDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = "Sign Out",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { signOut(context) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = "Sign In",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                modifier = Modifier.clickable { onNavigateToAccount() }
                            )
                        }
                    }
                }
            }
        }
    }
}