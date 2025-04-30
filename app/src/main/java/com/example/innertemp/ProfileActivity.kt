package com.example.innertemp

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.ui.graphics.Color as ComposeColor


class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InnerTempTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProfileScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
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
        )
    }

    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var heightErrorMessage by remember { mutableStateOf("") }
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
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = ComposeColor.Transparent
                        )
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Text("Name:", fontSize = 18.sp)
                // Spacer(modifier = Modifier.width(8.dp))

                if (isEditMode) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; isDataChanged = true},
                        label = { Text("Enter your name") },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        text = if (name.isEmpty()) "Name: Not set" else "Name: $name",
                        style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, fontSize = 20.sp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Text("Gender:", fontSize = 18.sp)
                // Spacer(modifier = Modifier.width(8.dp))

                if (isEditMode) {
                    ExposedDropdownMenuBox(
                        expanded = genderExpanded,
                        onExpandedChange = { genderExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedGender,
                            onValueChange = {},
                            label = { Text("Select gender") },
                            readOnly = true,
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
                    Text(
                        text = if (selectedGender.isEmpty()) "Gender: Not set" else "Gender: $selectedGender",
                        style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, fontSize = 20.sp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Text("Date of Birth:", fontSize = 18.sp)
                // Spacer(modifier = Modifier.width(8.dp))

                if (isEditMode) {
                    OutlinedTextField(
                        value = dateOfBirth,
                        onValueChange = { isDataChanged = true},
                        label = { Text("Select date of birth") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { datePickerDialog.show() }

                    )
                } else {
                    Text(
                        text = if (dateOfBirth.isEmpty()) "Date of Birth: Not set" else "Date of Birth: $dateOfBirth",
                        style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, fontSize = 20.sp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Text("Height:", fontSize = 18.sp)
                // Spacer(modifier = Modifier.width(8.dp))

                if (isEditMode) {
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it; isDataChanged = true },
                        label = { Text("Enter height") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = { Text("cm") },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = if (height.isEmpty()) "Height: Not set" else "Height: $height cm",
                        style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, fontSize = 20.sp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Text("Weight:", fontSize = 18.sp)
                // Spacer(modifier = Modifier.width(8.dp))

                if (isEditMode) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it; isDataChanged = true },
                        label = { Text("Enter weight") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = { Text("kg") },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = if (weight.isEmpty()) "Weight: Not set" else "Weight: $weight kg",
                        style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, fontSize = 20.sp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (isEditMode) {
                    ExposedDropdownMenuBox(
                        expanded = athleticLevelExpanded,
                        onExpandedChange = { athleticLevelExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = athleticLevel,
                            onValueChange = {},
                            label = { Text("Select athletic level") },
                            readOnly = true,
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
                    Text(
                        text = if (athleticLevel.isEmpty()) "Athletic Level: Not set" else "Athletic Level: $athleticLevel",
                        style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, fontSize = 20.sp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                if (isEditMode) {
                    heightErrorMessage = ""
                    val heightValue = height.toIntOrNull()

                    if (heightValue == null) {
                        heightErrorMessage = "Invalid height input."
                    } else if (heightValue < 50) {
                        heightErrorMessage = "Entered height is too small."
                    } else if (heightValue > 250) {
                        heightErrorMessage = "Entered height is too big."
                    }

                    if (heightErrorMessage.isEmpty()) {
                        saveUserProfile(context, name, selectedGender, dateOfBirth, height, weight, athleticLevel)
                        isEditMode = false
                        isDataChanged = false
                    }
                } else {
                    isEditMode = true
                }
            }) {
                Text(if (isEditMode) "Save" else "Edit")
            }


            if (heightErrorMessage.isNotEmpty()) {
                Text(
                    text = heightErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
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

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    InnerTempTheme {
        ProfileScreen(onBack = {})
    }
}