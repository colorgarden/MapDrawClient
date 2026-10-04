import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project

// ------------------------------------------------------------------
// 属性读取（根 gradle.properties + 版本自己的 versions/<ver>/gradle.properties）
// ------------------------------------------------------------------
fun Project.propOrNull(key: String) = findProperty(key)
fun Project.propStrOrNull(key: String): String? = propOrNull(key)?.toString()
fun Project.propStr(key: String): String =
	propStrOrNull(key) ?: throw GradleException("buildSrc: 属性 $key 未配置")

val Project.modId get() = propStr("mod_id")
val Project.modName get() = propStr("mod_name")
val Project.modVersion get() = propStr("mod_version")
val Project.modMavenGroup get() = propStr("mod_maven_group")
val Project.modArchivesBaseName get() = propStr("mod_archives_base_name")

val Project.modDescription get() = propStrOrNull("mod_description")
val Project.modHomepage get() = propStrOrNull("mod_homepage")
val Project.modLicense get() = propStrOrNull("mod_license")
val Project.modSources get() = propStrOrNull("mod_sources")

/** 该版本的 Minecraft 版本号（字符串），来自 versions/<ver>/gradle.properties。 */
val Project.mcVersion get() = propStrOrNull("minecraft_version")

/** 该版本的 Minecraft 版本号（数字，如 12108 / 260200），由 preprocess 的 node 注入。 */
val Project.mcVersionInt get() = propStrOrNull("mcVersion")?.toIntOrNull() ?: 0

val Project.fabricLoaderVersion get() = propStrOrNull("loader_version")
val Project.fabricApiVersion get() = propStrOrNull("fabric_version")
val Project.mcDependency get() = propStrOrNull("minecraft_dependency")

/** 按 MC 版本决定 Java 版本：26.x = 25，1.20.5+ = 21，1.18+ = 17。 */
val Project.javaVersion
	get() = when {
		mcVersionInt >= 260000 -> JavaVersion.VERSION_25
		mcVersionInt >= 12005 -> JavaVersion.VERSION_21
		mcVersionInt >= 11800 -> JavaVersion.VERSION_17
		else -> JavaVersion.VERSION_16
	}

/** jar 版本号：`<mod_version>-mc<mc版本>`（多版本发布时一眼能看出对应哪个 MC）。 */
val Project.fullProjectVersion: String
	get() {
		val mc = mcVersion
		return if (mc == null) modVersion else "${modVersion}-mc$mc"
	}

/** fabric.mod.json / mixins.json 里的占位符。 */
val Project.placeholderProps: Map<String, Any>
	get() = mapOf(
		"version" to fullProjectVersion,
		"mod_id" to modId,
		"mod_name" to modName,
		"mod_description" to modDescription,
		"mod_homepage" to modHomepage,
		"mod_license" to modLicense,
		"mod_sources" to modSources,
		"loader_version" to fabricLoaderVersion,
		"fabric_api_version" to fabricApiVersion,
		"minecraft_dependency" to mcDependency
	).filterValues { it != null }.mapValues { it.value!! }
