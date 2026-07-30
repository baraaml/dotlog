package com.example.dotlog

import android.app.Application
import com.example.dotlog.data.AppDatabase
import com.example.dotlog.data.PoiRepository
import com.example.dotlog.data.SearchRepository
import com.example.dotlog.data.VisitRepository
import org.osmdroid.config.Configuration
import java.io.File

class DotlogApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { VisitRepository(database.visitDao()) }
    val poiRepository by lazy { PoiRepository.create() }
    val searchRepository by lazy { SearchRepository.create() }

    override fun onCreate() {
        super.onCreate()

        val osmConfig = Configuration.getInstance()
        val prefs = getSharedPreferences("osmdroid", MODE_PRIVATE)

        // NUCLEAR: Wipe any stale cached config from previous installs
        prefs.edit().clear().apply()
        osmConfig.load(this, prefs)

        // Set compliant values
        val userAgent = "Dotlog Location Tracker/1.0 (https://github.com/baraaml/dotlog; contact: baraalearnsml@gmail.com)"
        osmConfig.userAgentValue = userAgent
        // Force this UA for MAPNIK tiles too (osmdroid 6.1.20 ignores setUserAgentValue
        // when TileSourcePolicy normalizes, using getNormalizedUserAgent() instead).
        // additionalHttpRequestProperties is applied AFTER the normalized UA header,
        // so it overwrites the UA sent to the tile server.
        osmConfig.additionalHttpRequestProperties["User-Agent"] = userAgent
        osmConfig.tileDownloadThreads = 2

        // Verify immediately
        android.util.Log.d("Dotlog", "Set userAgent=${osmConfig.userAgentValue}")

        osmConfig.save(this, prefs)

        val baseDir = File(cacheDir, "osmdroid")
        if (!baseDir.exists()) baseDir.mkdirs()
        osmConfig.osmdroidBasePath = baseDir

        val tileCache = File(baseDir, "tiles")
        if (!tileCache.exists()) tileCache.mkdirs()
        osmConfig.osmdroidTileCache = tileCache
    }
}