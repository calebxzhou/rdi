### `GET /entity-type-search?text=query`

Resolve a player-visible mob/entity name, Chinese name, English name, mod name, or partial ID into loaded entity type IDs.

Optional: `modId=namespace`, `limit=1..50`.

Returns ranked candidates with `id`, names, mod info, `score`, and `match`.
