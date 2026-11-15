package com.pockyl.handson_delight.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import com.pockyl.handson_delight.station.KitchenKeg;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brewin' and Chewin's keg as a {@link KitchenKeg}: it ferments as it always does, the station only reads it and uses it by hand. The
 * keg's code counts liquids in the loader's unit (droplets on Fabric): the station gets millibuckets.
 * <p>
 * Brewin' and Chewin' has no build for this Minecraft version, so the mod is not a dependency of this one any more: the keg is reached
 * by its names, through {@link KegAccess}, and {@link Pseudo} lets the game start without the mod at all. Every member the keg inherits
 * from Minecraft is called through Minecraft's own types, as before.
 */
@Pseudo
@Mixin(targets = KegAccess.KEG_BLOCK_ENTITY, remap = false)
abstract class KegMixin implements KitchenKeg {
    /** The keg's data keeps the ticks only: its temperature comes from the keg itself. */
    @Unique
    private ContainerData handson$data;
    /** The keg's recipes, kept for a second: they change when the game reloads them, not while it runs. */
    @Unique
    private List<RecipeHolder<?>> handson$known;
    @Unique
    private long handson$knownAt;

    @Override
    public ItemStack handson$getItem(int slot) {
        return KegAccess.item(this, slot);
    }

    @Override
    public void handson$setItem(int slot, ItemStack stack) {
        KegAccess.setItem(this, slot, stack);
    }

    @Override
    public Fluid handson$fluid() {
        return KegAccess.fluidOf(handson$contents());
    }

    @Override
    public int handson$amount() {
        return handson$millibuckets(KegAccess.amountOf(handson$contents()));
    }

    @Override
    public int handson$capacity() {
        return handson$millibuckets(KegAccess.capacity(this));
    }

    @Override
    public ContainerData handson$data() {
        if (handson$data == null) {
            handson$data = KitchenKeg.withTemperature(KegAccess.data(this), () -> KegAccess.temperature(this));
        }
        return handson$data;
    }

    @Override
    public void handson$feelTemperature() {
        if (handson$level() != null) {
            KegAccess.feelTemperature(this);
        }
    }

    @Override
    public int handson$wantedTemperature() {
        return KegAccess.wantedTemperature(this);
    }

    @Override
    public List<ItemStack> handson$use(ItemStack held, int most, boolean creative) {
        return KegAccess.use(this, held, most, creative);
    }

    @Override
    public boolean handson$canTap(ItemStack stack) {
        Optional<Object> recipe = handson$recipeFor(stack);
        if (recipe.isEmpty()) {
            return false;
        }
        Object pouring = recipe.get();
        Object contents = handson$contents();
        return ItemStack.isSameItem(stack, KegAccess.container(pouring)) && contents != null
                && KegAccess.fluidOf(contents) == KegAccess.rawFluid(pouring)
                && KegAccess.amountOf(contents) >= KegAccess.loaderAmount(pouring);
    }

    @Override
    public boolean handson$canPour(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Optional<Object> recipe = handson$recipeFor(stack);
        if (recipe.isPresent()) {
            // A drink poured back: the same liquid, with room for it.
            Object pouring = recipe.get();
            Object contents = handson$contents();
            return KegAccess.canFill(pouring) && !ItemStack.isSameItem(stack, KegAccess.container(pouring))
                    && (contents == null || KegAccess.fluidOf(contents) == KegAccess.rawFluid(pouring))
                    && KegAccess.loaderAmount(this) + KegAccess.loaderAmount(pouring) <= KegAccess.capacity(this);
        }
        Object inside = KegAccess.tankContents(KegAccess.fluidContainer(stack.copyWithCount(1)));
        if (inside == null) {
            return false;
        }
        Fluid liquid = KegAccess.fluidOf(inside);
        Object contents = handson$contents();
        if (contents != null && KegAccess.fluidOf(contents) != liquid) {
            return false;
        }
        if (contents == null && handson$pouring().stream().noneMatch(pouring -> KegAccess.pouredFluid(pouring, stack) == liquid)) {
            // The keg takes only liquids it knows a drink of, as its own use does.
            return false;
        }
        return KegAccess.loaderAmount(this) + KegAccess.amountOf(inside) <= KegAccess.capacity(this);
    }

