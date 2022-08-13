package xyz.xenondevs.downloader.extractor

internal sealed interface Extractor {
    
    suspend fun extract()
    
}