package com.raresb.hud;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * The server resource pack (plugins/Hud/pack.zip): offers it to players and serves the download
 * on the game port itself, so no second port or web server is needed. A
 * connection whose first bytes are an HTTP "GET /pack" gets the zip; anything else is a normal
 * Minecraft connection and is left alone.
 *
 * <p>Players who have the pack loaded carry the {@code hud:pack} flag in their data, which the
 * other plugins read (by key) to decide whether to use the custom icons.
 */
final class PackHost implements Listener {
    private static final UUID PACK_ID = UUID.fromString("5e2f6f0a-7c1d-4b53-9a57-3d1f0c8e26a1");
    private static final String HANDLER = "hud_pack_host";
    private static final String HOLDER = "io.papermc.paper.network.ChannelInitializeListenerHolder";
    private static final String LISTENER = "io.papermc.paper.network.ChannelInitializeListener";

    private final HudPlugin plugin;
    private final NamespacedKey loadedKey;
    private final Key listenerKey;
    /** The zip and its SHA-1, or null when there is no pack. Read from network threads. */
    private volatile byte[] pack;
    private volatile byte[] sha1;
    private boolean hooked;

    PackHost(HudPlugin plugin) {
        this.plugin = plugin;
        this.loadedKey = new NamespacedKey(plugin, "pack");
        this.listenerKey = Key.key("hud", "pack_host");
    }

    void enable() {
        load();
        // Paper's hook for new connections lives in the server, not the API: reach it by name so
        // a server update that moves it only disables the pack instead of breaking the plugin.
        try {
            Class<?> listenerType = Class.forName(LISTENER);
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] {listenerType}, (proxy, method, args) -> {
                if (method.getName().equals("afterInitChannel")) {
                    ((Channel) args[0]).pipeline().addFirst(HANDLER, new Sniffer());
                    return null;
                }
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> "Hud pack host";
                };
            });
            Class.forName(HOLDER).getMethod("addListener", Key.class, listenerType).invoke(null, listenerKey, listener);
            hooked = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Can't serve the resource pack on the game port; players won't be offered it.", e);
        }
        plugin.getServer().getOnlinePlayers().forEach(this::offer);
    }

    void disable() {
        if (hooked) {
            try {
                Class.forName(HOLDER).getMethod("removeListener", Key.class).invoke(null, listenerKey);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // the server is shutting down anyway
            }
        }
    }

    /** Re-reads pack.zip and offers it again to everyone online. */
    void reload() {
        load();
        plugin.getServer().getOnlinePlayers().forEach(this::offer);
    }

    private void load() {
        File file = packFile();
        pack = null;
        sha1 = null;
        if (!file.isFile()) {
            plugin.getLogger().info("No plugins/Hud/pack.zip: no resource pack is offered.");
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            sha1 = MessageDigest.getInstance("SHA-1").digest(bytes);
            pack = bytes;
            plugin.getLogger().info("Resource pack loaded (" + bytes.length / 1024 + " KB).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Can't read pack.zip", e);
        }
    }

    private void offer(Player player) {
        player.getPersistentDataContainer().remove(loadedKey);
        byte[] hash = sha1;
        String address = plugin.getConfig().getString("resource-pack.address", "");
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (hash == null || !plugin.getConfig().getBoolean("resource-pack.enabled", true)
                || (url.isBlank() && (address.isBlank() || !hooked))) {
            return;
        }
        if (url.isBlank()) {
            // The hash in the file name makes clients fetch a changed pack instead of a cached one.
            url = "http://" + address + "/pack-" + HexFormat.of().formatHex(hash, 0, 4) + ".zip";
        }
        String prompt = plugin.getConfig().getString("resource-pack.prompt", "");
        player.setResourcePack(PACK_ID, url, hash, prompt.isBlank() ? plugin.lang().get("pack-prompt") : Component.text(prompt),
                plugin.getConfig().getBoolean("resource-pack.required", false));
    }

    File packFile() {
        return new File(plugin.getDataFolder(), "pack.zip");
    }

    boolean loaded() {
        return pack != null;
    }

    /** Size in KB and short SHA-1 of the loaded pack, for /hud pack status. */
    String describe() {
        byte[] bytes = pack;
        byte[] hash = sha1;
        return bytes == null || hash == null ? "none" : bytes.length / 1024 + " KB, SHA-1 " + HexFormat.of().formatHex(hash, 0, 4) + "…";
    }

    /**
     * /hud pack url: downloads the pack from a public URL (off the main thread), saves it as
     * pack.zip (so the client hash and the icons the other plugins look up match the hosted file),
     * sets resource-pack.url, enables the pack and offers it to everyone.
     */
    void download(String url, org.bukkit.command.CommandSender sender) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var client = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                        .connectTimeout(java.time.Duration.ofSeconds(15)).build();
                var response = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(60)).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                byte[] body = response.body();
                if (response.statusCode() != 200 || body.length < 4 || body[0] != 'P' || body[1] != 'K') {
                    throw new java.io.IOException("not a zip (HTTP " + response.statusCode() + ", " + body.length + " bytes)");
                }
                plugin.getDataFolder().mkdirs();
                Files.write(packFile().toPath(), body);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getConfig().set("resource-pack.url", url);
                    plugin.getConfig().set("resource-pack.enabled", true);
                    plugin.saveConfig();
                    reload();
                    sender.sendMessage(Component.text("Resource pack downloaded (" + describe() + ") and offered to everyone online.",
                            net.kyori.adventure.text.format.NamedTextColor.GREEN));
                });
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text(
                        "Couldn't download the pack: " + e.getMessage(), net.kyori.adventure.text.format.NamedTextColor.RED)));
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        offer(event.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> event.getPlayer().getPersistentDataContainer().set(loadedKey, PersistentDataType.BYTE, (byte) 1);
            case ACCEPTED, DOWNLOADED -> {
                // still on its way
            }
            default -> event.getPlayer().getPersistentDataContainer().remove(loadedKey);
        }
    }

    /** First handler of every new connection: answers a pack download, otherwise steps aside. */
    private final class Sniffer extends ChannelInboundHandlerAdapter {
        private boolean http;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object message) {
            if (http) {
                ReferenceCountUtil.release(message); // rest of a request we already answered
                return;
            }
            if (message instanceof ByteBuf bytes && bytes.readableBytes() >= 4
                    && bytes.toString(bytes.readerIndex(), 4, StandardCharsets.US_ASCII).equals("GET ")) {
                http = true;
                String request = bytes.toString(bytes.readerIndex(), Math.min(bytes.readableBytes(), 64), StandardCharsets.US_ASCII);
                bytes.release();
                byte[] body = request.startsWith("GET /pack") ? pack : null;
                String head = body == null
                        ? "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        : "HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
                ByteBuf response = Unpooled.wrappedBuffer(head.getBytes(StandardCharsets.US_ASCII), body == null ? new byte[0] : body);
                // Written from the front of the pipeline, so the Minecraft encoders never see it.
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            ctx.pipeline().remove(this);
            ctx.fireChannelRead(message);
        }
    }
}
