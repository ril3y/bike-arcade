plugins {
    id("com.android.application") version "8.3.0" apply false
    id("com.android.library") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}

/**
 * Downloads velofit.jar from the latest velo-platform GitHub release.
 * Run: ./gradlew updateVelofitSdk
 * CI runs this automatically before assembleDebug/assembleRelease.
 */
tasks.register("updateVelofitSdk") {
    val outputFile = file("metrics/libs/velofit.jar")
    val repo = "ril3y/velo-platform"

    doLast {
        // Fetch latest release asset URL via GitHub API
        val apiUrl = java.net.URL("https://api.github.com/repos/$repo/releases/latest")
        val conn = apiUrl.openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "BikeArcade-Build")
        val token = System.getenv("GITHUB_TOKEN")
        if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "token $token")

        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        // Find velofit.jar download URL
        val urlPattern = Regex(""""browser_download_url"\s*:\s*"([^"]*velofit\.jar)"""")
        val match = urlPattern.find(json)
        if (match == null) {
            println("WARNING: velofit.jar not found in latest release — keeping existing copy")
            return@doLast
        }
        val downloadUrl = match.groupValues[1]
        println("Downloading velofit.jar from $downloadUrl")

        // Download
        val dlConn = java.net.URL(downloadUrl).openConnection() as java.net.HttpURLConnection
        dlConn.instanceFollowRedirects = true
        if (!token.isNullOrBlank()) dlConn.setRequestProperty("Authorization", "token $token")
        outputFile.parentFile.mkdirs()
        dlConn.inputStream.use { input -> outputFile.outputStream().use { output -> input.copyTo(output) } }
        dlConn.disconnect()
        println("Updated: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
    }
}

// Auto-run before builds if velofit.jar is missing
afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("compile") }.configureEach {
        if (!file("metrics/libs/velofit.jar").exists()) {
            dependsOn("updateVelofitSdk")
        }
    }
}