    @Override
    public boolean handson$isVessel(ItemStack stack) {
        if (stack.isEmpty() || handson$level() == null) {
            return false;
        }
        if (KegAccess.fluidContainer(stack.copyWithCount(1)) != null) {
            return true;
        }
        return handson$pouring().stream()
                .anyMatch(pouring -> ItemStack.isSameItem(stack, KegAccess.container(pouring))
                        || ItemStack.isSameItem(stack, KegAccess.output(pouring)));
    }

    @Override
    public ItemStack handson$drinkOf(Fluid fluid) {
        if (fluid == Fluids.EMPTY || handson$level() == null) {
            return ItemStack.EMPTY;
        }
        for (Object pouring : handson$pouring()) {
            ItemStack drink = KegAccess.output(pouring);
            if (KegAccess.rawFluid(pouring) == fluid && !drink.isEmpty()) {
                return drink.copy();
            }
        }
        return fluid.getBucket() == Items.AIR ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
    }

    @Override
    public List<Fermenting> handson$recipes() {
        List<Fermenting> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : handson$ofType(KegAccess.fermentingType())) {
            Object recipe = holder.value();
            Optional<Object> base = KegAccess.fluidIngredient(recipe);
            Fluid baseFluid = base.flatMap(KegAccess::baseFluid).orElse(Fluids.EMPTY);
            int baseAmount = baseFluid == Fluids.EMPTY ? 0 : handson$millibuckets(KegAccess.loaderAmount(base.get()));
            recipes.add(new Fermenting(holder.id().identifier(), KegAccess.ingredients(recipe), baseFluid, baseAmount,
                    KegAccess.resultFluid(recipe), KegAccess.resultItem(recipe), KegAccess.fermentTime(recipe),
                    KegAccess.recipeTemperature(recipe)));
        }
        return recipes;
    }

    @Override
    public void handson$awardRecipes(Player player) {
        ((RecipeCraftingHolder) (Object) this).awardUsedRecipes(player, KegAccess.droppable(this));
    }

    @Override
    public BlockEntity handson$blockEntity() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    private Level handson$level() {
        return handson$blockEntity().getLevel();
    }

    /** What the keg's tank holds, or null with nothing in it. */
    @Unique
    private Object handson$contents() {
        return KegAccess.tankContents(KegAccess.tank(this));
    }

    @Unique
    private List<Object> handson$pouring() {
        List<Object> pouring = new ArrayList<>();
        for (RecipeHolder<?> holder : handson$ofType(KegAccess.pouringType())) {
            pouring.add(holder.value());
        }
        return pouring;
    }

    @Unique
    private Optional<Object> handson$recipeFor(ItemStack stack) {
        return handson$level() == null || stack.isEmpty() ? Optional.empty() : KegAccess.pouringRecipe(this, stack);
    }

    /** The keg's recipes of {@code type}, from the recipes the server knows. */
    @Unique
    private List<RecipeHolder<?>> handson$ofType(Object type) {
        List<RecipeHolder<?>> found = new ArrayList<>();
        if (type == null) {
            return found;
        }
        for (RecipeHolder<?> holder : handson$allRecipes()) {
            if (holder.value().getType() == type) {
                found.add(holder);
            }
        }
        return found;
    }

    /**
     * Every recipe the game knows, or none where there are none to be had: since this version the client is sent displays only and
     * keeps the recipes with the server, so a keg seen from a client that is not the server's own reads no recipes.
     */
    @Unique
    private List<RecipeHolder<?>> handson$allRecipes() {
        long now = System.nanoTime();
        if (handson$known == null || now - handson$knownAt > KegAccess.RECIPE_CACHE) {
            Level level = handson$level();
            MinecraftServer server = level == null ? null : level.getServer();
            handson$known = server == null ? List.of() : List.copyOf(server.getRecipeManager().getRecipes());
            handson$knownAt = now;
        }
        return handson$known;
    }

    @Unique
    private static int handson$millibuckets(long amount) {
        return KegAccess.millibuckets(amount);
    }
}

