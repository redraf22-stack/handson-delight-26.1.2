package com.pockyl.handson_delight.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.LiquidLook;
import com.pockyl.diegesis.api.client.ParticleStyle;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.CuttingBoardStation;
import com.pockyl.handson_delight.station.KegStation;
import com.pockyl.handson_delight.station.SkilletStation;
import com.pockyl.handson_delight.station.StoveStation;

import java.util.ArrayList;
import java.util.List;

/** Client setup; only loaded on clients. */
public final class HandsOnDelightClient {
    public static final Identifier OIL_POP = HandsOnDelight.id("oil_pop");
    public static final Identifier FAT = HandsOnDelight.id("fat");
    public static final Identifier PAN_SMOKE = HandsOnDelight.id("pan_smoke");
    public static final Identifier HEAT = HandsOnDelight.id("heat");
    public static final Identifier GRAIN = HandsOnDelight.id("grain");
    @SuppressWarnings("removal")
    private static final Identifier DIEGESIS_BUBBLE = Identifier.fromNamespaceAndPath("diegesis", "bubble");

    private HandsOnDelightClient() {
    }

    public static void init() {
        DiegesisClient.registerBehavior(CookingPotStation.ID, PotBehavior::new);
        DiegesisClient.registerBehavior(CookingPotStation.COPPER_ID, view -> new PotBehavior(view, PotBehavior.Shape.COPPER_POT));
        DiegesisClient.registerBehavior(CookingPotStation.MONSTER_ID, PotBehavior::new);
        DiegesisClient.registerBehavior(KegStation.ID, KegBehavior::lying);
        DiegesisClient.registerBehavior(KegStation.STANDING_ID, KegBehavior::standing);
        DiegesisClient.registerLiquid(HandsOnDelight.id("drink"), LiquidLook.of(0xC0B08850));
        KegLiquids.register();
        DiegesisClient.registerBehavior(CuttingBoardStation.ID, BoardBehavior::new);
        DiegesisClient.registerBehavior(SkilletStation.ID, SkilletBehavior::new);
        DiegesisClient.registerBehavior(StoveStation.ID, StoveBehavior::new);
        DiegesisClient.registerLiquid(HandsOnDelight.id("broth"), LiquidLook.of(0xE0B0A070).thick(0.6F));
        registerParticles();
        AddonCheck.install();
        warmBoardBook();
    }

    /** The board's cuts are read on a worker thread as the world joins: the first open of a board then has nothing to build. */
    private static void warmBoardBook() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            net.minecraft.world.level.Level level = minecraft.level;
            if (level == null || level == warmedLevel) {
                return;
            }
            warmedLevel = level;
            Thread worker = new Thread(() -> BoardBook.warm(level), "handson-board-cuts");
            worker.setDaemon(true);
            worker.start();
        });
    }

    private static net.minecraft.world.level.Level warmedLevel;

    private static List<Identifier> frames(String base, int count) {
        List<Identifier> frames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            frames.add(HandsOnDelight.id(base + "_" + i));
        }
        return frames;
    }

    /** The skillet's heat: oil seething round the food, fat spitting off it, smoke of a side that waits, shimmer over the hot pan. */
    @SuppressWarnings("removal")
    private static void registerParticles() {
        List<Identifier> popping = new ArrayList<>(List.of(DIEGESIS_BUBBLE, DIEGESIS_BUBBLE));
        for (int i = 0; i < 5; i++) {
            popping.add(Identifier.fromNamespaceAndPath("diegesis", "bubble_pop_" + i));
        }
        DiegesisClient.registerParticle(OIL_POP, ParticleStyle.builder(popping).color(0xE6F7C27A).size(0.8F, 1.3F).life(0.25F, 0.5F)
                .drag(1.0F).grow(0.4F).pop().build());
        DiegesisClient.registerParticle(FAT, ParticleStyle.builder(Identifier.fromNamespaceAndPath("minecraft", "drip_fall")).color(0xFFFFE7A8)
                .size(0.3F, 0.45F).life(0.3F, 0.6F).gravity(75.0F).drag(0.05F).stretch().pop().build());
        List<Identifier> steam = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            steam.add(Identifier.fromNamespaceAndPath("diegesis", "steam_" + i));
        }
        // Smaller than 1.21.1: there the particle grows to three times its size (up to ~0.5 block) and reads as campfire smoke from
        // the station's close camera. Here it stays a thin wisp (about 0.15 block), white and emissive so the dark kitchen does not
        // grey it.
        DiegesisClient.registerParticle(PAN_SMOKE, ParticleStyle.builder(steam).color(0x66E8E8E8).size(1.2F, 1.7F).life(1.4F, 2.0F)
                .gravity(-4.0F).drag(0.75F).grow(1.5F).wobble(1.0F).fadeIn(0.35F).emissive().build());
        DiegesisClient.registerParticle(HEAT, ParticleStyle.builder(frames("heat", 4)).color(0x48FFFFFF)
                .size(3.0F, 4.0F).life(0.8F, 1.3F).gravity(-5.0F).drag(0.6F).grow(0.4F).wobble(0.3F).fadeIn(0.4F).build());
        // Sugar, cocoa and the like shaken out over a pot: fine grains that fall straight and are gone in the soup.
        DiegesisClient.registerParticle(GRAIN, ParticleStyle.builder(Identifier.withDefaultNamespace("generic_0")).color(0xFFFFFFFF)
                .size(0.28F, 0.42F).life(0.35F, 0.6F).gravity(55.0F).drag(0.2F).pop().build());
    }

    /** Whether a scene stands in for the block at {@code pos} on this client. */
    public static boolean isShown(BlockPos pos) {
        return DiegesisClient.station(pos).isPresent();
    }

    /** Whether the scene at {@code pos} is drawn this frame: then it stands in for the block's renderer. */
    public static boolean isSceneDrawn(BlockPos pos) {
        return DiegesisClient.isSceneDrawn(pos);
    }

    /** Whether a station is shown on this client at {@code pos}. */
    public static boolean stationShown(BlockPos pos) {
        return DiegesisClient.station(pos).isPresent();
    }

    /** Whether a station is being worked at within {@code blocks} of {@code pos}: its scene is what the player watches. */
    public static boolean stationNear(BlockPos pos, double blocks) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || !com.pockyl.diegesis.impl.client.focus.FocusController.get().isActive()) {
            return false;
        }
        return player.position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= blocks * blocks;
    }
}
