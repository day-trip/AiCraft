package com.daytrip.aicraft.client;

import com.daytrip.aicraft.command.AiCommandManager;
import com.daytrip.aicraft.command.AiCraftCommands;
import com.daytrip.aicraft.event.BlockUpdateCallback;
import com.daytrip.aicraft.event.SendChatCallback;
import com.daytrip.aicraft.mixin.IMixinBlockBehaviour;
import com.daytrip.aicraft.natives.Chunk;
import com.daytrip.aicraft.util.TimerManager;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


public class AicraftClient implements ClientModInitializer {
    protected static final Set<Pair<Integer, Integer>> chunks = Collections.synchronizedSet(new HashSet<>());
    protected static final int WIDTH = 16;
    protected static final int HEIGHT = 384;
    protected static final int SHIFT = 64;

    @Override
    public void onInitializeClient() {
        System.out.println("AICraft Client initializing...");

        AiCommandManager.registerClass(AiCraftCommands.class);

        SendChatCallback.EVENT.register(message -> {
            if (message.stripLeading().startsWith("!")) {
                try {
                    AiCommandManager.execute(message.stripLeading());
                } catch (IllegalArgumentException e) {
                    AiCraftCommands.chatLog(e.getMessage());
                }
                return true;
            }
            return false;
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            TimerManager.tick();
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            ChunkPos pos = chunk.getPos();

            if (chunks.contains(new Pair<>(pos.x, pos.z))) {
                return;
            }
            chunks.add(new Pair<>(pos.x, pos.z));

            new Thread(() -> {
                System.out.println("Handling chunk: " + pos);

                byte[] data = new byte[WIDTH * WIDTH * HEIGHT];

                for (int x = 0; x < WIDTH; x++) {
                    for (int z = 0; z < WIDTH; z++) {
                        for (int y = 0; y < HEIGHT; y++) {
                            var state = chunk.getBlockState(pos.getBlockAt(x, y - SHIFT, z));
                            data[(x * WIDTH * HEIGHT) + ((y) * WIDTH) + z] = (byte) (blockType(state) + 1);
                        }
                    }
                }

                Chunk.build(pos.x, pos.z, data);
            }).start();
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            Chunk.remove(chunk.getPos().x, chunk.getPos().z);
            chunks.remove(new Pair<>(chunk.getPos().x, chunk.getPos().z));
        });

        BlockUpdateCallback.EVENT.register((pos, state) -> {
            ChunkPos cp = new ChunkPos(pos);
            assert Minecraft.getInstance().level != null;
            if (chunks.contains(new Pair<>(cp.x, cp.z)) && blockType(Minecraft.getInstance().level.getBlockState(pos)) != blockType(state)) {
                Chunk.set(pos.getX(), pos.getZ(), pos.getY(), blockType(state));
            }
            return false;
        });
    }

    private static byte blockType(BlockState state) {
        if (state.getBlock() == Blocks.WATER) {
            return 1;
        }
        if (/*!((IMixinBlockBehaviour) state.getBlock()).hasCollision()*/ !state.isSolid()) {
            return 0;
        }
        return -1;
    }
}
