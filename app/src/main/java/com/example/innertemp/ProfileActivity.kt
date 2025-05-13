package com.example.innertemp

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.innertemp.ui.theme.InnerTempTheme
import androidx.compose.ui.tooling.preview.Preview
import java.util.Calendar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.outlined.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class ProfileActivity : ComponentActivity() {
    private var useDarkTheme by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadThemePreference()

        setContent {
            InnerTempTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProfileScreen(
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

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentTheme = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("theme", "Light") ?: "Light"
                val shouldUseDarkTheme = currentTheme == "Dark"

                if (shouldUseDarkTheme != useDarkTheme) {
                    useDarkTheme = shouldUseDarkTheme
                }
            }
        })
    }

    private fun loadThemePreference() {
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedTheme = sharedPref.getString("theme", "Light") ?: "Light"
        useDarkTheme = savedTheme == "Dark"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onThemeChanged: () -> Unit,
    onNavigateToAccount: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var genderExpanded by remember { mutableStateOf(false) }
    var selectedGender by remember { mutableStateOf("") }
    val context = LocalContext.current
    var dateOfBirth by remember { mutableStateOf("") }
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    var isDataChanged by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var isUserSignedIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sharedPref = context.getSharedPreferences("user_auth", Context.MODE_PRIVATE)
        isUserSignedIn = sharedPref.getBoolean("is_signed_in", false)
    }

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, monthOfYear, dayOfMonth ->
                dateOfBirth = "$dayOfMonth/${monthOfYear + 1}/$year"
                isDataChanged = true
            },
            year,
            month,
            day
        ).apply {
            datePicker.maxDate = Calendar.getInstance().timeInMillis
        }
    }

    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var heightErrorMessage by remember { mutableStateOf("") }
    var nameErrorMessage by remember { mutableStateOf("") }
    var dobErrorMessage by remember { mutableStateOf("") }
    var weightErrorMessage by remember { mutableStateOf("") }
    var showExitDialog by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var athleticLevel by remember { mutableStateOf("") }
    var athleticLevelExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sharedPref = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        name = sharedPref.getString("name", "") ?: ""
        selectedGender = sharedPref.getString("gender", "") ?: ""
        dateOfBirth = sharedPref.getString("dob", "") ?: ""
        height = sharedPref.getString("height", "") ?: ""
        weight = sharedPref.getString("weight", "") ?: ""
        athleticLevel = sharedPref.getString("athletic_level", "") ?: ""
    }

    fun saveUserProfile(
        context: Context,
        name: String,
        gender: String,
        dateOfBirth: String,
        height: String,
        weight: String,
        athleticLevel: String
    ) {
        val sharedPref = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("name", name)
            putString("gender", gender)
            putString("dob", dateOfBirth)
            putString("height", height)
            putString("weight", weight)
            putString("athletic_level", athleticLevel)
            apply()
        }
    }

    fun signOut(context: Context) {
        val sharedPref = context.getSharedPreferences("user_auth", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("is_signed_in", false)
            apply()
        }
        isUserSignedIn = false
    }

    fun validateDateOfBirth(dateString: String): Boolean {
        if (dateString.isEmpty()) return false

        val parts = dateString.split("/")
        if (parts.size != 3) return false

        try {
            val day = parts[0].toInt()
            val month = parts[1].toInt() - 1
            val year = parts[2].toInt()

            val dobCalendar = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val currentCalendar = Calendar.getInstance()

            if (dobCalendar.after(currentCalendar)) {
                return false
            }

            val oneYearAgo = Calendar.getInstance().apply {
                add(Calendar.YEAR, -1)
            }

            return dobCalendar.before(oneYearAgo)
        } catch (e: Exception) {
            return false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditMode) "Edit Profile" else "Profile",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                actions = {
                    if (!isEditMode) {
                        IconButton(onClick = {
                            val settingsIntent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(settingsIntent)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDataChanged) {
                            showExitDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Personal Information",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (isEditMode) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                if (it.length <= 20) {
                                    name = it
                                    isDataChanged = true
                                }
                            },
                            label = { Text("Enter your name") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = "Name"
                                )
                            },
                            isError = nameErrorMessage.isNotEmpty(),
                            supportingText = {
                                Text("${name.length}/20 characters")
                            }
                        )
                        if (nameErrorMessage.isNotEmpty()) {
                            Text(
                                text = nameErrorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            )
                        }
                    } else {
                        ProfileInfoRow(
                            icon = Icons.Outlined.Person,
                            label = "Name",
                            value = if (name.isEmpty()) "Not set" else name
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isEditMode) {
                        ExposedDropdownMenuBox(
                            expanded = genderExpanded,
                            onExpandedChange = { genderExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedGender,
                                onValueChange = {},
                                label = { Text("Select gender") },
                                readOnly = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.People,
                                        contentDescription = "Gender"
                                    )
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .clickable { genderExpanded = !genderExpanded }
                            )

                            ExposedDropdownMenu(
                                expanded = genderExpanded,
                                onDismissRequest = { genderExpanded = false }
                            ) {
                                DropdownMenuItem(text = { Text("Male") }, onClick = {
                                    selectedGender = "Male"
                                    genderExpanded = false
                                    isDataChanged = true
                                })
                                DropdownMenuItem(text = { Text("Female") }, onClick = {
                                    selectedGender = "Female"
                                    genderExpanded = false
                                    isDataChanged = true
                                })
                                DropdownMenuItem(text = { Text("Other") }, onClick = {
                                    selectedGender = "Other"
                                    genderExpanded = false
                                    isDataChanged = true
                                })
                            }
                        }
                    } else {
                        ProfileInfoRow(
                            icon = Icons.Outlined.People,
                            label = "Gender",
                            value = if (selectedGender.isEmpty()) "Not set" else selectedGender
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isEditMode) {
                        OutlinedTextField(
                            value = dateOfBirth,
                            onValueChange = { },
                            label = { Text("Select date of birth") },
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { datePickerDialog.show() },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Cake,
                                    contentDescription = "Date of Birth"
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Select date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { datePickerDialog.show() }
                                )
                            },
                            isError = dobErrorMessage.isNotEmpty()
                        )
                        if (dobErrorMessage.isNotEmpty()) {
                            Text(
                                text = dobErrorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            )
                        }
                    } else {
                        ProfileInfoRow(
                            icon = Icons.Outlined.Cake,
                            label = "Date of Birth",
                            value = if (dateOfBirth.isEmpty()) "Not set" else dateOfBirth
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        "Physical Measurements",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (isEditMode) {
                        OutlinedTextField(
                            value = height,
                            onValueChange = { newText ->
                                if (newText.all { it.isDigit() } && newText.length <= 3) {
                                    height = newText
                                    isDataChanged = true
                                }
                            },
                            label = { Text("Enter height") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Height,
                                    contentDescription = "Height"
                                )
                            },
                            trailingIcon = { Text("cm") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = heightErrorMessage.isNotEmpty()
                        )
                        if (heightErrorMessage.isNotEmpty()) {
                            Text(
                                text = heightErrorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            )
                        }
                    } else {
                        ProfileInfoRow(
                            icon = Icons.Outlined.Height,
                            label = "Height",
                            value = if (height.isEmpty()) "Not set" else "$height cm"
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isEditMode) {
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { newText ->
                                if (newText.all { it.isDigit() } && newText.length <= 3) {
                                    weight = newText
                                    isDataChanged = true
                                }
                            },
                            label = { Text("Enter weight") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.FitnessCenter,
                                    contentDescription = "Weight"
                                )
                            },
                            trailingIcon = { Text("kg") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = weightErrorMessage.isNotEmpty()
                        )
                        if (weightErrorMessage.isNotEmpty()) {
                            Text(
                                text = weightErrorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            )
                        }
                    } else {
                        ProfileInfoRow(
                            icon = Icons.Outlined.FitnessCenter,
                            label = "Weight",
                            value = if (weight.isEmpty()) "Not set" else "$weight kg"
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        "Athletic Information",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (isEditMode) {
                        ExposedDropdownMenuBox(
                            expanded = athleticLevelExpanded,
                            onExpandedChange = { athleticLevelExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = athleticLevel,
                                onValueChange = {},
                                label = { Text("Select athletic level") },
                                readOnly = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.SportsScore,
                                        contentDescription = "Athletic Level"
                                    )
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = athleticLevelExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .clickable { athleticLevelExpanded = !athleticLevelExpanded }
                            )

                            ExposedDropdownMenu(
                                expanded = athleticLevelExpanded,
                                onDismissRequest = { athleticLevelExpanded = false }
                            ) {
                                DropdownMenuItem(text = { Text("Low") }, onClick = {
                                    athleticLevel = "Low"
                                    athleticLevelExpanded = false
                                    isDataChanged = true
                                })
                                DropdownMenuItem(text = { Text("Medium") }, onClick = {
                                    athleticLevel = "Medium"
                                    athleticLevelExpanded = false
                                    isDataChanged = true
                                })
                                DropdownMenuItem(text = { Text("High") }, onClick = {
                                    athleticLevel = "High"
                                    athleticLevelExpanded = false
                                    isDataChanged = true
                                })
                            }
                        }
                    } else {
                        ProfileInfoRow(
                            icon = Icons.Outlined.SportsScore,
                            label = "Athletic Level",
                            value = if (athleticLevel.isEmpty()) "Not set" else athleticLevel
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (isEditMode) {
                        nameErrorMessage = ""
                        heightErrorMessage = ""
                        dobErrorMessage = ""
                        weightErrorMessage = ""

                        if (name.isEmpty()) {
                            nameErrorMessage = "Name cannot be empty."
                        } else if (name.length > 20) {
                            nameErrorMessage = "Name cannot exceed 20 characters."
                        }

                        val heightValue = height.toIntOrNull()
                        if (heightValue == null) {
                            heightErrorMessage = "Height is required."
                        } else if (heightValue < 50) {
                            heightErrorMessage = "Entered height is too small."
                        } else if (heightValue > 250) {
                            heightErrorMessage = "Entered height is too big."
                        }

                        val weightValue = weight.toIntOrNull()
                        if (weightValue == null) {
                            weightErrorMessage = "Weight is required."
                        } else if (weightValue < 1) {
                            weightErrorMessage = "Weight must be at least 1 kg."
                        } else if (weightValue > 250) {
                            weightErrorMessage = "Weight cannot exceed 250 kg."
                        }

                        if (dateOfBirth.isEmpty()) {
                            dobErrorMessage = "Date of birth is required."
                        } else if (!validateDateOfBirth(dateOfBirth)) {
                            dobErrorMessage = "You are required to be at least 1 year old."
                        }

                        if (nameErrorMessage.isEmpty() && heightErrorMessage.isEmpty() &&
                            dobErrorMessage.isEmpty() && weightErrorMessage.isEmpty()) {
                            saveUserProfile(context, name, selectedGender, dateOfBirth, height, weight, athleticLevel)
                            isEditMode = false
                            isDataChanged = false
                        }
                    } else {
                        isEditMode = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (isEditMode) "Save" else "Edit",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isEditMode) "Save" else "Edit Profile",
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isEditMode) {
                Button(
                    onClick = {
                        if (isUserSignedIn) {
                            signOut(context)
                        } else {
                            onNavigateToAccount()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isUserSignedIn)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = if (isUserSignedIn) Icons.Default.Logout else Icons.Default.Login,
                        contentDescription = if (isUserSignedIn) "Sign Out" else "Sign In",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isUserSignedIn) "Sign Out" else "Sign In / Sign Up",
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    title = { Text("Changes have not been saved.") },
                    text = { Text("Do you want to continue?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showExitDialog = false
                                onBack()
                            }
                        ) {
                            Text("Continue")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                showExitDialog = false
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (value == "Not set") FontWeight.Normal else FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    InnerTempTheme {
        ProfileScreen(
            onBack = {},
            onThemeChanged = {},
            onNavigateToAccount = {}
        )
    }
}