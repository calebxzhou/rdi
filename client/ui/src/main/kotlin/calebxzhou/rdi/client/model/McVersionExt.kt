package calebxzhou.rdi.client.model

import calebxzau.rdi.client.ui.loadResourceStream
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.client.service.GameService

val McVersion.metadata
    get() = loadResourceStream("mcmeta/$mcVer.json").bufferedReader().readText()
        .let { serdesJson.decodeFromString<MojangVersionManifest>(it) }
val McVersion.baseDir get() = GameService.versionListDir.resolve(mcVer)
val McVersion.nativesDir get() = baseDir.resolve("natives")
val McVersion.firstLoader get() = loaderVersions.keys.first()
val McVersion.firstLoaderVersion get() = loaderVersions.values.first()
val McVersion.firstLoaderDir get() = loaderVersions.values.first().let { GameService.versionListDir.resolve(it.dirName) }
val McVersion.manifest: MojangVersionManifest get() = metadata
val McVersion.loaderManifest: MojangVersionManifest
    get() = GameService.versionListDir.resolve(firstLoaderVersion.dirName).resolve(firstLoaderVersion.dirName + ".json")
        .let { serdesJson.decodeFromString(it.readText()) }