/**
 * Brewin' and Chewin's keg looked up by name: its own classes, its fields and its methods, once, and kept.
 * <p>
 * The mod has no build for this Minecraft version and is not a dependency of this one any more, so no member of it can be named here:
 * a mixin cannot even name its target. The names below are the ones the keg's code has always had; anything that is not found is
 * read as nothing, so a changed mod makes the station quiet instead of broken.
 */
final class KegAccess {
    static final String KEG_BLOCK_ENTITY = "umpaz.brewinandchewin.common.block.entity.KegBlockEntity";
    private static final String KEG = "umpaz.brewinandchewin.BrewinAndChewin";
    private static final String RECIPE_TYPES = "umpaz.brewinandchewin.common.registry.BnCRecipeTypes";
    private static final String FLUID_UNIT = "umpaz.brewinandchewin.common.utility.FluidUnit";
    /** How long the keg trusts its list of recipes, nanoseconds: recipes change when the game reloads them. */
    static final long RECIPE_CACHE = 1_000_000_000L;
    /** A millibucket in the loader's own unit where the keg's own converter cannot be had (droplets on Fabric). */
    private static final long DROPLETS_PER_MILLIBUCKET = 81L;

    private static final Map<String, Optional<Field>> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private static volatile Object helper;

    private static final ContainerData NO_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    private KegAccess() {
    }

    /** The keg's inventory slots: the ingredients, the container and the output. */
    static ItemStack item(Object keg, int slot) {
        Object inventory = get(keg, "inventory");
        Object found = call(inventory, "getStackInSlot", 1, slot);
        return found instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }

    static void setItem(Object keg, int slot, ItemStack stack) {
        call(get(keg, "inventory"), "setStackInSlot", 2, slot, stack);
    }

    /** The keg's tank, for its own liquids: null when it cannot be had. */
    static Object tank(Object keg) {
        return get(keg, "fluidTank");
    }

    /** What a keg's or an item's tank holds, or null with nothing in it. */
    static Object tankContents(Object tank) {
        if (tank == null) {
            return null;
        }
        Object empty = call(tank, "isEmpty", 0);
        if (empty instanceof Boolean value && value) {
            return null;
        }
        Object contents = call(tank, "getAbstractedFluid", 0);
        return contents != null && !Boolean.TRUE.equals(call(contents, "isEmpty", 0)) ? contents : null;
    }

    static long capacity(Object keg) {
        Object capacity = call(tank(keg), "getFluidCapacity", 0);
        return capacity instanceof Number number ? number.longValue() : 0;
    }

    static ContainerData data(Object keg) {
        return get(keg, "kegData") instanceof ContainerData data ? data : NO_DATA;
    }

    static int temperature(Object keg) {
        Object temperature = call(keg, "getTemperature", 0);
        return temperature instanceof Number number ? number.intValue() : 0;
    }

    static void feelTemperature(Object keg) {
        call(keg, "updateTemperature", 0);
    }

    static int wantedTemperature(Object keg) {
        Object holder = call(keg, "getRecipeWithoutTemperature", 0);
        if (holder instanceof Optional<?> optional && optional.isPresent()) {
            return recipeTemperature(call(optional.get(), "value", 0));
        }
        return 0;
    }

    static List<ItemStack> use(Object keg, ItemStack held, int most, boolean creative) {
        List<ItemStack> back = new ArrayList<>();
        Object used = call(keg, "extractInWorld", 3, held, most, creative);
        if (used instanceof List<?> list) {
            for (Object stack : list) {
                if (stack instanceof ItemStack got) {
                    back.add(got);
                }
            }
        }
        return back;
    }

