package com.example.dotlog.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dotlog.data.SearchResult
import com.example.dotlog.data.Visit
import com.example.dotlog.ui.theme.DotlogTheme
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.focus.onFocusChanged

@Composable
fun MainScreenRoot(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var permissionsGranted by remember { mutableStateOf(checkPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> permissionsGranted = checkPermissions(context) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val csvContent = inputStream?.bufferedReader()?.readText() ?: ""
            inputStream?.close()
            viewModel.onAction(MainAction.OnImportVisits(csvContent))
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MainEvent.VisitLogged -> snackbarHostState.showSnackbar("Visit logged")
                is MainEvent.ExportReady -> {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(android.content.Intent.EXTRA_TEXT, event.csvContent)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "dotLog visits export")
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Export visits"))
                }
            }
        }
    }

    DotlogTheme(darkTheme = state.isDarkMode) {
        Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!permissionsGranted) {
                    PermissionRequestScreen(onRequestPermissions = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    })
                } else {
                    val onImportRequest = remember {
                        { importLauncher.launch(arrayOf("text/*", "*/*")) }
                    }
                    MainScreen(
                        state = state,
                        onAction = viewModel::onAction,
                        onImportRequest = onImportRequest
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    state: MainState,
    onAction: (MainAction) -> Unit,
    onImportRequest: () -> Unit = {}
) {
    var showSettings by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val showSearchDropdown = state.locationSearchResults.isNotEmpty()
            || (state.isLocationSearching && state.locationSearchQuery.length >= 2)
            || (state.locationSearchQuery.isEmpty() && isSearchFocused && state.recentSearches.isNotEmpty())

    Box(modifier = Modifier.fillMaxSize()) {
        MapViewCompose(
            modifier = Modifier.fillMaxSize(),
            currentLocation = state.currentLocation,
            visits = state.visits,
            showHistory = state.showHistoryOnMap,
            zoomTarget = state.zoomTarget,
            onZoomConsumed = { onAction(MainAction.OnZoomConsumed) },
            onMapLongPress = { lat, lon -> onAction(MainAction.OnMapLongClick(lat, lon)) }
        )

        // SCRIM: dismiss search when tapping outside
        AnimatedVisibility(
            visible = showSearchDropdown,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            isSearchFocused = false
                            if (state.locationSearchResults.isNotEmpty()) {
                                onAction(MainAction.OnClearLocationSearch)
                            }
                        }
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            FilledIconButton(
                onClick = { showSettings = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }

            Spacer(modifier = Modifier.width(8.dp))

            LocationSearchBar(
                modifier = Modifier.weight(1f),
                query = state.locationSearchQuery,
                isSearching = state.isLocationSearching,
                results = state.locationSearchResults,
                recentSearches = state.recentSearches,
                focused = isSearchFocused,
                onQueryChange = { onAction(MainAction.OnLocationSearchQueryChange(it)) },
                onClear = { onAction(MainAction.OnClearLocationSearch) },
                onResultClick = { onAction(MainAction.OnLocationSearchResultClick(it)) },
                onRecentClick = { onAction(MainAction.OnRecentSearchClick(it)) },
                onClearRecents = { onAction(MainAction.OnClearRecentSearches) },
                onFocusChanged = { isSearchFocused = it }
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = { onAction(MainAction.OnToggleHistory) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (state.showHistoryOnMap) Icons.Default.Close else Icons.Default.History,
                    contentDescription = if (state.showHistoryOnMap) "Close history" else "Show history"
                )
            }
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Settings", style = MaterialTheme.typography.titleLarge)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Dark mode",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = state.isDarkMode,
                                    onCheckedChange = {
                                        showSettings = false
                                        onAction(MainAction.OnToggleDarkMode)
                                    }
                                )
                            }
                        }
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showSettings = false
                                            onAction(MainAction.OnExportVisits)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Upload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Export visits (CSV)",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showSettings = false
                                            onImportRequest()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Import visits (CSV)",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSettings = false }) { Text("Close") }
                }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.currentPlaceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        state.currentLocation?.let {
                            Text(
                                text = "%.6f, %.6f".format(it.latitude, it.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { onAction(MainAction.OnRefreshLocation) }) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Refresh location"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val haptic = LocalHapticFeedback.current
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onAction(MainAction.OnLogClick)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = "Log current visit" },
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Visit", style = MaterialTheme.typography.titleMedium)
            }
        }

        state.pendingLogLocation?.let { loc ->
            var name by remember(loc) { mutableStateOf(state.pendingLogPlaceName) }
            val now = Calendar.getInstance()
            var selectedDateMillis by remember(loc) { mutableStateOf(now.timeInMillis) }
            var selectedHour by remember(loc) { mutableStateOf(now.get(Calendar.HOUR_OF_DAY)) }
            var selectedMinute by remember(loc) { mutableStateOf(now.get(Calendar.MINUTE)) }
            var showDatePicker by remember { mutableStateOf(false) }
            var showTimePicker by remember { mutableStateOf(false) }

            val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
            val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showTimePicker) {
                val timePickerState = rememberTimePickerState(
                    initialHour = selectedHour,
                    initialMinute = selectedMinute,
                    is24Hour = false
                )
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    title = { Text("Select time") },
                    text = { TimePicker(state = timePickerState) },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedHour = timePickerState.hour
                            selectedMinute = timePickerState.minute
                            showTimePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                    }
                )
            }

            AlertDialog(
                onDismissRequest = { onAction(MainAction.OnDismissLogLocation) },
                title = { Text("Log this location?") },
                text = {
                    Column {
                        Text(
                            text = "%.6f, %.6f".format(loc.latitude, loc.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Place name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Date & time",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = dateFormat.format(Date(selectedDateMillis)),
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                label = { Text("Date") }
                            )
                            OutlinedTextField(
                                value = timeFormat.format(
                                    remember(selectedHour, selectedMinute) {
                                        Date(
                                            Calendar.getInstance().apply {
                                                set(Calendar.HOUR_OF_DAY, selectedHour)
                                                set(Calendar.MINUTE, selectedMinute)
                                            }.timeInMillis
                                        )
                                    }
                                ),
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                label = { Text("Time") }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showDatePicker = true }) { Text("Change date") }
                            TextButton(onClick = { showTimePicker = true }) { Text("Change time") }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = selectedDateMillis
                            set(Calendar.HOUR_OF_DAY, selectedHour)
                            set(Calendar.MINUTE, selectedMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onAction(MainAction.OnConfirmLogLocation(name, cal.timeInMillis))
                    }) { Text("Confirm") }
                },
                dismissButton = {
                    TextButton(onClick = { onAction(MainAction.OnDismissLogLocation) }) { Text("Cancel") }
                }
            )
        }

        AnimatedVisibility(
            visible = state.showHistoryOnMap,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VisitsList(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.45f),
                visits = state.visits,
                searchQuery = state.searchQuery,
                onSearchQueryChange = { onAction(MainAction.OnSearchQueryChange(it)) },
                onVisitClick = { visit ->
                    onAction(MainAction.OnVisitClick(visit.latitude, visit.longitude))
                },
                onEditVisit = { visit -> onAction(MainAction.OnEditVisit(visit)) },
                onDeleteVisit = { visit -> onAction(MainAction.OnDeleteVisit(visit)) }
            )
        }
    }
}

