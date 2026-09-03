rootProject.name = "SahincsCloudstreamPlugins"

// Kök dizindeki her eklenti klasörü (build.gradle.kts içerenler) otomatik modül olur.
// Şablon/yardımcı klasörleri "disabled" listesine ekleyerek hariç tutabilirsin.
val disabled = listOf<String>()

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
