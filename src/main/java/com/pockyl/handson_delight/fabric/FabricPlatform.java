package com.pockyl.handson_delight.fabric;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;
import vectorwing.farmersdelight.refabricated.inventory.ItemStackHandler;
import vectorwing.farmersdelight.refabricated.inventory.RecipeWrapper;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.assembly.BoardDishes;
import com.pockyl.handson_delight.platform.Platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Farmer's Delight Refabricated: the pot keeps its items in Refabricated's own port of NeoForge's item handler. */
public final class FabricPlatform implements Platform {
    @Override
    public ItemStack getItem(CookingPotBlockEntity pot, int slot) {
        return pot.getInventory().getStackInSlot(slot);
    }

    @Override
    public void setItem(CookingPotBlockEntity pot, int slot, ItemStack stack) {
        pot.getInventory().setStackInSlot(slot, stack);
    }

    @Override
    public Optional<RecipeHolder<CookingPotRecipe>> findRecipe(Level level, List<ItemStack> items) {
        // Matched here, ingredient by ingredient: Farmer's Delight asks its own wrapper's StackedItemContents, which comes out
        // empty on a client (no crafting book behind it) - no dish was ever found while cooking, so the badge and the broth's
        // colour never came. An ingredient's own test covers plain items, tags and Fabric's "any of" alike.
        return matchByHand(recipesOf(level), items);
    }

    /** The pot's recipes: the server's own manager, or the ones synced to a client. */
    private static List<RecipeHolder<CookingPotRecipe>> recipesOf(Level level) {
        return Platform.INSTANCE.cookingRecipes(level);
    }

    /** The recipe {@code items} make in the pot: one of each of its ingredients, whatever order they lie in. */
    private static Optional<RecipeHolder<CookingPotRecipe>> matchByHand(List<RecipeHolder<CookingPotRecipe>> recipes,
            List<ItemStack> items) {
        List<ItemStack> given = new java.util.ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                given.add(stack);
            }
        }
        for (RecipeHolder<CookingPotRecipe> holder : recipes) {
            List<net.minecraft.world.item.crafting.Ingredient> needs = holder.value().input();
            if (needs.isEmpty() || needs.size() != given.size()) {
                continue;
            }
            int[] taken = new int[given.size()];
            if (fits(needs, given, 0, taken)) {
                return Optional.of(holder);
            }
        }
        return Optional.empty();
    }

    /** Whether every ingredient from {@code need} on finds a stack of its own: {@code taken} says which ones are used. */
    private static boolean fits(List<net.minecraft.world.item.crafting.Ingredient> needs, List<ItemStack> given, int need,
            int[] taken) {
        if (need == needs.size()) {
            return true;
        }
        for (int i = 0; i < given.size(); i++) {
            if (taken[i] == 0 && needs.get(need).test(given.get(i))) {
                taken[i] = 1;
                if (fits(needs, given, need + 1, taken)) {
                    return true;
                }
                taken[i] = 0;
            }
        }
        return false;
    }

    @Override
    public ItemStack cooked(Level level, CookingPotRecipe recipe, List<ItemStack> items) {
        return recipe.assemble(new RecipeWrapper(slots(items)));
    }

    /** The pot's slots holding copies of {@code items}, from the first one on. */
    private static ItemStackHandler slots(List<ItemStack> items) {
        ItemStackHandler slots = new ItemStackHandler(CookingPotBlockEntity.INVENTORY_SIZE);
        for (int i = 0; i < Math.min(items.size(), CookingPotBlockEntity.MEAL_DISPLAY_SLOT); i++) {
            slots.setStackInSlot(i, items.get(i).copy());
        }
        return slots;
    }

    @Override
    public List<RecipeHolder<CookingPotRecipe>> cookingRecipes(Level level) {
        // Same as above: a server reads its own manager, a client the recipes synced to it.
        if (level.recipeAccess() instanceof net.minecraft.world.item.crafting.RecipeManager manager) {
            List<RecipeHolder<CookingPotRecipe>> found = new java.util.ArrayList<>();
            for (RecipeHolder<?> holder : manager.getRecipes()) {
                if (holder.value() instanceof CookingPotRecipe recipe) {
                    @SuppressWarnings("unchecked")
                    RecipeHolder<CookingPotRecipe> typed = (RecipeHolder<CookingPotRecipe>) holder;
                    found.add(typed);
                }
            }
            return found;
        }
        return new java.util.ArrayList<>(((net.fabricmc.fabric.api.recipe.v1.FabricRecipeAccess) level.recipeAccess())
                .getSynchronizedRecipes().getAllOfType(ModRecipeTypes.COOKING.get()));
    }

    @Override
    public ItemStack getItem(CuttingBoardBlockEntity board) {
        return board.getInventory().getStackInSlot(0);
    }

    @Override
    public void setItem(CuttingBoardBlockEntity board, ItemStack stack) {
        board.getInventory().setStackInSlot(0, stack);
    }

    @Override
    public void setItem(SkilletBlockEntity skillet, ItemStack stack) {
        skillet.getInventory().setStackInSlot(0, stack);
    }

    @Override
    public ItemStack getItem(AbstractStoveBlockEntity stove, int slot) {
        return stove.getItems().getStackInSlot(slot);
    }

    @Override
    public void setItem(AbstractStoveBlockEntity stove, int slot, ItemStack stack) {
        stove.getItems().setStackInSlot(slot, stack);
    }

    @Override
    public boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isFarmersDelightAddon(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(mod -> mod.getMetadata().getDependencies().stream()
                        .anyMatch(dependency -> dependency.getKind() == ModDependency.Kind.DEPENDS
                                && dependency.getModId().equals(FarmersDelightContent.MOD_ID)))
                .orElse(false);
    }

    /** The client names the channels it listens on while it configures, before it joins the world. */
    @Override
    public boolean hasThisMod(ServerPlayer player) {
        // No connection, or one that goes nowhere (game tests): no client.
        return player instanceof FakePlayer || player.connection == null || player.connection.getRemoteAddress() == null
                || ServerPlayNetworking.canSend(player, BoardDishes.Payload.TYPE);
    }

    @Override
    public void sendBoardDishes(ServerPlayer player) {
        if (player.connection != null && ServerPlayNetworking.canSend(player, BoardDishes.Payload.TYPE)) {
            ServerPlayNetworking.send(player, BoardDishes.payload());
        }
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    /**
     * Refabricated 3's item ability ingredient is a plain Fabric custom ingredient with its own test, so Fabric's wrapper tests
     * it as it is (on 1.20.1 its test lost its name in the remap and had to be unwrapped).
     */
    @Override
    public Ingredient unwrap(Ingredient ingredient) {
        return ingredient;
    }
}
