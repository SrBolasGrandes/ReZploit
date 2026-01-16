package bridge;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class PipelineHandler extends ChannelInitializer<SocketChannel> {
    private final Map<String, String> targetServers;

    public PipelineHandler(Map<String, String> targetServers) {
        this.targetServers = targetServers;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(
            new LengthFieldBasedFrameDecoder(2097152, 0, 3, 0, 3),
            new BridgeHandshakeHandler(targetServers)
        );
    }

    static class BridgeHandshakeHandler extends ChannelInboundHandlerAdapter {
        private final Map<String, String> targetServers;
        private ProxyConnection proxyConnection;

        BridgeHandshakeHandler(Map<String, String> targetServers) {
            this.targetServers = targetServers;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) return;

            try {
                if (handleLoginPacket(ctx.channel(), buf)) {
                    ctx.pipeline().remove(this);
                }
            } finally {
                buf.release();
            }
        }

        private boolean handleLoginPacket(Channel clientChannel, ByteBuf packet) {
            // Handshake (0x00) ou Login Start (0x01)
            if (packet.readableBytes() < 1) return false;
            
            int packetId = packet.getUnsignedByte(packet.readerIndex());
            
            if (packetId == 0x00) {
                sendFancyServerList(clientChannel);
                return true;
            } else if (packetId == 0x01) {
                // Extrai username do Login Start
                packet.skipBytes(1); // Packet ID
                String username = readString(packet);
                
                // Conecta no primeiro servidor disponível
                String targetServer = targetServers.values().iterator().next();
                proxyConnection = new ProxyConnection(clientChannel, username, targetServer);
                proxyConnection.connect();
                
                return true;
            }
            return false;
        }

        private void sendFancyServerList(Channel channel) {
            StringBuilder serversList = new StringBuilder();
            int index = 0;
            for (Map.Entry<String, String> server : targetServers.entrySet()) {
                if (index++ > 0) serversList.append(",");
                String name = server.getKey().substring(0, 1).toUpperCase() + 
                             server.getKey().substring(1);
                serversList.append("{\"text\":\"§a◆ §e").append(name)
                          .append("§r\",\"clickEvent\":{\"action\":\"connect\",\"value\":\"")
                          .append(server.getValue())
                          .append("\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"§7Conectar: §f")
                          .append(server.getValue()).append("\"}}");
            }

            String motd = String.format(
                "{\"version\":{\"name\":\"§61.8-1.21 Bridge\",\"protocol\":999},\"players\":{\"max\":%d,\"online\":0,\"sample\":[]},\"description\":{\"text\":\"§6§lMINECRAFT BRIDGE\",\"extra\":[{\"text\":\"\\n§7§l%d SERVIDORES \\n\\n\"},%s,{\"text\":\"\\n§8§l• §7servers.txt na pasta\"}]}}",
                targetServers.size(), targetServers.size(), serversList
            );

            // Status Response Packet
            ByteBuf response = Unpooled.buffer();
            writeVarInt(response, 0); // Packet ID
            writeString(response, motd);
            
            ByteBuf lengthPrefix = Unpooled.buffer();
            lengthPrefix.writeShort(response.readableBytes());
            lengthPrefix.writeBytes(response);
            
            channel.writeAndFlush(lengthPrefix);
        }

        private String readString(ByteBuf buf) {
            int length = readVarInt(buf);
            byte[] bytes = new byte[length];
            buf.readBytes(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private int readVarInt(ByteBuf buf) {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                b = buf.readByte();
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            return value;
        }

        private void writeVarInt(ByteBuf buf, int value) {
            while ((value & ~0x7F) != 0) {
                buf.writeByte((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            buf.writeByte(value);
        }

        private void writeString(ByteBuf buf, String string) {
            byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
            writeVarInt(buf, bytes.length);
            buf.writeBytes(bytes);
        }
    }
}
