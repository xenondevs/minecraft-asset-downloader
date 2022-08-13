package xyz.xenondevs.downloader.extractor

import com.google.gson.JsonObject
import io.ktor.client.*
import xyz.xenondevs.downloader.util.IOUtils
import xyz.xenondevs.downloader.util.downloadBuffered
import java.io.File
import java.util.function.Predicate

private const val DOWNLOAD_URL = "https://resources.download.minecraft.net/"

internal class AssetIndexExtractor(outputDirectory: File,
                                   private val httpClient: HttpClient,
                                   private val filters: ArrayList<Predicate<String>>,
                                   private val index: JsonObject) : Extractor {
    
    private val outputDirectory = File(outputDirectory, "assets")
    private val canonicalOutput = outputDirectory.canonicalPath
    
    override suspend fun extract() {
        val objects = index.getAsJsonObject("objects")
        objects.keySet().forEach { name ->
            if (!filters.all { it.test("assets/$name") })
                return@forEach
            
            val hash = objects.get(name).asJsonObject.get("hash").asString
            val url = "$DOWNLOAD_URL${hash.take(2)}/$hash"
            val file = File(outputDirectory, name)
            
            if (!file.canonicalPath.startsWith(canonicalOutput + File.separatorChar))
                throw IllegalStateException("File $file is outside of output directory!")
            
            if (!file.exists() || IOUtils.getFileHash(file, "SHA1") != hash)
                httpClient.downloadBuffered(url, file, hash, "SHA1")
        }
    }
}