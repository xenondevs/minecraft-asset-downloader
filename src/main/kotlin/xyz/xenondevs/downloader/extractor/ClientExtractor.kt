package xyz.xenondevs.downloader.extractor

import com.google.gson.JsonObject
import io.ktor.client.*
import io.ktor.utils.io.core.*
import xyz.xenondevs.downloader.util.IOUtils
import xyz.xenondevs.downloader.util.downloadBuffered
import java.io.File
import java.util.*
import java.util.function.Predicate
import java.util.logging.Logger
import java.util.zip.ZipInputStream

internal class ClientExtractor(
    private val outputDirectory: File,
    private val httpClient: HttpClient,
    private val filters: ArrayList<Predicate<String>>,
    clientManifest: JsonObject,
    private val logger: Logger?
) : Extractor {
    
    private val clientFile = File(outputDirectory, "temp-client")
    private val canonicalOutput = outputDirectory.canonicalPath
    
    private val size = clientManifest.get("size").asLong
    private val hash = clientManifest.get("sha1").asString
    private val downloadUrl = clientManifest.get("url").asString
    
    override suspend fun extract() {
        download()
        extractAssets()
    }
    
    private suspend fun download() {
        if (clientFile.exists()) {
            if (IOUtils.getFileHash(clientFile, "SHA1") == hash)
                return
        }
        
        var lastPrint = 0.0
        httpClient.downloadBuffered(downloadUrl, clientFile, hash = hash, algorithm = "SHA1",
            progressHandler = if (logger != null) ({ total, current ->
                val percentage = (current.toDouble() / total.toDouble()) * 100
                if (percentage - lastPrint >= 5 || percentage == 100.0) {
                    logger.info("Downloading client: ${"%.2f".format(Locale.US, percentage)}%")
                    lastPrint = percentage
                }
            }) else null
        )
    }
    
    private fun extractAssets() {
        logger?.info("Extracting client assets...")
        ZipInputStream(clientFile.inputStream()).use { zis ->
            generateSequence(zis::getNextEntry).forEach { entry ->
                if (!entry.name.startsWith("assets/"))
                    return@forEach
                
                if (!filters.all { it.test(entry.name) })
                    return@forEach
                
                val file = File(outputDirectory, entry.name)
                if (!file.canonicalPath.startsWith(canonicalOutput + File.separatorChar))
                    throw IllegalStateException("File $file is outside of output directory!")
                
                if (!entry.isDirectory) {
                    file.parentFile.mkdirs()
                    file.outputStream().use(zis::copyTo)
                } else file.mkdirs()
                
                zis.closeEntry()
            }
        }
    }
    
}