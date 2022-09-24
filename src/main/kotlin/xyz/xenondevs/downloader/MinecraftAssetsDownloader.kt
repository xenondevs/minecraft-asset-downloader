package xyz.xenondevs.downloader

import com.google.gson.JsonObject
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.runBlocking
import xyz.xenondevs.downloader.extractor.AssetIndexExtractor
import xyz.xenondevs.downloader.extractor.ClientExtractor
import xyz.xenondevs.downloader.extractor.GitHubExtractor
import java.io.File
import java.util.function.Predicate
import java.util.logging.Logger

private const val VERSION_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest.json"

class MinecraftAssetsDownloader(
    private val version: String = "latest",
    private val outputDirectory: File,
    private val mode: ExtractionMode,
    private val logger: Logger? = null
) {
    
    val filters = ArrayList<Predicate<String>>()
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { gson() }
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10 * 1000
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
        }
    }
    private val tempClientFile = File(outputDirectory, "temp-client")
    
    private lateinit var actualVersion: String
    private lateinit var versionManifest: JsonObject
    
    init {
        runBlocking {
            logger?.info("Retrieving version manifest...")
            retrieveVersionManifest()
        }
    }
    
    private suspend fun retrieveVersionManifest() {
        val manifest by lazy { runBlocking { httpClient.get(VERSION_MANIFEST).body<JsonObject>() } }
        actualVersion = when (version) {
            "latest" -> manifest.getAsJsonObject("latest").get("release").asString
            "latest-snapshot" -> manifest.getAsJsonObject("latest").get("snapshot").asString
            else -> version
        }
        if (mode != ExtractionMode.GITHUB) {
            val versions = manifest.getAsJsonArray("versions")
            val versionManifestUrl = versions.first { (it as JsonObject).get("id").asString == actualVersion }.asJsonObject.get("url").asString
            versionManifest = httpClient.get(versionManifestUrl).body<JsonObject>().apply {
                remove("arguments")
                remove("libraries")
                remove("logging")
            }
        }
    }
    
    suspend fun downloadAssets() {
        logger?.info("Downloading minecraft assets for version $actualVersion...")
        if (mode.allowsGithub) {
            GitHubExtractor(
                outputDirectory,
                httpClient,
                filters,
                actualVersion,
                logger
            ).extract()
        }
        if (mode.allowsClient) {
            ClientExtractor(
                outputDirectory,
                httpClient,
                filters,
                versionManifest.getAsJsonObject("downloads").getAsJsonObject("client"),
                logger
            ).extract()
        }
        if (mode.allowsAssetIndex) {
            AssetIndexExtractor(
                outputDirectory,
                httpClient,
                filters,
                httpClient.get(versionManifest.getAsJsonObject("assetIndex").get("url").asString).body(),
                logger
            ).extract()
        }
        logger?.info("Finished downloading Minecraft assets!")
    }
    
    
}