    static Optional<Object> pouringRecipe(Object keg, ItemStack stack) {
        Object found = call(keg, "getPouringRecipe", 1, stack);
        return found instanceof Optional<?> optional && optional.isPresent() ? Optional.of(optional.get()) : Optional.empty();
    }

    /** The keg's own inventory as its menu drops it. */
    static List<ItemStack> droppable(Object keg) {
        List<ItemStack> stacks = new ArrayList<>();
        Object inventory = call(keg, "getDroppableInventory", 0);
        if (inventory instanceof List<?> list) {
            for (Object stack : list) {
                if (stack instanceof ItemStack got) {
                    stacks.add(got);
                }
            }
        }
        return stacks;
    }

    /** The keg a held item is a container of, or null. */
    static Object fluidContainer(ItemStack stack) {
        Object helper = helper();
        return helper == null ? null : call(helper, "getFluidContainerFromItem", 1, stack);
    }

    private static Object helper() {
        Object found = helper;
        if (found == null) {
            found = callStatic(type(KEG), "getHelper", 0);
            helper = found;
        }
        return found;
    }

    static Object fermentingType() {
        return constant(RECIPE_TYPES, "FERMENTING");
    }

    static Object pouringType() {
        return constant(RECIPE_TYPES, "KEG_POURING");
    }

    // --- one pouring recipe -----------------------------------------------------------------------------------------------

    static boolean canFill(Object pouring) {
        return Boolean.TRUE.equals(call(pouring, "canFill", 0));
    }

    static ItemStack container(Object pouring) {
        return stack(call(pouring, "getContainer", 0));
    }

    static ItemStack output(Object pouring) {
        return stack(call(pouring, "getOutput", 0));
    }

    /** The liquid a pouring recipe draws, or the one it holds when poured from {@code stack}. */
    static Fluid rawFluid(Object pouring) {
        return fluidOf(call(pouring, "getRawFluid", 0));
    }

    static Fluid pouredFluid(Object pouring, ItemStack stack) {
        return fluidOf(call(pouring, "getFluid", 1, stack));
    }

    // --- one fermenting recipe --------------------------------------------------------------------------------------------

    static Optional<Object> fluidIngredient(Object recipe) {
        Object found = call(recipe, "getFluidIngredient", 0);
        return found instanceof Optional<?> optional && optional.isPresent() ? Optional.of(optional.get()) : Optional.empty();
    }

    /** The first liquid an ingredient of a recipe takes, as it shows it, or none. */
    static Optional<Fluid> baseFluid(Object fluidIngredient) {
        Object stacks = call(call(fluidIngredient, "ingredient", 0), "displayStacks", 0);
        if (stacks instanceof List<?> list) {
            for (Object first : list) {
                return Optional.of(fluidOf(first));
            }
        }
        return Optional.empty();
    }

    /** The quantity of a pouring recipe or of a liquid ingredient, in the keg's own unit. */
    static long loaderAmount(Object amount) {
        Object found = call(amount, "getLoaderAmount", 0);
        return found instanceof Number number ? number.longValue() : 0;
    }

    static List<Ingredient> ingredients(Object recipe) {
        List<Ingredient> ingredients = new ArrayList<>();
        if (call(recipe, "getIngredients", 0) instanceof List<?> list) {
            for (Object one : list) {
                if (one instanceof Ingredient ingredient) {
                    ingredients.add(ingredient);
                }
            }
        }
        return ingredients;
    }

    static Fluid resultFluid(Object recipe) {
        Object left = call(call(recipe, "getResult", 0), "left", 0);
        return left instanceof Optional<?> optional && optional.isPresent() ? fluidOf(optional.get()) : Fluids.EMPTY;
    }

    static ItemStack resultItem(Object recipe) {
        Object right = call(call(recipe, "getResult", 0), "right", 0);
        return right instanceof Optional<?> optional && optional.isPresent() ? stack(optional.get()).copy() : ItemStack.EMPTY;
    }

