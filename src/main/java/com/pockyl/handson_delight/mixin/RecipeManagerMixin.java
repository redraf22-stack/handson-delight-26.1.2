package com.pockyl.handson_delight.mixin;

import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.assembly.AssemblyRecipes;

import java.util.HashMap;
import java.util.Map;

/**
 * Food put together on the cutting board: the server's recipes are read again for the board when they load. In 26.1 the manager
 * parses its recipes through a codec and keeps no JSON, so the board reads its own from the reload's resources
 * ({@link AssemblyRecipes#collectFrom}); the ones the manager loaded are its {@link RecipeMap}. The board's recipes stay among the
 * server's, for automation; a player's crafting grid does not make them ({@code CraftingMenuMixin}).
 */
@Mixin(RecipeManager.class)
abstract class RecipeManagerMixin {
    @Shadow
    @Final
    private HolderLookup.Provider registries;

    @Inject(method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void handson$keepAssembled(RecipeMap recipes, ResourceManager resources, ProfilerFiller profiler, CallbackInfo callback) {
        Map<Identifier, JsonObject> taken = new HashMap<>();
        Map<Identifier, JsonObject> crafts = new HashMap<>();
        AssemblyRecipes.collectFrom(resources, taken, crafts);
        Map<Identifier, RecipeHolder<?>> loaded = new HashMap<>();
        for (RecipeHolder<?> holder : recipes.values()) {
            loaded.put(holder.id().identifier(), holder);
        }
        // Only those the manager loaded: a recipe whose conditions fail is not made anywhere.
        AssemblyRecipes.load(taken, crafts, loaded, registries, resources);
    }
}
