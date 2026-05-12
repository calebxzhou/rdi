package calebxzhou.rdi.mc.client.compat.jei;

import calebxzhou.rdi.mc.common2.mcp.RMcpRecipeData;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class RJeiRecipeSource {
    private static final Logger LGR = LoggerFactory.getLogger(RJeiRecipeSource.class);
    private static final Component INDEX_BUILD_FAILED_MESSAGE = Component.literal("RDI无法为合成表建立索引 详见日志 若不使用AI陪玩请忽略本消息");
    private static final int MAX_DIRECT_ITEMS = 4;
    private static final int MAX_TAG_EXAMPLES = 3;
    private static final ExecutorService INDEX_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "rdi-jei-recipe-index");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicInteger generation = new AtomicInteger();
    private static final AtomicInteger notifiedFailureGeneration = new AtomicInteger();
    private static volatile IJeiRuntime runtime;
    private static volatile Map<String, List<RMcpRecipeData>> recipeIndex = Map.of();
    private static volatile CompletableFuture<?> indexFuture;
    private static volatile IndexState indexState = IndexState.EMPTY;

    private enum IndexState {
        EMPTY,
        BUILDING,
        READY,
        FAILED
    }

    private RJeiRecipeSource() {
    }

    public static void setRuntime(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        recipeIndex = Map.of();
        var nextGeneration = generation.incrementAndGet();
        notifiedFailureGeneration.set(0);
        startBuildIndexAsync(jeiRuntime, nextGeneration);
    }

    public static void clearRuntime() {
        runtime = null;
        recipeIndex = Map.of();
        indexState = IndexState.EMPTY;
        notifiedFailureGeneration.set(0);
        generation.incrementAndGet();
    }

    public static List<RMcpRecipeData> queryByOutputItem(String itemId) {
        if (indexState != IndexState.READY) {
            var currentRuntime = runtime;
            if (currentRuntime != null) {
                startBuildIndexAsync(currentRuntime, generation.get());
            }
            return List.of();
        }
        return recipeIndex.getOrDefault(itemId, List.of());
    }

    private static void startBuildIndexAsync(IJeiRuntime currentRuntime, int targetGeneration) {
        var currentFuture = indexFuture;
        if (currentFuture != null && !currentFuture.isDone()) {
            return;
        }
        synchronized (RJeiRecipeSource.class) {
            currentFuture = indexFuture;
            if (currentFuture != null && !currentFuture.isDone()) {
                return;
            }
            indexState = IndexState.BUILDING;
            indexFuture = CompletableFuture.supplyAsync(() -> buildRecipeIndex(currentRuntime.getRecipeManager()), INDEX_EXECUTOR)
                    .thenAccept(index -> {
                        if (generation.get() == targetGeneration && runtime == currentRuntime) {
                            recipeIndex = index;
                            indexState = IndexState.READY;
                        }
                    })
                    .exceptionally(e -> {
                        if (generation.get() == targetGeneration && runtime == currentRuntime) {
                            recipeIndex = Map.of();
                            indexState = IndexState.FAILED;
                            LGR.error("RDI无法为合成表建立索引", e);
                            notifyIndexBuildFailed(targetGeneration, e.getMessage());
                        }
                        return null;
                    });
        }
    }

    private static void notifyIndexBuildFailed(int targetGeneration, String message) {
        if (!notifiedFailureGeneration.compareAndSet(0, targetGeneration)) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(INDEX_BUILD_FAILED_MESSAGE, false);
                minecraft.player.displayClientMessage(Component.literal("错误信息："+message), false);
                return;
            }
            minecraft.gui.getChat().addMessage(INDEX_BUILD_FAILED_MESSAGE);
        });
    }

    private static Map<String, List<RMcpRecipeData>> buildRecipeIndex(IRecipeManager recipeManager) {
        var index = new LinkedHashMap<String, ArrayList<RMcpRecipeData>>();
        var categories = recipeManager.createRecipeCategoryLookup()
                .includeHidden()
                .get()
                .toList();
        for (var category : categories) {
            indexCategory(recipeManager, category, index);
        }
        var immutable = new LinkedHashMap<String, List<RMcpRecipeData>>();
        for (var entry : index.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

    private static <T> void indexCategory(IRecipeManager recipeManager, IRecipeCategory<T> category, Map<String, ArrayList<RMcpRecipeData>> index) {
        RecipeType<T> recipeType = category.getRecipeType();
        var title = categoryTitle(category);
        var recipeStream = recipeManager.createRecipeLookup(recipeType)
                .includeHidden()
                .get();
        for (var recipe : recipeStream.toList()) {
            IIngredientSupplier supplier = recipeManager.getRecipeIngredients(category, recipe);
            var outputs = ingredientSlots(supplier, RecipeIngredientRole.OUTPUT);
            var outputItemIds = outputItemIds(outputs);
            if (outputItemIds.isEmpty()) {
                continue;
            }
            var data = new RMcpRecipeData(
                    recipeId(category, recipe),
                    "jei",
                    recipeType.getUid().toString(),
                    title,
                    title,
                    recipe.getClass().getName(),
                    inputSlots(recipe, supplier),
                    outputs,
                    ingredientSlots(supplier, RecipeIngredientRole.CATALYST),
                    ingredientSlots(supplier, RecipeIngredientRole.RENDER_ONLY),
                    recipeExtra(category, recipeType, recipe)
            );
            for (var itemId : outputItemIds) {
                index.computeIfAbsent(itemId, unused -> new ArrayList<>()).add(data);
            }
        }
    }

    private static List<RMcpRecipeData.IngredientSlot> inputSlots(Object recipe, IIngredientSupplier supplier) {
        var fallbackSlots = ingredientSlots(supplier, RecipeIngredientRole.INPUT);
        if (recipe instanceof RecipeHolder<?> holder && holder.value() instanceof Recipe<?> minecraftRecipe) {
            var slots = ingredientSlots(minecraftRecipe.getIngredients());
            if (!slots.isEmpty()) {
                var merged = new ArrayList<>(slots);
                for (var fallbackSlot : fallbackSlots) {
                    if (!fallbackSlot.fluids().isEmpty()) {
                        merged.add(fallbackSlot);
                    }
                }
                return List.copyOf(merged);
            }
        }
        return fallbackSlots;
    }

    private static List<RMcpRecipeData.IngredientSlot> ingredientSlots(Iterable<Ingredient> ingredients) {
        var result = new ArrayList<RMcpRecipeData.IngredientSlot>();
        for (var ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            var slot = ingredientSlot(ingredient);
            if (!slot.items().isEmpty() || !slot.tags().isEmpty()) {
                result.add(slot);
            }
        }
        return List.copyOf(result);
    }

    private static RMcpRecipeData.IngredientSlot ingredientSlot(Ingredient ingredient) {
        var items = new ArrayList<RMcpRecipeData.Item>();
        var tags = new ArrayList<RMcpRecipeData.ItemTag>();
        if (!ingredient.isCustom()) {
            for (var value : ingredient.getValues()) {
                if (value instanceof Ingredient.TagValue tagValue) {
                    tags.add(itemTag(tagValue.tag()));
                } else if (value instanceof Ingredient.ItemValue itemValue) {
                    addItem(items, itemValue.item());
                }
            }
        }
        if (tags.isEmpty() && items.isEmpty()) {
            for (var itemStack : ingredient.getItems()) {
                if (items.size() >= MAX_DIRECT_ITEMS) {
                    break;
                }
                addItem(items, itemStack);
            }
        }
        return new RMcpRecipeData.IngredientSlot("input", List.copyOf(items), List.of(), List.copyOf(tags));
    }

    private static RMcpRecipeData.ItemTag itemTag(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        var examples = new ArrayList<RMcpRecipeData.Item>();
        int candidateCount = 0;
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            candidateCount++;
            if (examples.size() < MAX_TAG_EXAMPLES) {
                addItem(examples, new ItemStack(holder));
            }
        }
        return new RMcpRecipeData.ItemTag(tag.location().toString(), 1, candidateCount, List.copyOf(examples), "recipe");
    }

    private static List<RMcpRecipeData.IngredientSlot> ingredientSlots(IIngredientSupplier supplier, RecipeIngredientRole role) {
        var slots = supplier.getIngredients(role);
        var result = new ArrayList<RMcpRecipeData.IngredientSlot>();
        for (ITypedIngredient<?> slot : slots) {
            var items = new ArrayList<RMcpRecipeData.Item>();
            var fluids = new ArrayList<RMcpRecipeData.Fluid>();
            var ingredient = slot.getIngredient();
            if (ingredient instanceof ItemStack itemStack) {
                addItem(items, itemStack);
            } else if (ingredient instanceof FluidStack fluidStack) {
                addFluid(fluids, fluidStack);
            }
            if (!items.isEmpty() || !fluids.isEmpty()) {
                result.add(new RMcpRecipeData.IngredientSlot(roleName(role), List.copyOf(items), List.copyOf(fluids)));
            }
        }
        return List.copyOf(result);
    }

    private static void addItem(ArrayList<RMcpRecipeData.Item> items, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        items.add(new RMcpRecipeData.Item(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getItem().getDescriptionId(stack),
                stack.getCount(),
                null
        ));
    }

    private static void addFluid(ArrayList<RMcpRecipeData.Fluid> fluids, FluidStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        fluids.add(new RMcpRecipeData.Fluid(
                BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString(),
                stack.getHoverName().getString(),
                stack.getAmount()
        ));
    }

    private static List<String> outputItemIds(List<RMcpRecipeData.IngredientSlot> outputs) {
        var ids = new ArrayList<String>();
        for (var output : outputs) {
            for (var item : output.items()) {
                if (!ids.contains(item.id())) {
                    ids.add(item.id());
                }
            }
        }
        return ids;
    }

    private static <T> String recipeId(IRecipeCategory<T> category, T recipe) {
        ResourceLocation id = category.getRegistryName(recipe);
        return id == null ? null : id.toString();
    }

    private static String categoryTitle(IRecipeCategory<?> category) {
        Component title = category.getTitle();
        return title == null ? null : title.getString();
    }

    private static String roleName(RecipeIngredientRole role) {
        return role.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static Map<String, Object> recipeExtra(IRecipeCategory<?> category, RecipeType<?> recipeType, Object recipe) {
        var extra = new LinkedHashMap<String, Object>();
        extra.put("recipeClass", recipe.getClass().getName());
        extra.put("recipeTypeUid", recipeType.getUid().toString());
        extra.put("recipeTypeClass", recipeType.getRecipeClass().getName());
        extra.put("categoryClass", category.getClass().getName());
        return Map.copyOf(extra);
    }
}
