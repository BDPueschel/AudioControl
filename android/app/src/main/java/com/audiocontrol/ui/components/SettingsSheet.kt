package com.audiocontrol.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.ui.theme.Ink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(host: String, onHostChange: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(host) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("SERVER", fontSize = 11.sp, color = Color(Ink.txt3))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text("host:port") }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onHostChange(text); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}
