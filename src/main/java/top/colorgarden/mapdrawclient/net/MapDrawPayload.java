package top.colorgarden.mapdrawclient.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * mapdraw:main 通道的唯一 payload 类型。
 *
 * <p>协议把「包类型」放在负载的第一个字节里 (PacketID)，而不是放在通道名里，
 * 因此整个模组只注册一个 payload，负载的字节原样透传，
 * 保证与 Paper 插件的 {@code sendPluginMessage(plugin, "mapdraw:main", bytes)} 完全互通。</p>
 */
public record MapDrawPayload(byte[] data) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MapDrawPayload> TYPE =
			new CustomPacketPayload.Type<>(MapDrawProtocol.CHANNEL);

	public static final StreamCodec<RegistryFriendlyByteBuf, MapDrawPayload> CODEC =
			StreamCodec.of(MapDrawPayload::encode, MapDrawPayload::decode);

	private static void encode(RegistryFriendlyByteBuf buf, MapDrawPayload payload) {
		buf.writeBytes(payload.data());
	}

	private static MapDrawPayload decode(RegistryFriendlyByteBuf buf) {
		byte[] raw = new byte[buf.readableBytes()];
		buf.readBytes(raw);
		return new MapDrawPayload(raw);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public int packetId() {
		return this.data.length == 0 ? -1 : (this.data[0] & 0xFF);
	}

	public int length() {
		return this.data.length;
	}
}