    static int fermentTime(Object recipe) {
        Object time = call(recipe, "getFermentTime", 0);
        return time instanceof Number number ? number.intValue() : 0;
    }

    static int recipeTemperature(Object recipe) {
        Object temperature = call(recipe, "getTemperature", 0);
        return temperature instanceof Number number ? number.intValue() : 0;
    }

    // --- liquids ----------------------------------------------------------------------------------------------------------

    static Fluid fluidOf(Object contents) {
        Object fluid = call(contents, "fluid", 0);
        return fluid instanceof Fluid value ? value : Fluids.EMPTY;
    }

    static long amountOf(Object contents) {
        Object amount = call(contents, "amount", 0);
        return amount instanceof Number number ? number.longValue() : 0;
    }

    /** The keg counts liquids in the loader's own unit; the station counts millibuckets. */
    static int millibuckets(long amount) {
        Class<?> unit = type(FLUID_UNIT);
        Object converted = callStatic(unit, "convert", 3, amount, callStatic(unit, "getLoaderUnit", 0), constant(unit, "MILLIBUCKET"));
        return converted instanceof Number number ? (int) number.longValue() : (int) (amount / DROPLETS_PER_MILLIBUCKET);
    }

    private static ItemStack stack(Object value) {
        return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }

    // --- looking names up -------------------------------------------------------------------------------------------------

    private static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** A static field of the class {@code owner}, as the recipe types are, or null. */
    private static Object constant(String owner, String name) {
        return constant(type(owner), name);
    }

    private static Object constant(Class<?> owner, String name) {
        Field field = field(owner, name);
        if (field == null) {
            return null;
        }
        try {
            return field.get(null);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Object get(Object target, String name) {
        if (target == null) {
            return null;
        }
        Field field = field(target.getClass(), name);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field field(Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        String key = owner.getName() + '#' + name;
        Optional<Field> cached = FIELDS.get(key);
        if (cached == null) {
            Field found = null;
            for (Class<?> type = owner; type != null && found == null; type = type.getSuperclass()) {
                try {
                    found = type.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    // On up the keg's own classes.
                }
            }
            if (found != null) {
                try {
                    found.setAccessible(true);
                } catch (RuntimeException ignored) {
                    found = null;
                }
            }
            cached = Optional.ofNullable(found);
            FIELDS.put(key, cached);
        }
        return cached.orElse(null);
    }

    private static Object call(Object target, String name, int arity, Object... arguments) {
        if (target == null) {
            return null;
        }
        Method method = method(target.getClass(), name, arity);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, arguments);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object callStatic(Class<?> owner, String name, int arity, Object... arguments) {
        if (owner == null) {
            return null;
        }
        Method method = method(owner, name, arity);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, arguments);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** A method by its name and how many arguments it takes: the keg's own types are not here to name it by. */
    private static Method method(Class<?> owner, String name, int arity) {
        if (owner == null) {
            return null;
        }
        String key = owner.getName() + '#' + name + '/' + arity;
        Optional<Method> cached = METHODS.get(key);
        if (cached == null) {
            Method found = search(owner, name, arity);
            if (found != null) {
                try {
                    found.setAccessible(true);
                } catch (RuntimeException ignored) {
                    found = null;
                }
            }
            cached = Optional.ofNullable(found);
            METHODS.put(key, cached);
        }
        return cached.orElse(null);
    }

    private static Method search(Class<?> owner, String name, int arity) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            Method found = pick(type.getDeclaredMethods(), name, arity);
            if (found != null) {
                return found;
            }
            for (Class<?> face : type.getInterfaces()) {
                Method onFace = search(face, name, arity);
                if (onFace != null) {
                    return onFace;
                }
            }
        }
        return null;
    }

    private static Method pick(Method[] methods, String name, int arity) {
        for (Method method : methods) {
            if (method.getName().equals(name) && method.getParameterCount() == arity) {
                return method;
            }
        }
        return null;
    }
}
