import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources

/**
 * 每个版本子项目共用的 java / 资源 / jar 配置。
 *
 * <p>Java 版本按 MC 版本自动选（26.x=25、1.20.5+=21、1.18+=17），
 * 资源里的 `${...}` 占位符由 [placeholderProps] 展开。</p>
 */
@Suppress("unused")
abstract class ModPlugin : Plugin<Project> {
	override fun apply(project: Project) = with(project) {
		pluginManager.apply("java")

		extensions.configure<JavaPluginExtension> {
			sourceCompatibility = javaVersion
			targetCompatibility = javaVersion
			withSourcesJar()
		}

		tasks.withType<JavaCompile>().configureEach {
			options.encoding = "UTF-8"
		}

		tasks.named<ProcessResources>("processResources") {
			inputs.properties(placeholderProps)
			filesMatching(listOf("fabric.mod.json", "*.mixins.json")) {
				expand(placeholderProps)
			}
		}

		tasks.withType<Jar>().configureEach {
			from(rootProject.file("LICENSE")) {
				rename { originalName -> "${originalName}_$modArchivesBaseName" }
			}
			duplicatesStrategy = DuplicatesStrategy.EXCLUDE
			manifest {
				attributes(
					mapOf(
						"Implementation-Title" to project.name,
						"Implementation-Version" to project.version
					)
				)
			}
		}
	}
}