@Composable
private fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Location permission is required to use this app.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermissions) {
                Text("Grant Permission")
            }
        }
    }
}

private fun checkPermissions(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    DotlogTheme {
        MainScreen(
            state = MainState(
                currentPlaceName = "Cairo, Egypt",
                currentLocation = Location("gps").also {
                    it.latitude = 30.0444
                    it.longitude = 31.2357
                },
                visits = listOf(
                    Visit(id = 1, latitude = 30.0444, longitude = 31.2357, placeName = "Cairo", timestamp = 0),
                    Visit(id = 2, latitude = 30.112, longitude = 31.243, placeName = "Ramses", timestamp = 0)
                )
            ),
            onAction = {}
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSearchBar(
    query: String,
    isSearching: Boolean,
    results: List<SearchResult>,
    recentSearches: List<String>,
    focused: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onRecentClick: (String) -> Unit,
    onClearRecents: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val showDropdown = results.isNotEmpty()
            || (isSearching && query.length >= 2)
            || (query.isEmpty() && focused && recentSearches.isNotEmpty())

    BackHandler(enabled = showDropdown) {
        onClear()
        keyboardController?.hide()
        focusManager.clearFocus()
        onFocusChanged(false)
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChanged(it.isFocused) },
            placeholder = { Text("Search places...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                when {
                    isSearching -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    query.isNotEmpty() -> {
                        IconButton(onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onFocusChanged(false)
                            onClear()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onFocusChanged(false)
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        AnimatedVisibility(
            visible = showDropdown,
            enter = fadeIn(tween(150)) + expandVertically(tween(150)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                when {
                    isSearching && results.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                    results.isNotEmpty() -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                            items(
                                items = results,
                                key = { it.displayName + it.latitude }
                            ) { result ->
                                SearchResultRow(
                                    result = result,
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        onFocusChanged(false)
                                        onResultClick(result)
                                    }
                                )
                                if (result != results.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                    query.isNotEmpty() && !isSearching -> {
                        EmptySearchState(query = query)
                    }
                    query.isEmpty() && recentSearches.isNotEmpty() -> {
                        RecentSearchesList(
                            searches = recentSearches,
                            onClick = {
                                onRecentClick(it)
                            },
                            onClear = onClearRecents
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${result.displayName}, ${result.type}"
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            Text(
                text = result.type,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptySearchState(
    query: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No places found for \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecentSearchesList(
    searches: List<String>,
    onClick: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }
        searches.forEach { search ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(search) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = search,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}