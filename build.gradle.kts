plugins {
	id("maven-publish")
	id("net.fabricmc.fabric-loom") version "1.18-SNAPSHOT" apply false
	id("net.fabricmc.fabric-loom-remap") version "1.18-SNAPSHOT" apply false

	// 多版本预处理：https://github.com/Fallen-Breath/preprocessor
	id("com.replaymod.preprocess") version "c5abb4fb12"
}

/**
 * 版本图谱：每个节点 = 一个 Minecraft 版本（= 一个子项目）。
 *
 * <p>共享源码在根目录的 {@code src/main/java}，按<b>主版本</b>（{@code versions/mainProject}，现在是 26.2）
 * 的 API 写；往旧版本翻译时由 {@code link(...)} 挂的映射文件改名，
 * 结构性差异用源码里的 {@code //#if MC >= 12104} 预处理指令分支。</p>
 *
 * <p>映射文件命名：{@code versions/mapping-<旧>-<新>.txt}，内容方向是「新 → 旧」
 * （把主版本的写法翻译成旧版本的写法）。</p>
 */
preprocess {
	strictExtraMappings.set(false)

	val mc260200 = createNode("26.2", 26_02_00, "mojang")
	val mc260102 = createNode("26.1.2", 26_01_02, "mojang")
	val mc12111 = createNode("1.21.11", 1_21_11, "mojang")
	val mc12110 = createNode("1.21.10", 1_21_10, "mojang")
	val mc12108 = createNode("1.21.8", 1_21_08, "mojang")
	val mc12105 = createNode("1.21.5", 1_21_05, "mojang")
	val mc12104 = createNode("1.21.4", 1_21_04, "mojang")
	val mc12103 = createNode("1.21.3", 1_21_03, "mojang")
	val mc12101 = createNode("1.21.1", 1_21_01, "mojang")
	val mc12006 = createNode("1.20.6", 1_20_06, "mojang")

	// 相邻版本 link：从旧到新，映射文件命名 mapping-<旧>-<新>.txt
	mc260102.link(mc260200, file("versions/mapping-26.1.2-26.2.txt"))
	mc12111.link(mc260102, file("versions/mapping-1.21.11-26.1.2.txt"))
	mc12110.link(mc12111, null)
	mc12108.link(mc12110, null)
	mc12105.link(mc12108, null)
	mc12104.link(mc12105, null)
	mc12103.link(mc12104, null)
	mc12101.link(mc12103, null)
	mc12006.link(mc12101, null)

	// 把 mcVersion（数字）传给子项目：buildSrc 用它决定 Java 版本等
	for (node in getNodes()) {
		findProject(node.project)
			?.ext
			?.set("mcVersion", node.mcVersion)
	}
}
