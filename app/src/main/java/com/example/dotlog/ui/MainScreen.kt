package com.example.dotlog.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dotlog.data.Visit
import com.example.dotlog.ui.theme.DotlogTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MainScreenRoot(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var permissionsGranted by remember { mutableStateOf(checkPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> permissionsGranted = checkPermissions(context) }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { _ -> }
    }

    if (!permissionsGranted) {
        PermissionRequestScreen(onRequestPermissions = {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        })
    } else {
        MainScreen(
            state = state,
            onAction = viewModel::onAction
        )
    }
}

@Composable
private fun MainScreen(
    state: MainState,
    onAction: (MainAction) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MapViewCompose(
            modifier = Modifier.fillMaxSize(),
            currentLocation = state.currentLocation,
            visits = state.visits,
            showHistory = state.showHistoryOnMap,
            zoomTarget = state.zoomTarget,
            onZoomConsumed = { onAction(MainAction.OnZoomConsumed) }
        )

        FilledTonalButton(
            onClick = { onAction(MainAction.OnToggleHistory) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .statusBarsPadding()
        ) {
            Text(if (state.showHistoryOnMap) "Hide History" else "Show History")
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = state.currentPlaceName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    state.currentLocation?.let {
                        Text(
                            text = "%.6f, %.6f".format(it.latitude, it.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = { onAction(MainAction.OnLogClick) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics { contentDescription = "Log current visit" }
            ) {
                Text("Log")
            }
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
                onVisitClick = { visit ->
                    onAction(MainAction.OnVisitClick(visit.latitude, visit.longitude))
                }
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
