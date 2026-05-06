package calebxzhou.rdi.mc.client.rcmd;

import calebxzhou.rdi.mc.common2.rcmd.client.RcmdIngredientView;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdItemStackView;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdRecipeView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class RcmdRecipeCodec211 {
    private RcmdRecipeCodec211() {
    }

    public static RcmdRecipeView recipeView(String id, Recipe<?> recipe, ItemStack result) {
        return recipeView(id, recipe, itemStackView(result));
    }

    public static String itemId(ItemStack itemStack) {
        return itemStack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
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
                    null
            );
        }
        return new RcmdRecipeView(id, type, group, special, result, null, null, null, null, null, null);
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
        var count = itemStack.isEmpty() ? 0 : itemStack.getCount();
        return new RcmdItemStackView(itemId(itemStack), itemStack.getItem().getDescriptionId(itemStack), count);
    }
}
