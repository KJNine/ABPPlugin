package net.kjnine.abpplugin;

import com.comphenix.packetwrapper.WrapperPlayClientUseItem;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.MovingObjectPositionBlock;
import org.bukkit.Axis;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.block.data.type.Slab;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.UUID;

public final class ABPPlugin extends JavaPlugin implements Listener {

    public HashMap<UUID, HashMap<Vector, Integer>> abp_data = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.USE_ITEM) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if(!abp_data.containsKey(player.getUniqueId()))
                    return;
                PacketContainer packetContainer = event.getPacket();
                WrapperPlayClientUseItem packet = new WrapperPlayClientUseItem(packetContainer);
                MovingObjectPositionBlock mop = packet.getPosition();
                Vector vec3d = mop.getPosVector();
                if (!Double.isFinite(vec3d.getX()) || !Double.isFinite(vec3d.getY()) || !Double.isFinite(vec3d.getZ())) {
                    return;
                }
                BlockPosition blockPos = mop.getBlockPosition();
                Vector vec3d1 = blockPos.toVector().clone().add(new Vector(0.5D, 0.5D, 0.5D));
                Vector vec3d2 = vec3d.clone().subtract(vec3d1);
                double xdiff = vec3d.getX() - blockPos.getX();
                if(xdiff < 2) {
                    return;
                }

                if(vec3d2.getX() >= 1.0000001D) {
                    mop.setPosVector(new Vector(vec3d1.getX(), vec3d.getY(), vec3d.getZ()));
                    packet.setPosition(mop);
                }
                event.setPacket(packet.getHandle());

                int abpData = ((int)xdiff-2) / 2;
                abp_data.get(player.getUniqueId()).put(blockPos.toVector(), abpData);
                getServer().getScheduler().runTaskLater(ABPPlugin.this, () -> abp_data.remove(blockPos), 1L);
            }
        });
        getServer().getMessenger().registerIncomingPluginChannel(this, "kjnine:abp",
                (channel, player, message) -> {
                    if(!channel.equals("kjnine:abp")) return;
                    getLogger().info(player.getName() + " has client-supported AccurateBlockPlacement mod");
                    abp_data.putIfAbsent(player.getUniqueId(), new HashMap<>());
                });
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(command.getName().equals("abp-enable")) {
            if(sender.hasPermission("abp.enable")) {
                if(!(sender instanceof Player))
                    return true;
                Player player = (Player) sender;
                abp_data.putIfAbsent(player.getUniqueId(), new HashMap<>());
                player.sendMessage(ChatColor.GRAY +
                        "Enabled Accurate-Block-Placement for yourself. " +
                        "This might make block-placing buggy. Relog to disable.");
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Vector blockPos = e.getBlock().getLocation().toVector(),
                against = e.getBlockAgainst().getLocation().toVector();
        if(!abp_data.containsKey(e.getPlayer().getUniqueId()))
            return;
        HashMap<Vector, Integer> pdata = abp_data.get(e.getPlayer().getUniqueId());
        int protocol;
        if(pdata.containsKey(blockPos))
            protocol = pdata.get(blockPos);
        else if(pdata.containsKey(against))
            protocol = pdata.get(against);
        else return;
        pdata.remove(blockPos);
        abp_data.put(e.getPlayer().getUniqueId(), pdata);
        Block block = e.getBlock();
        BlockState state = block.getState();
        BlockData blockData = state.getBlockData();
        if(blockData instanceof Directional) {
            Directional dir = (Directional) blockData;
            int dirId = protocol & 0xF;
            BlockFace face = dir.getFacing();
            if(dirId == 6)
                face = dir.getFacing().getOppositeFace();
            else if(dirId <= 5)
                face = directions[dirId];
            if(!dir.getFaces().contains(face))
                face = e.getPlayer().getFacing().getOppositeFace();
            if(face != dir.getFacing() && dir.getFaces().contains(face)) {
                if(dir instanceof Bed) {
                    return; // too complicated
                }
                dir.setFacing(face);
                blockData = dir;
            }
        } else if(blockData instanceof Orientable) {
            Orientable or = (Orientable) blockData;
            Axis axis = Axis.values()[protocol % 3];
            if(or.getAxes().contains(axis)) {
                or.setAxis(axis);
                blockData = or;
            }
        }
        protocol &= 0xFFFFFFF0;
        if(protocol >= 16) {
            if(blockData instanceof Repeater) {
                Repeater repeater = (Repeater) blockData;
                int delay = protocol / 16;
                if(repeater.getMinimumDelay() <= delay && repeater.getMaximumDelay() >= delay) {
                    repeater.setDelay(delay);
                    blockData = repeater;
                }
            } else if(protocol == 16) {
                if(blockData instanceof Comparator) {
                    Comparator comparator = (Comparator) blockData;
                    comparator.setMode(Comparator.Mode.SUBTRACT);
                    blockData = comparator;
                } else if(blockData instanceof Bisected) {
                    Bisected bisected = (Bisected) blockData;
                    if(bisected.getHalf() == Bisected.Half.BOTTOM) {
                        bisected.setHalf(Bisected.Half.TOP);
                        blockData = bisected;
                    }
                } else if(blockData instanceof Slab) {
                    Slab slab = (Slab) blockData;
                    if(slab.getType() == Slab.Type.BOTTOM) {
                        slab.setType(Slab.Type.TOP);
                        blockData = slab;
                    }
                }
            }
        }

        state.setBlockData(blockData);
        state.update(true, false);
    }

    // based on n.m.c.Direction enum order.
    public final BlockFace[] directions = new BlockFace[]{
            BlockFace.DOWN, BlockFace.UP,
            BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.WEST, BlockFace.EAST
    };

    @Override
    public void onDisable() {

    }
}
