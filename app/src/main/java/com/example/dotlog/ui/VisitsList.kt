package com.example.dotlog.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dotlog.data.Visit
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun VisitsList(
    modifier: Modifier = Modifier,
    visits: List<Visit>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onVisitClick: (Visit) -> Unit,
    onEditVisit: (Visit) -> Unit,
    onDeleteVisit: (Visit) -> Unit
) {
    var showOptionsDialog by remember { mutableStateOf(false) }
    var selectedVisit by remember { mutableStateOf<Visit?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }

    selectedVisit?.let { visit ->
        if (showOptionsDialog) {
            AlertDialog(
                onDismissRequest = { showOptionsDialog = false },
                title = { Text(visit.placeName) },
                text = {
                    Column {
                        TextButton(
                            onClick = {
                                editName = visit.placeName
                                showOptionsDialog = false
                                showEditDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Edit name") }
                        TextButton(
                            onClick = {
                                showOptionsDialog = false
                                onDeleteVisit(visit)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showOptionsDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit name") },
                text = {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Place name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onEditVisit(visit.copy(placeName = editName))
                        showEditDialog = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
                }
            )
        }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search places...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            if (visits.isEmpty()) {
                EmptyVisitsState(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(visits, key = { it.id }) { visit ->
                        VisitItem(
                            visit = visit,
                            onClick = { onVisitClick(visit) },
                            onLongClick = {
                                selectedVisit = visit
                                showOptionsDialog = true
                            }
                        )
                    }
                }
            }

            val uriHandler = LocalUriHandler.current
            TextButton(
                onClick = { uriHandler.openUri("https://www.openstreetmap.org/fixthemap") },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Report a map issue", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VisitItem(visit: Visit, onClick: () -> Unit, onLongClick: () -> Unit) {
    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val dateString = remember(visit.timestamp) { sdf.format(Date(visit.timestamp)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = visit.placeName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = dateString,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = String.format(Locale.getDefault(), "%.4f, %.4f", visit.latitude, visit.longitude),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun EmptyVisitsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No visits yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Your logged visits will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
