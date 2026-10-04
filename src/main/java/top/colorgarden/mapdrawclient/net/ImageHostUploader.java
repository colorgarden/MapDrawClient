package top.colorgarden.mapdrawclient.net;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

import com.google.gson.JsonParser;

import top.colorgarden.mapdrawclient.MapDrawClient;

/**
 * 把本地图片传到免费图床，换回一个公网 URL，再交给插件的 {@code /mdw upload} 处理。
 *
 * <p>这样既能用插件的完整图片管线（抖动、多联画、扣费、权限），又不需要用户自己找图床。
 * 用的是三个免 key 的公共图床，全部是标准 multipart/form-data 上传：
 * <ul>
 *   <li>{@code catbox} —— catbox.moe，永久保存，返回纯文本 URL</li>
 *   <li>{@code uguu} —— uguu.se，临时保存(约3小时)，返回 JSON</li>
 *   <li>{@code 0x0} —— 0x0.st，临时保存，**必须带 User-Agent**，返回纯文本 URL</li>
 * </ul>
 * 上传在后台线程跑，不卡渲染线程。</p>
 */
public final class ImageHostUploader {
	private ImageHostUploader() {
	}

	public static final String[] HOSTS = {"catbox", "uguu", "0x0"};

	/** 回调总在后台线程触发，调用方需要自己切回主线程。 */
	public interface Callback {
		void done(String url, String error);
	}

	public static void upload(File file, String host, Callback callback) {
		Thread thread = new Thread(() -> {
			try {
				String url = doUpload(file, host);
				callback.done(url, null);
			} catch (Exception e) {
				MapDrawClient.LOGGER.warn("[MapDrawClient] 图床上传失败({}): {}", host, e.toString());
				callback.done(null, e.getMessage() == null ? e.toString() : e.getMessage());
			}
		}, "mapdrawclient-image-upload");
		thread.setDaemon(true);
		thread.start();
	}

	private static String doUpload(File file, String host) throws Exception {
		if (!file.isFile()) {
			throw new IllegalArgumentException("文件不存在: " + file.getPath());
		}

		byte[] bytes = Files.readAllBytes(file.toPath());

		if (bytes.length > 32 * 1024 * 1024) {
			throw new IllegalArgumentException("文件太大(" + (bytes.length / 1024 / 1024) + "MB)，图床限制 32MB 以内");
		}

		String boundary = "----MapDrawClient" + System.nanoTime();
		String url;
		String fieldName;
		String endpoint;
		boolean json;

		switch (host == null ? "catbox" : host.toLowerCase()) {
			case "uguu" -> {
				endpoint = "https://uguu.se/upload.php";
				fieldName = "files[]";
				json = true;
			}
			case "0x0" -> {
				endpoint = "https://0x0.st";
				fieldName = "file";
				json = false;
			}
			default -> {
				endpoint = "https://catbox.moe/user/api.php";
				fieldName = "fileToUpload";
				json = false;
			}
		}

		ByteArrayOutputStream body = new ByteArrayOutputStream(bytes.length + 512);

		if ("catbox".equalsIgnoreCase(host)) {
			append(body, "--" + boundary + "\r\n");
			append(body, "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n");
		}

		append(body, "--" + boundary + "\r\n");
		append(body, "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + file.getName() + "\"\r\n");
		append(body, "Content-Type: application/octet-stream\r\n\r\n");
		body.write(bytes);
		append(body, "\r\n--" + boundary + "--\r\n");

		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(15))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
				.timeout(Duration.ofSeconds(60))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.header("User-Agent", "MapDrawClient/1.0 (Minecraft Fabric mod)")
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
				.build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalStateException("图床返回 HTTP " + response.statusCode() + ": "
					+ response.body().replace('\n', ' ').trim());
		}

		String text = response.body().trim();

		if (json) {
			url = JsonParser.parseString(text).getAsJsonObject()
					.getAsJsonArray("files").get(0).getAsJsonObject()
					.get("url").getAsString();
		} else {
			url = text.lines().findFirst().orElse("").trim();
		}

		if (url.isEmpty() || !url.startsWith("http")) {
			throw new IllegalStateException("图床没有返回有效链接: " + text.substring(0, Math.min(120, text.length())));
		}

		MapDrawClient.LOGGER.info("[MapDrawClient] 图床上传成功({}): {}", host, url);
		return url;
	}

	private static void append(ByteArrayOutputStream out, String text) {
		byte[] data = text.getBytes(StandardCharsets.UTF_8);
		out.write(data, 0, data.length);
	}
}
