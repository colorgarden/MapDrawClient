import groovy.json.JsonSlurper

pluginManagement {
	repositories {
		mavenLocal()
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://jitpack.io") { name = "Jitpack" }
	}

	plugins {
		id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version")
		id("net.fabricmc.fabric-loom-remap") version providers.gradleProperty("loom_version")
	}

	resolutionStrategy {
		eachPlugin {
			// preprocessor 由 Fallen-Breath 维护（ReplayMod 那套的维护分支），走 JitPack
			if (requested.id.id == "com.replaymod.preprocess") {
				useModule("com.github.Fallen-Breath:preprocessor:${requested.version}")
			}
		}
	}
}

// 从 settings.json 读版本列表，每个版本一个子项目（projectDir = versions/<ver>）
val settingsFile = file("settings.json")
@Suppress("UNCHECKED_CAST")
val settings = JsonSlurper().parseText(settingsFile.readText()) as Map<String, Any>
@Suppress("UNCHECKED_CAST")
val versions = settings["versions"] as List<String>

for (version in versions) {
	include(":$version")
	project(":$version").apply {
		projectDir = file("versions/$version")
		// 26.x 的 Minecraft 不再混淆，用 fabric-loom；1.21.x 及更早需要 remap
		buildFileName = if (parseMcVersionToNumber(version) >= 260000) {
			"../../build.unobfuscated.gradle.kts"
		} else {
			"../../build.obfuscated.gradle.kts"
		}
	}
}

rootProject.name = "mapdrawclient"

fun parseMcVersionToNumber(mcVersionStr: String): Int {
	if (mcVersionStr.isBlank()) {
		return 0
	}

	return try {
		val clean = mcVersionStr.split("-")[0].replace(Regex("[^0-9.]"), "")
		val parts = clean.split(".").filter { it.isNotEmpty() }
		val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
		val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
		val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
		major * 10000 + minor * 100 + patch
	} catch (_: Exception) {
		0
	}
}
