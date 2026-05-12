package calebxzhou.rdi.mc.common2.rcmd.client;

import java.util.List;

public record RcmdIngredientView(List<RcmdItemStackView> items, List<ItemTag> tags) {
    public RcmdIngredientView(List<RcmdItemStackView> items) {
        this(items, List.of());
    }

    public record ItemTag(String id, int count, int candidateCount, List<RcmdItemStackView> examples) {
    }

    public RcmdIngredientView(List<RcmdItemStackView> items, List<ItemTag> tags) {
        this.items = items == null ? List.of() : items;
        this.tags = tags == null ? List.of() : tags;
    }

    public boolean isEmpty() {
        return items.isEmpty() && tags.isEmpty();
    }
}
