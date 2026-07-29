package com.example.dotlog.ui

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.dotlog.data.Visit
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.TilesOverlay
@Composable
fun MapViewCompose(
    modifier: Modifier = Modifier,
    currentLocation: Location?,
    visits: List<Visit>,
    showHistory: Boolean,
    zoomTarget: Location?,
    onZoomConsumed: () -> Unit,
    onMapLongPress: (Double, Double) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    val mapView = remember {
        // Verify config at the exact moment of MapView creation
        android.util.Log.d(
            "Dotlog",
            "Config userAgent at MapView creation: ${Configuration.getInstance().userAgentValue}"
        )

        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.0)

            val copyrightOverlay = CopyrightOverlay(context)
            copyrightOverlay.setAlignRight(true)
            overlays.add(copyrightOverlay)
        }
    }

    val currentOnLongPress by rememberUpdatedState(onMapLongPress)
    remember {
        mapView.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = false
            override fun longPressHelper(p: GeoPoint): Boolean {
                currentOnLongPress(p.latitude, p.longitude)
                return true
            }
        }))
    }

    val currentMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "You are here"
        }
    }

    val historyOverlay = remember { FolderOverlay() }

    LaunchedEffect(visits) {
        historyOverlay.items.clear()
        visits.forEach { visit ->
            val marker = Marker(mapView)
            marker.position = GeoPoint(visit.latitude, visit.longitude)
            marker.title = visit.placeName
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            historyOverlay.add(marker)
        }
        mapView.invalidate()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            // PRESERVE both CopyrightOverlay AND TilesOverlay
            // CopyOnWriteArrayList iterator does not support remove()
            val toRemove = view.overlays.filter { it !is CopyrightOverlay && it !is TilesOverlay && it !is MapEventsOverlay }
            view.overlays.removeAll(toRemove)

            currentLocation?.let {
                currentMarker.position = GeoPoint(it.latitude, it.longitude)
                view.overlays.add(currentMarker)
            }

            if (showHistory) {
                view.overlays.add(historyOverlay)
            }

            view.invalidate()
        }
    )

    LaunchedEffect(currentLocation) {
        if (zoomTarget == null && currentLocation != null) {
            mapView.controller.animateTo(
                GeoPoint(currentLocation.latitude, currentLocation.longitude)
            )
        }
    }

    LaunchedEffect(zoomTarget) {
        zoomTarget?.let {
            mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            mapView.controller.setZoom(18.0)
            onZoomConsumed()
        }
    }
}