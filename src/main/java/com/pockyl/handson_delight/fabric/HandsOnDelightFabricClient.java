package com.pockyl.handson_delight.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import com.pockyl.handson_delight.assembly.BoardDishes;
import com.pockyl.handson_delight.client.HandsOnDelightClient;

import java.io.File;

/** Fabric entry point, client. */
public final class HandsOnDelightFabricClient implements ClientModInitializer {
    /** The tick of the last screenshot taken (a paused game keeps its tick count: without this a shot every frame). */
    private static int lastShot = Integer.MIN_VALUE;

    @Override
    public void onInitializeClient() {
        HandsOnDelightClient.init();
        // Pot models are resolved by Farmer's Delight in 26.1: the old BlockModelDefinition API is gone.
        ClientPlayNetworking.registerGlobalReceiver(BoardDishes.Payload.TYPE, (payload, context) -> BoardDishes.receive(payload));
        // The next server may not have the mod: its sandwiches are crafted.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BoardDishes.clear());
        shots();
    }

    /**
     * For looking at the client while porting: with {@code -Dhds.shot=<dir>} the client saves a screenshot of what it shows every
     * ten seconds (the first after five), so a session on this machine can be looked at without a hand on the keyboard.
     */
    private static void shots() {
        String dir = System.getProperty("hds.shot");
        if (dir == null) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            int tick = client.gui.getGuiTicks();
            // A paused game keeps its tick count, so only one shot per tick is taken.
            if (client.level == null || tick == lastShot || tick % 200 != 100) {
                return;
            }
            lastShot = tick;
            Screenshot.grab(new File(dir), Minecraft.getInstance().getMainRenderTarget(), message -> {
            });
        });
    }
}
