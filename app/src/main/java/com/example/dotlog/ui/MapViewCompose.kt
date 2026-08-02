package com.example.dotlog.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
    selectedVisit: Visit?,
    zoomTarget: Location?,
    onZoomConsumed: () -> Unit,
    onVisitMarkerClick: (Visit) -> Unit = {},
    onMapClick: () -> Unit = {},
    onMapLongPress: (Double, Double) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val primaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.toArgb()
    val secondaryColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary.toArgb()
    val surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.toArgb()

    val markerIcon = remember(secondaryColor) { createCircularMarker(context, secondaryColor, 12) }
    val selectedIcon = remember(primaryColor, surfaceColor) { 
        createCircularMarker(context, primaryColor, 16, strokeColor = surfaceColor) 
    }
    val currentLocIcon = remember(primaryColor) { createCircularMarker(context, primaryColor, 14) }

    val mapView = remember {
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
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    remember {
        mapView.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                currentOnMapClick()
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean {
                currentOnLongPress(p.latitude, p.longitude)
                return true
            }
        }))
    }

    val currentMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = currentLocIcon
            title = "You are here"
            infoWindow = null // Disable default grey rectangle
        }
    }

    val historyOverlay = remember { FolderOverlay() }

    LaunchedEffect(visits, selectedVisit) {
        historyOverlay.items.clear()
        visits.forEach { visit ->
            val marker = Marker(mapView)
            marker.position = GeoPoint(visit.latitude, visit.longitude)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            marker.infoWindow = null // Disable default grey rectangle
            
            val isSelected = visit.id == selectedVisit?.id
            marker.icon = if (isSelected) selectedIcon else markerIcon
            
            marker.setOnMarkerClickListener { m, _ ->
                onVisitMarkerClick(visit)
                mapView.controller.animateTo(m.position)
                true // Consume click to prevent showing InfoWindow
            }
            
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

    var initialZoomDone by remember { mutableStateOf(false) }

    LaunchedEffect(currentLocation) {
        if (!initialZoomDone && currentLocation != null) {
            mapView.controller.animateTo(
                GeoPoint(currentLocation.latitude, currentLocation.longitude)
            )
            initialZoomDone = true
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

private fun createCircularMarker(
    context: android.content.Context,
    color: Int,
    sizeDp: Int,
    strokeColor: Int? = null
): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    if (strokeColor != null) {
        paint.color = strokeColor
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (2 * density), paint)
    } else {
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    }
    
    return BitmapDrawable(context.resources, bitmap)
}
