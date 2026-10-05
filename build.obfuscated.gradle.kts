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
// 1.21.8 以下没有可用的纹理 blit / DynamicTexture 构造：把 GPU 渲染类排除出编译
if (project.mcVersionInt < 12108) {
	tasks.withType<JavaCompile>().configureEach {
		exclude("**/ui/CanvasTexture.java")
	}
}