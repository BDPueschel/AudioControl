package com.audiocontrol.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiocontrol.core.FilterType
import com.audiocontrol.core.V1_FILTER_TYPES
import com.audiocontrol.ui.theme.Ink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterTypeDropdown(selected: FilterType, onSelect: (FilterType) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected.label, onValueChange = {}, readOnly = true,
                label = { Text("Filter") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                V1_FILTER_TYPES.forEach { t ->
                    DropdownMenuItem(text = { Text(t.label) }, onClick = { onSelect(t); expanded = false })
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(selected.caption, fontSize = 11.sp, color = Color(Ink.txt2))
    }
}
