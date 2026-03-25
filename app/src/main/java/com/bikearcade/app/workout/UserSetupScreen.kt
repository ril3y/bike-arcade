package com.bikearcade.app.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bikearcade.settings.UserProfile
import com.bikearcade.ui.theme.NeonAccent
import com.bikearcade.ui.theme.SurfaceBright
import com.bikearcade.ui.theme.SurfaceDark
import com.bikearcade.ui.theme.TextPrimary
import com.bikearcade.ui.theme.TextSecondary

@Composable
fun UserSetupScreen(
    existingProfile: UserProfile = UserProfile.EMPTY,
    onSave: (UserProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(existingProfile.name) }
    var age by remember { mutableStateOf(if (existingProfile.age > 0) existingProfile.age.toString() else "") }
    var weight by remember { mutableStateOf(if (existingProfile.weightLbs > 0) existingProfile.weightLbs.toString() else "") }
    var height by remember { mutableStateOf(if (existingProfile.heightInches > 0) existingProfile.heightInches.toString() else "") }
    var bodyFat by remember { mutableStateOf(existingProfile.bodyFatPercent?.toString() ?: "") }
    var targetWeight by remember { mutableStateOf(existingProfile.targetWeightLbs?.toString() ?: "") }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = NeonAccent,
        unfocusedBorderColor = TextSecondary,
        cursorColor = NeonAccent,
        focusedLabelColor = NeonAccent,
        unfocusedLabelColor = TextSecondary,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "RIDER SETUP",
            style = MaterialTheme.typography.headlineLarge,
            color = NeonAccent,
        )

        Spacer(Modifier.height(32.dp))

        // Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            colors = textFieldColors,
            modifier = Modifier.width(400.dp),
        )

        Spacer(Modifier.height(16.dp))

        // Age
        OutlinedTextField(
            value = age,
            onValueChange = { age = it.filter { c -> c.isDigit() } },
            label = { Text("Age") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = textFieldColors,
            modifier = Modifier.width(400.dp),
        )

        Spacer(Modifier.height(16.dp))

        // Weight + Height row
        Row(
            modifier = Modifier.width(400.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Weight (lbs)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = textFieldColors,
                modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
                value = height,
                onValueChange = { height = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Height (in)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = textFieldColors,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        // Body fat + Target weight row (optional)
        Row(
            modifier = Modifier.width(400.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = bodyFat,
                onValueChange = { bodyFat = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Body Fat % (opt)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = textFieldColors,
                modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
                value = targetWeight,
                onValueChange = { targetWeight = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Target lbs (opt)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = textFieldColors,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(32.dp))

        val isValid = name.isNotBlank() &&
                age.toIntOrNull()?.let { it in 10..100 } == true &&
                weight.toFloatOrNull()?.let { it > 0 } == true &&
                height.toFloatOrNull()?.let { it > 0 } == true

        Button(
            onClick = {
                onSave(UserProfile(
                    name = name.trim(),
                    age = age.toIntOrNull() ?: 30,
                    weightLbs = weight.toFloatOrNull() ?: 150f,
                    heightInches = height.toFloatOrNull() ?: 68f,
                    bodyFatPercent = bodyFat.toFloatOrNull(),
                    targetWeightLbs = targetWeight.toFloatOrNull(),
                ))
            },
            enabled = isValid,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonAccent,
                contentColor = SurfaceDark,
                disabledContainerColor = SurfaceBright,
            ),
            modifier = Modifier.width(400.dp).height(48.dp),
        ) {
            Text(
                text = if (existingProfile.isValid) "UPDATE PROFILE" else "START RIDING",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
