@file:Suppress("UnstableApiUsage")

plugins {
	id("mod-plugin")
	id("maven-publish")
	id("net.fabricmc.fabric-loom-remap")
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
	// 1.21.x 及更早：需要官方 mappings + remap
	minecraft("com.mojang:minecraft:$mcVersion")
	mappings(loom.officialMojangMappings())
	modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
	modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
}

loom {
	runs {
		named("client") {
			programArguments.set(listOf("--quickPlayMultiplayer", "127.0.0.1:25565"))
			runDirectory.dir("../../run")
		}
	}
}

tasks {
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

	// 画布渲染用 Skija（Skia 的 Java 绑定）；原生库在各平台包里
	include(implementation("io.github.humbleui:skija-shared:0.143.17")!!)
	include(runtimeOnly("io.github.humbleui:skija-windows-x64:0.143.17")!!)
	include(runtimeOnly("io.github.humbleui:skija-windows-arm64:0.143.17")!!)
	include(runtimeOnly("io.github.humbleui:skija-linux-x64:0.143.17")!!)
	include(runtimeOnly("io.github.humbleui:skija-linux-arm64:0.143.17")!!)
	include(runtimeOnly("io.github.humbleui:skija-macos-x64:0.143.17")!!)
	include(runtimeOnly("io.github.humbleui:skija-macos-arm64:0.143.17")!!)
}
// Skija 在 Maven Central（Loom 默认仓库里没有）
repositories {
	mavenCentral()
}
// 1.21.8 以下没有「目标尺寸 + UV」的 blit 重载：Skija 渲染器不参与编译
if (project.mcVersionInt < 12108) {
	tasks.withType<JavaCompile>().configureEach {
		exclude("**/ui/SkiaCanvasRenderer.java")
	}
}