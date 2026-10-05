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
// 画布贴图 blit 的三个版本族：同一类名，按版本只保留一个文件
tasks.withType<JavaCompile>().configureEach {
	val v = project.mcVersionInt

	if (v >= 12108) {
		exclude("**/ui/blit/legacy/**", "**/ui/blit/rendertype/**")
	} else if (v >= 12102) {
		exclude("**/ui/blit/legacy/**", "**/ui/blit/pipeline/**")
	} else {
		exclude("**/ui/blit/rendertype/**", "**/ui/blit/pipeline/**")
	}
}
// 1.21.8 以下没有「目标尺寸 + UV」的缩放 blit：GPU 渲染类不参与编译
if (project.mcVersionInt < 12108) {
	tasks.withType<JavaCompile>().configureEach {
		exclude("**/ui/CanvasTexture.java", "**/ui/blit/**")
	}
}
// 数位板压感：Wintab 走 JNA；include 会把 jna 嵌进 mod jar，玩家无需另装
dependencies {
	include(implementation("net.java.dev.jna:jna:5.14.0")!!)
}