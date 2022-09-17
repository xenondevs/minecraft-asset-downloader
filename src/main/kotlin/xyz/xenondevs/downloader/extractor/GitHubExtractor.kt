package xyz.xenondevs.downloader.extractor

import io.ktor.client.*
import io.ktor.utils.io.core.*
import xyz.xenondevs.downloader.util.downloadBuffered
import java.io.File
import java.util.*
import java.util.function.Predicate
import java.util.logging.Logger
import java.util.zip.ZipInputStream

private const val GITHUB_FORMAT = "https://github.com/InventivetalentDev/minecraft-assets/archive/refs/heads/%s.zip"

class GitHubExtractor(
    private val outputDirectory: File,
    private val httpClient: HttpClient,
    private val filters: ArrayList<Predicate<String>>,
    private val version: String,
    private val logger: Logger?
) : Extractor {
    
    private val branchFile = File(outputDirectory, "temp-branch")
    private val downloadUrl = GITHUB_FORMAT.format(version)
    private val canonicalOutput = outputDirectory.canonicalPath
    
    override suspend fun extract() {
        
        var lastPrint = 0.0
        httpClient.downloadBuffered(downloadUrl, branchFile,
            progressHandler = if (logger != null) ({ _, current -> // GitHub doesn't provide a content length. So we print every 10 mb
                val currentMb = current.toDouble() / 1_000_000
                if (currentMb - lastPrint >= 10) {
                    logger.info("Downloading Minecraft assets repository: ${"%.2f".format(Locale.US, currentMb)} MB")
                    lastPrint = currentMb
                }
            }) else null)
        extractAssets()
    }
    
    private fun extractAssets() {
        logger?.info("Extracting Minecraft assets repository...")
        ZipInputStream(branchFile.inputStream()).use { zis ->
            generateSequence(zis::getNextEntry).forEach { entry ->
                val name = entry.name.drop("minecraft-assets-$version/".length)
                val actualName = name.substringAfterLast('/')
                if (name.isEmpty() || actualName.startsWith('_') || actualName == ".mcassetsroot" || !name.startsWith("assets/"))
                    return@forEach
                
                if (!filters.all { it.test(name) })
                    return@forEach
                
                val file = File(outputDirectory, name)
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