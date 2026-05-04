package calebxzhou.rdi.mc.client.rcmd;

import calebxzhou.rdi.mc.common.RDI;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdClientBridge;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdIngredientView;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdItemStackView;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdLangSnapshot;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdRecipeSnapshot;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdRecipeView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public final class RcmdClientBridge211 implements RcmdClientBridge {
    private final Minecraft minecraft;

    public RcmdClientBridge211(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public String name() {
        return minecraft.getUser().getName();
    }

    @Override
    public UUID playerId() {
        return minecraft.player == null ? NO_PLAYER_ID : minecraft.player.getUUID();
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    @Override
    public void sendFeedback(String message) {
        sendMessage(message);
    }

    @Override
    public void sendError(String message) {
        sendMessage("[rcmd] " + message);
    }

    @Override
    public Path gameDirectory() {
        return minecraft.gameDirectory.toPath();
    }

    @Override
    public void executeOnMainThread(Runnable task) {
        minecraft.execute(task);
    }

    @Override
    public void setFirmChunkVisible(boolean visible) {
        RDI.SHOW_FIRM_CHUNKS = visible;
    }

    @Override
    public RcmdRecipeSnapshot recipeExportSnapshot() {
        if (minecraft.level == null) {
            return null;
        }
        var registries = minecraft.level.registryAccess();
        var recipes = new ArrayList<RcmdRecipeView>();
        for (var holder : minecraft.level.getRecipeManager().getOrderedRecipes()) {
            var recipe = holder.value();
            var result = recipe.getResultItem(registries);
            recipes.add(recipeView(
                    holder.id().toString(),
                    recipe,
                    itemStackView(result)
            ));
        }
        return new RcmdRecipeSnapshot(List.copyOf(recipes));
    }

    @Override
    public RcmdLangSnapshot langExportSnapshot() {
        var resourceManager = minecraft.getResourceManager();
        var english = ClientLanguage.loadFrom(resourceManager, List.of("en_us"), false).getLanguageData();
        var chinese = ClientLanguage.loadFrom(resourceManager, List.of("zh_cn"), false).getLanguageData();
        return new RcmdLangSnapshot(english, chinese);
    }

    private void sendMessage(String message) {
        var component = Component.literal(message);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(component, false);
            return;
        }
        minecraft.gui.getChat().addMessage(component);
    }

    private static RcmdRecipeView recipeView(String id, Recipe<?> recipe, RcmdItemStackView result) {
        var type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString();
        var group = recipe.getGroup();
        var special = recipe.isSpecial();

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            return shapedRecipeView(id, type, group, special, result, shapedRecipe);
        }
        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            return new RcmdRecipeView(
                    id,
                    type,
                    group,
                    special,
                    result,
                    shapelessRecipe.category().getSerializedName(),
                    ingredientViews(shapelessRecipe.getIngredients()),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        if (recipe instanceof AbstractCookingRecipe cookingRecipe) {
            return new RcmdRecipeView(
                    id,
                    type,
                    group,
                    special,
                    result,
                    cookingRecipe.category().getSerializedName(),
                    ingredientViews(cookingRecipe.getIngredients()),
                    null,
                    null,
                    null,
                    cookingRecipe.getExperience(),
                    cookingRecipe.getCookingTime()
            );
        }
        if (recipe instanceof SingleItemRecipe singleItemRecipe) {
            return new RcmdRecipeView(
                    id,
                    type,
                    group,
                    special,
                    result,
                    null,
                    ingredientViews(singleItemRecipe.getIngredients()),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        return new RcmdRecipeView(id, type, group, special, result, null, null, null, null, null, null, null);
    }

    private static RcmdRecipeView shapedRecipeView(
            String id,
            String type,
            String group,
            boolean special,
            RcmdItemStackView result,
            ShapedRecipe recipe
    ) {
        var signatureToSymbol = new LinkedHashMap<String, String>();
        var key = new LinkedHashMap<String, RcmdIngredientView>();
        var pattern = new ArrayList<String>();
        var ingredients = recipe.getIngredients();
        int width = recipe.getWidth();
        int height = recipe.getHeight();

        for (int y = 0; y < height; y++) {
            var row = new StringBuilder();
            for (int x = 0; x < width; x++) {
                var ingredient = ingredients.get(y * width + x);
                if (ingredient.isEmpty()) {
                    row.append(' ');
                    continue;
                }
                var ingredientView = ingredientView(ingredient);
                var signature = ingredientView.items().toString();
                var symbol = signatureToSymbol.computeIfAbsent(signature, unused -> symbolFor(signatureToSymbol.size()));
                key.putIfAbsent(symbol, ingredientView);
                row.append(symbol);
            }
            pattern.add(row.toString());
        }

        return new RcmdRecipeView(
                id,
                type,
                group,
                special,
                result,
                recipe.category().getSerializedName(),
                null,
                key,
                pattern,
                recipe.showNotification(),
                null,
                null
        );
    }

    private static String symbolFor(int index) {
        var symbols = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        if (index >= symbols.length()) {
            throw new IllegalStateException("shaped配方ingredient种类过多，无法分配单字符key");
        }
        return String.valueOf(symbols.charAt(index));
    }

    private static List<RcmdIngredientView> ingredientViews(Iterable<Ingredient> ingredients) {
        var views = new ArrayList<RcmdIngredientView>();
        for (var ingredient : ingredients) {
            views.add(ingredientView(ingredient));
        }
        return List.copyOf(views);
    }

    private static RcmdIngredientView ingredientView(Ingredient ingredient) {
        var items = new ArrayList<RcmdItemStackView>();
        for (var itemStack : ingredient.getItems()) {
            items.add(itemStackView(itemStack));
        }
        return new RcmdIngredientView(List.copyOf(items));
    }

    private static RcmdItemStackView itemStackView(ItemStack itemStack) {
        var id = itemStack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
        var count = itemStack.isEmpty() ? 0 : itemStack.getCount();
        return new RcmdItemStackView(id, itemStack.getItem().getDescriptionId(itemStack), count);
    }

}
