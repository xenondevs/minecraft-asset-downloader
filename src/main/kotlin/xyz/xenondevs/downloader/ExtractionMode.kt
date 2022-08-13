package xyz.xenondevs.downloader

enum class ExtractionMode(val allowsClient: Boolean, val allowsAssetIndex: Boolean) {
    
    CLIENT(true, false),
    ASSET_INDEX(false, true),
    ALL(true, true)
    
}