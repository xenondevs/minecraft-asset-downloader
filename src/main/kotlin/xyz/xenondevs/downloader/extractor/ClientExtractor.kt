package xyz.xenondevs.downloader.extractor

import com.google.gson.JsonObject
import io.ktor.client.*
import io.ktor.utils.io.core.*
import xyz.xenondevs.downloader.AssetFilter
import xyz.xenondevs.downloader.util.IOUtils
import xyz.xenondevs.downloader.util.downloadBuffered
import java.io.File
import java.util.zip.ZipInputStream

internal class ClientExtractor(private val outputDirectory: File,
                      private val httpClient: HttpClient,
                      private val filters: ArrayList<AssetFilter>,
                      clientManifest: JsonObject) : Extractor {
    
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
        
        httpClient.downloadBuffered(downloadUrl, clientFile, hash, "SHA1")
    }
    
    private fun extractAssets() {
        ZipInputStream(clientFile.inputStream()).use { zis ->
            generateSequence(zis::getNextEntry).forEach { entry ->
                if (!entry.name.startsWith("assets/"))
                    return@forEach
                
                if (filters.any { !it(entry.name) })
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