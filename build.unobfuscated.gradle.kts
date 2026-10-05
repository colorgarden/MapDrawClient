@file:Suppress("UnstableApiUsage")

plugins {
	id("mod-plugin")
	id("maven-publish")
	id("net.fabricmc.fabric-loom")
	id("com.replaymod.preprocess")
}

version = fullProjectVersion
group = modMavenGroup
base {
	archivesName.set(modArchivesBaseName)
}

repositories {
	mavenLocal()
	maven("https://maven.fabricmc.net/") { name = "FabricMC" }
}

dependencies {
	// 26.x 的 Minecraft 官方不再混淆，直接拿 jar，不需要 mappings
	minecraft("com.mojang:minecraft:$mcVersion")
	implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
	implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
}

loom {
	runs {
		named("client") {
			// 联调：runClient 直接连本机 Paper 测试服（不需要时删掉这一行）
			programArguments.set(listOf("--quickPlayMultiplayer", "127.0.0.1:25565"))
			// 开发运行目录仍然用根目录的 run/（沿用已有的配置与日志）
			runDirectory.dir("../../run")
		}
	}
}

tasks {
	// 把每个版本的 jar 汇总到根 build/libs/<版本号>/ 下，方便发布
	register<Copy>("buildAndCollect") {
		description = "Build and collect this version's jar into the root build directory"
		group = "build"
		from(jar.map { it.archiveFile })
		into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod_version")}"))
		dependsOn("build")
	}
}
// 数位板压感：Wintab 走 JNA；include 会把 jna 嵌进 mod jar，玩家无需另装
dependencies {
	include(implementation("net.java.dev.jna:jna:5.14.0")!!)

}
repositories {
	// Skija 的官方仓库（Musangclient 也是用这个，原生库比 Maven Central 的更靠谱）
	mavenCentral()
}
// NanoVG（LWJGL 官方 2D 矢量渲染，GPU 加速）；版本与 MC 的 LWJGL 3.4.1 对齐
val lwjglNvg = "3.4.1"

dependencies {
	implementation("org.lwjgl:lwjgl-nanovg:$lwjglNvg")
	runtimeOnly("org.lwjgl:lwjgl-nanovg:$lwjglNvg:natives-windows")
	runtimeOnly("org.lwjgl:lwjgl-nanovg:$lwjglNvg:natives-linux")
	runtimeOnly("org.lwjgl:lwjgl-nanovg:$lwjglNvg:natives-macos")
	runtimeOnly("org.lwjgl:lwjgl-nanovg:$lwjglNvg:natives-macos-arm64")
	include("org.lwjgl:lwjgl-nanovg:$lwjglNvg")
	include("org.lwjgl:lwjgl-nanovg:$lwjglNvg:natives-windows")
	include("org.lwjgl:lwjgl-nanovg:$lwjglNvg:natives-linux")
	include("org.lwjgl:lwjgl-nanovg:$lwjglNvg:natives-macos")
	include("org.lwjgl:lwjgl-nanovg:$lwjglNvg:natives-macos-arm64")
}
