package xyz.xenondevs.downloader.extractor

import com.google.gson.JsonObject
import io.ktor.client.*
import xyz.xenondevs.downloader.util.IOUtils
import xyz.xenondevs.downloader.util.downloadBuffered
import java.io.File
import java.util.function.Predicate
import java.util.logging.Logger

private const val DOWNLOAD_URL = "https://resources.download.minecraft.net/"

internal class AssetIndexExtractor(
    outputDirectory: File,
    private val httpClient: HttpClient,
    private val filters: ArrayList<Predicate<String>>,
    private val index: JsonObject,
    private val logger: Logger?
) : Extractor {
    
    private val outputDirectory = File(outputDirectory, "assets")
    private val canonicalOutput = outputDirectory.canonicalPath
    
    override suspend fun extract() {
        val objects = index.getAsJsonObject("objects")
        val toDownload = objects.keySet().filter { name -> filters.all { it.test("assets/$name") } }
        val total = toDownload.size
        val logStep = if (total > 100) 20 else 5
        var count = 0
        toDownload.forEach { name ->
            val hash = objects.get(name).asJsonObject.get("hash").asString
            val url = "$DOWNLOAD_URL${hash.take(2)}/$hash"
            val file = File(outputDirectory, name)
            
            if (!file.canonicalPath.startsWith(canonicalOutput + File.separatorChar))
                throw IllegalStateException("File $file is outside of output directory!")
            
            if (!file.exists() || IOUtils.getFileHash(file, "SHA1") != hash)
                httpClient.downloadBuffered(url, file, null, hash, "SHA1")
            if (++count % logStep == 0 || count == total) logger?.info("Downloaded $count/$total needed assets from asset index.")
        }
    }
}