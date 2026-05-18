package net.kendo.nightfall.network;

import net.kendo.nightfall.NightfallSkin;
import net.kendo.nightfall.ServerSkinStorage;
import net.kendo.nightfall.SkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SkinNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(NightfallSkin.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static final int CHUNK_SIZE = 20000;
    private static int maxMultiplayerSize = 512;

    // Packet IDs
    private static final int UPLOAD_START = packetId++;
    private static final int UPLOAD_CHUNK = packetId++;
    private static final int UPLOAD_END = packetId++;
    private static final int REQUEST_SKIN = packetId++;
    private static final int REQUEST_SKIN_LIST = packetId++;
    private static final int DOWNLOAD_START = packetId++;
    private static final int DOWNLOAD_CHUNK = packetId++;
    private static final int DOWNLOAD_END = packetId++;
    private static final int SKIN_LIST_RESPONSE = packetId++;
    private static final int RESET_SKIN = packetId++;
    private static final int PLAYER_SKIN_RESET = packetId++;

    private static final Map<UUID, ChunkedSkinData> receivingUploads = new ConcurrentHashMap<>();
    private static final Map<UUID, ChunkedSkinData> receivingDownloads = new ConcurrentHashMap<>();

    private static class ChunkedSkinData {
        final int totalChunks;
        final Map<Integer, byte[]> chunks;
        final boolean isSlim;

        ChunkedSkinData(int totalChunks, boolean isSlim) {
            this.totalChunks = totalChunks;
            this.chunks = new HashMap<>();
            this.isSlim = isSlim;
        }

        void addChunk(int index, byte[] data) { chunks.put(index, data); }
        boolean isComplete() { return chunks.size() == totalChunks; }

        byte[] assemble() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunk = chunks.get(i);
                if (chunk == null) throw new RuntimeException("Missing chunk " + i);
                baos.write(chunk, 0, chunk.length);
            }
            return baos.toByteArray();
        }
    }

    public static void registerChannels() {
        // C->S: Upload skin start
        CHANNEL.registerMessage(UPLOAD_START, UploadStartPacket.class,
                UploadStartPacket::encode, UploadStartPacket::decode,
                SkinNetworkHandler::handleUploadStart, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // C->S: Upload skin chunk
        CHANNEL.registerMessage(UPLOAD_CHUNK, UploadChunkPacket.class,
                UploadChunkPacket::encode, UploadChunkPacket::decode,
                SkinNetworkHandler::handleUploadChunk, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // C->S: Upload skin end
        CHANNEL.registerMessage(UPLOAD_END, UploadEndPacket.class,
                UploadEndPacket::encode, UploadEndPacket::decode,
                SkinNetworkHandler::handleUploadEnd, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // C->S: Request skin list
        CHANNEL.registerMessage(REQUEST_SKIN_LIST, RequestSkinListPacket.class,
                RequestSkinListPacket::encode, RequestSkinListPacket::decode,
                SkinNetworkHandler::handleRequestSkinList, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // C->S: Request specific player skin
        CHANNEL.registerMessage(REQUEST_SKIN, RequestSkinPacket.class,
                RequestSkinPacket::encode, RequestSkinPacket::decode,
                SkinNetworkHandler::handleRequestSkin, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // C->S: Reset skin
        CHANNEL.registerMessage(RESET_SKIN, ResetSkinPacket.class,
                ResetSkinPacket::encode, ResetSkinPacket::decode,
                SkinNetworkHandler::handleResetSkin, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // S->C: Skin list response
        CHANNEL.registerMessage(SKIN_LIST_RESPONSE, SkinListResponsePacket.class,
                SkinListResponsePacket::encode, SkinListResponsePacket::decode,
                SkinNetworkHandler::handleSkinListResponse, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // S->C: Download skin start
        CHANNEL.registerMessage(DOWNLOAD_START, DownloadStartPacket.class,
                DownloadStartPacket::encode, DownloadStartPacket::decode,
                SkinNetworkHandler::handleDownloadStart, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // S->C: Download skin chunk
        CHANNEL.registerMessage(DOWNLOAD_CHUNK, DownloadChunkPacket.class,
                DownloadChunkPacket::encode, DownloadChunkPacket::decode,
                SkinNetworkHandler::handleDownloadChunk, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // S->C: Download skin end
        CHANNEL.registerMessage(DOWNLOAD_END, DownloadEndPacket.class,
                DownloadEndPacket::encode, DownloadEndPacket::decode,
                SkinNetworkHandler::handleDownloadEnd, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // S->C: Player skin reset notification
        CHANNEL.registerMessage(PLAYER_SKIN_RESET, PlayerSkinResetPacket.class,
                PlayerSkinResetPacket::encode, PlayerSkinResetPacket::decode,
                SkinNetworkHandler::handlePlayerSkinReset, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void registerClientHandlers() {
        // Client handlers are registered via CHANNEL above - nothing extra needed
    }

    // ==================== SERVER HANDLERS ====================

    private static void handleUploadStart(UploadStartPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            receivingUploads.put(player.getUUID(), new ChunkedSkinData(pkt.totalChunks, pkt.isSlim));
            NightfallSkin.LOGGER.info("Receiving skin upload from {} ({} chunks)", player.getName().getString(), pkt.totalChunks);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleUploadChunk(UploadChunkPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ChunkedSkinData data = receivingUploads.get(player.getUUID());
            if (data != null) data.addChunk(pkt.chunkIndex, pkt.data);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleUploadEnd(UploadEndPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            UUID senderUuid = player.getUUID();
            ChunkedSkinData data = receivingUploads.remove(senderUuid);
            if (data != null && data.isComplete()) {
                try {
                    byte[] fullData = data.assemble();
                    boolean saved = ServerSkinStorage.saveSkin(senderUuid, fullData, data.isSlim);
                    if (saved) {
                        // Notify all other online players
                        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                                .getPlayerList().getPlayers().forEach(otherPlayer -> {
                            if (!otherPlayer.getUUID().equals(senderUuid)) {
                                sendSkinToPlayer(otherPlayer, senderUuid, fullData, data.isSlim);
                            }
                        });
                    }
                } catch (Exception e) {
                    NightfallSkin.LOGGER.error("Failed to process uploaded skin", e);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleRequestSkinList(RequestSkinListPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            List<UUID> uuids = new ArrayList<>();
            net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                    .getPlayerList().getPlayers().forEach(onlinePlayer -> {
                if (ServerSkinStorage.hasSkin(onlinePlayer.getUUID())) {
                    uuids.add(onlinePlayer.getUUID());
                }
            });

            CHANNEL.sendTo(new SkinListResponsePacket(uuids),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleRequestSkin(RequestSkinPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ServerSkinStorage.SkinData skinData = ServerSkinStorage.loadSkin(pkt.targetUuid);
            if (skinData != null) {
                sendSkinToPlayer(player, pkt.targetUuid, skinData.imageData, skinData.isSlim);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleResetSkin(ResetSkinPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            UUID senderUuid = player.getUUID();
            boolean deleted = ServerSkinStorage.deleteSkin(senderUuid);
            if (deleted) {
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                        .getPlayerList().getPlayers().forEach(otherPlayer -> {
                    if (!otherPlayer.getUUID().equals(senderUuid)) {
                        CHANNEL.sendTo(new PlayerSkinResetPacket(senderUuid),
                                otherPlayer.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // ==================== CLIENT HANDLERS ====================

    @OnlyIn(Dist.CLIENT)
    private static void handleSkinListResponse(SkinListResponsePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft client = Minecraft.getInstance();
            pkt.uuids.forEach(uuid -> {
                if (!uuid.equals(client.player.getUUID())) {
                    requestSkinFromServer(uuid);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleDownloadStart(DownloadStartPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            receivingDownloads.put(pkt.playerUuid, new ChunkedSkinData(pkt.totalChunks, pkt.isSlim));
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleDownloadChunk(DownloadChunkPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ChunkedSkinData data = receivingDownloads.get(pkt.playerUuid);
            if (data != null) data.addChunk(pkt.chunkIndex, pkt.data);
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleDownloadEnd(DownloadEndPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ChunkedSkinData data = receivingDownloads.remove(pkt.playerUuid);
            if (data != null && data.isComplete()) {
                try {
                    byte[] fullData = data.assemble();
                    SkinManager.applyRemoteSkin(Minecraft.getInstance(), pkt.playerUuid, fullData, data.isSlim);
                } catch (Exception e) {
                    NightfallSkin.LOGGER.error("Failed to apply downloaded skin", e);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handlePlayerSkinReset(PlayerSkinResetPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> SkinManager.removeRemoteSkin(pkt.playerUuid));
        ctx.get().setPacketHandled(true);
    }

    // ==================== HELPER METHODS ====================

    private static void sendSkinToPlayer(ServerPlayer player, UUID skinOwnerUuid, byte[] data, boolean isSlim) {
        int totalChunks = (int) Math.ceil((double) data.length / CHUNK_SIZE);
        CHANNEL.sendTo(new DownloadStartPacket(skinOwnerUuid, isSlim, totalChunks, data.length),
                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);

        for (int i = 0; i < totalChunks; i++) {
            int offset = i * CHUNK_SIZE;
            int chunkSize = Math.min(CHUNK_SIZE, data.length - offset);
            byte[] chunk = Arrays.copyOfRange(data, offset, offset + chunkSize);
            CHANNEL.sendTo(new DownloadChunkPacket(skinOwnerUuid, i, chunk),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }

        CHANNEL.sendTo(new DownloadEndPacket(skinOwnerUuid),
                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void requestSkinList() {
        CHANNEL.sendToServer(new RequestSkinListPacket());
    }

    public static void requestSkinFromServer(UUID playerUuid) {
        CHANNEL.sendToServer(new RequestSkinPacket(playerUuid));
    }

    public static void sendSkinReset() {
        CHANNEL.sendToServer(new ResetSkinPacket());
    }

    public static void uploadSkinToServer(byte[] imageData, boolean isSlim) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image.getWidth() > maxMultiplayerSize || image.getHeight() > maxMultiplayerSize) {
                image = downscaleImage(image, maxMultiplayerSize, maxMultiplayerSize);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] pngData = baos.toByteArray();

            while (pngData.length > 100000 && image.getWidth() > 64) {
                int newSize = image.getWidth() / 2;
                image = downscaleImage(image, newSize, newSize);
                baos.reset();
                ImageIO.write(image, "PNG", baos);
                pngData = baos.toByteArray();
            }

            sendChunkedUpload(pngData, isSlim);
        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to upload skin", e);
        }
    }

    private static void sendChunkedUpload(byte[] data, boolean isSlim) {
        int totalChunks = (int) Math.ceil((double) data.length / CHUNK_SIZE);
        CHANNEL.sendToServer(new UploadStartPacket(isSlim, totalChunks, data.length));

        for (int i = 0; i < totalChunks; i++) {
            int offset = i * CHUNK_SIZE;
            int chunkSize = Math.min(CHUNK_SIZE, data.length - offset);
            byte[] chunk = Arrays.copyOfRange(data, offset, offset + chunkSize);
            CHANNEL.sendToServer(new UploadChunkPacket(i, chunk));
        }

        CHANNEL.sendToServer(new UploadEndPacket());
    }

    private static BufferedImage downscaleImage(BufferedImage original, int w, int h) {
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, w, h, null);
        g.dispose();
        return resized;
    }

    // ==================== PACKET CLASSES ====================

    public record UploadStartPacket(boolean isSlim, int totalChunks, int totalSize) {
        public static void encode(UploadStartPacket pkt, FriendlyByteBuf buf) {
            buf.writeBoolean(pkt.isSlim);
            buf.writeInt(pkt.totalChunks);
            buf.writeInt(pkt.totalSize);
        }
        public static UploadStartPacket decode(FriendlyByteBuf buf) {
            return new UploadStartPacket(buf.readBoolean(), buf.readInt(), buf.readInt());
        }
    }

    public record UploadChunkPacket(int chunkIndex, byte[] data) {
        public static void encode(UploadChunkPacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.chunkIndex);
            buf.writeInt(pkt.data.length);
            buf.writeBytes(pkt.data);
        }
        public static UploadChunkPacket decode(FriendlyByteBuf buf) {
            int idx = buf.readInt();
            int len = buf.readInt();
            byte[] data = new byte[len];
            buf.readBytes(data);
            return new UploadChunkPacket(idx, data);
        }
    }

    public static class UploadEndPacket {
        public static void encode(UploadEndPacket pkt, FriendlyByteBuf buf) {}
        public static UploadEndPacket decode(FriendlyByteBuf buf) { return new UploadEndPacket(); }
    }

    public record RequestSkinListPacket() {
        public static void encode(RequestSkinListPacket pkt, FriendlyByteBuf buf) {}
        public static RequestSkinListPacket decode(FriendlyByteBuf buf) { return new RequestSkinListPacket(); }
    }

    public record RequestSkinPacket(UUID targetUuid) {
        public static void encode(RequestSkinPacket pkt, FriendlyByteBuf buf) { buf.writeUUID(pkt.targetUuid); }
        public static RequestSkinPacket decode(FriendlyByteBuf buf) { return new RequestSkinPacket(buf.readUUID()); }
    }

    public static class ResetSkinPacket {
        public static void encode(ResetSkinPacket pkt, FriendlyByteBuf buf) {}
        public static ResetSkinPacket decode(FriendlyByteBuf buf) { return new ResetSkinPacket(); }
    }

    public record SkinListResponsePacket(List<UUID> uuids) {
        public static void encode(SkinListResponsePacket pkt, FriendlyByteBuf buf) {
            buf.writeInt(pkt.uuids.size());
            pkt.uuids.forEach(buf::writeUUID);
        }
        public static SkinListResponsePacket decode(FriendlyByteBuf buf) {
            int count = buf.readInt();
            List<UUID> uuids = new ArrayList<>();
            for (int i = 0; i < count; i++) uuids.add(buf.readUUID());
            return new SkinListResponsePacket(uuids);
        }
    }

    public record DownloadStartPacket(UUID playerUuid, boolean isSlim, int totalChunks, int totalSize) {
        public static void encode(DownloadStartPacket pkt, FriendlyByteBuf buf) {
            buf.writeUUID(pkt.playerUuid);
            buf.writeBoolean(pkt.isSlim);
            buf.writeInt(pkt.totalChunks);
            buf.writeInt(pkt.totalSize);
        }
        public static DownloadStartPacket decode(FriendlyByteBuf buf) {
            return new DownloadStartPacket(buf.readUUID(), buf.readBoolean(), buf.readInt(), buf.readInt());
        }
    }

    public record DownloadChunkPacket(UUID playerUuid, int chunkIndex, byte[] data) {
        public static void encode(DownloadChunkPacket pkt, FriendlyByteBuf buf) {
            buf.writeUUID(pkt.playerUuid);
            buf.writeInt(pkt.chunkIndex);
            buf.writeInt(pkt.data.length);
            buf.writeBytes(pkt.data);
        }
        public static DownloadChunkPacket decode(FriendlyByteBuf buf) {
            UUID uuid = buf.readUUID();
            int idx = buf.readInt();
            int len = buf.readInt();
            byte[] data = new byte[len];
            buf.readBytes(data);
            return new DownloadChunkPacket(uuid, idx, data);
        }
    }

    public record DownloadEndPacket(UUID playerUuid) {
        public static void encode(DownloadEndPacket pkt, FriendlyByteBuf buf) { buf.writeUUID(pkt.playerUuid); }
        public static DownloadEndPacket decode(FriendlyByteBuf buf) { return new DownloadEndPacket(buf.readUUID()); }
    }

    public record PlayerSkinResetPacket(UUID playerUuid) {
        public static void encode(PlayerSkinResetPacket pkt, FriendlyByteBuf buf) { buf.writeUUID(pkt.playerUuid); }
        public static PlayerSkinResetPacket decode(FriendlyByteBuf buf) { return new PlayerSkinResetPacket(buf.readUUID()); }
    }
}
