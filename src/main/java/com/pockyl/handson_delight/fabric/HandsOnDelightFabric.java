package com.pockyl.handson_delight.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeSerializers;

import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.assembly.BoardDishes;

/** Fabric entry point, both sides. */
public final class HandsOnDelightFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        HandsOnDelight.init();
        PayloadTypeRegistry.clientboundPlay().register(BoardDishes.Payload.TYPE, BoardDishes.Payload.CODEC);
        // The books and the pans read the recipes synced to the client; since 26.1 only the serializers registered here are.
        RecipeSynchronization.synchronizeRecipeSerializer(CampfireCookingRecipe.SERIALIZER);
        RecipeSynchronization.synchronizeRecipeSerializer(SmeltingRecipe.SERIALIZER);
        RecipeSynchronization.synchronizeRecipeSerializer(ModRecipeSerializers.CUTTING.get());
        RecipeSynchronization.synchronizeRecipeSerializer(ModRecipeSerializers.COOKING.get());
    }
}
