package xyz.xenondevs.downloader

import com.google.gson.JsonObject
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.runBlocking
import xyz.xenondevs.downloader.extractor.AssetIndexExtractor
import xyz.xenondevs.downloader.extractor.ClientExtractor
import java.io.File
import java.util.function.Predicate

private const val VERSION_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest.json"

class MinecraftAssetsDownloader(private val version: String = "latest", private val outputDirectory: File, private val mode: ExtractionMode) {
    
    val filters = ArrayList<Predicate<String>>()
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { gson() }
        expectSuccess = false
        engine {
            requestTimeout = 10000
            endpoint.connectTimeout = 1000
        }
    }
    private val tempClientFile = File(outputDirectory, "temp-client")
    
    private lateinit var actualVersion: String
    private lateinit var versionManifest: JsonObject
    
    init {
        runBlocking {
            retrieveVersionManifest()
        }
    }
    
    private suspend fun retrieveVersionManifest() {
        val manifest = httpClient.get(VERSION_MANIFEST).body<JsonObject>()
        actualVersion = when (version) {
            "latest" -> manifest.getAsJsonObject("latest").get("release").asString
            "latest-snapshot" -> manifest.getAsJsonObject("latest").get("snapshot").asString
            else -> version
        }
        val versions = manifest.getAsJsonArray("versions")
        val versionManifestUrl = versions.first { (it as JsonObject).get("id").asString == actualVersion }.asJsonObject.get("url").asString
        versionManifest = httpClient.get(versionManifestUrl).body<JsonObject>().apply {
            remove("arguments")
            remove("libraries")
            remove("logging")
        }
    }
    
    suspend fun downloadAssets() {
        if (mode.allowsClient) {
            ClientExtractor(
                outputDirectory,
                httpClient,
                filters,
                versionManifest.getAsJsonObject("downloads").getAsJsonObject("client")
            ).extract()
        }
        if (mode.allowsAssetIndex) {
            AssetIndexExtractor(
                outputDirectory,
                httpClient,
                filters,
                httpClient.get(versionManifest.getAsJsonObject("assetIndex").get("url").asString).body()
            ).extract()
        }
    }
    
    
}