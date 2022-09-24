package xyz.xenondevs.downloader.util

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

internal suspend fun HttpClient.downloadBuffered(
    url: String,
    file: File,
    progressHandler: ((Long, Long) -> Unit)? = null,
    hash: String? = null,
    algorithm: String? = null
) {
    var contentLength: Long = -1L
    
    prepareGet(url).execute { response ->
        response.contentLength()?.let { contentLength = it }
        
        var totalRead = 0L
        withContext(Dispatchers.IO) {
            file.parentFile.mkdirs()
            val fos = file.outputStream()
            
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                while (!packet.isEmpty) {
                    val bytes = packet.readBytes()
                    totalRead += bytes.size
                    fos.write(bytes)
                    fos.flush()
                }
                progressHandler?.invoke(contentLength, totalRead)
            }
        }
    }
    if (algorithm != null && hash != null && IOUtils.getFileHash(file, algorithm) != hash)
        throw IllegalStateException("Downloaded file hash does not match expected hash ($file)")
}

internal object IOUtils {
    
    fun getFileHash(file: File, algorithm: String): String {
        val inputStream = file.inputStream()
        val md = MessageDigest.getInstance(algorithm)
        var len: Int
        val buffer = ByteArray(4096)
        while (run { len = inputStream.read(buffer); len } != -1) {
            md.update(buffer, 0, len)
        }
        inputStream.close()
        val binaryHash = md.digest()
        return buildString(capacity = binaryHash.size * 2) {
            binaryHash.forEach { byte -> append("%02x".format(byte)) }
        }
    }
    
}