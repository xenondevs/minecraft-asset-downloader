package xyz.xenondevs.downloader

enum class ExtractionMode(val allowsGithub: Boolean, val allowsClient: Boolean, val allowsAssetIndex: Boolean) {
    
    GITHUB(true, false, false),
    MOJANG_API_CLIENT(false, true, false),
    MOJANG_API_ASSET_INDEX(false, false, true),
    MOJANG_ALL(false, true, true)
    
}