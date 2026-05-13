### `GET /tag-search?text=query`

Resolve a tag name or partial tag ID into loaded item/block/fluid/entity type tags. Result IDs are prefixed with the registry, such as `item#minecraft:logs` or `block#minecraft:mineable/pickaxe`.

Optional: `modId=namespace`, `limit=1..50`.

Use this before tag-based inventory/resource reasoning when the user gives a broad category instead of an exact ID.
