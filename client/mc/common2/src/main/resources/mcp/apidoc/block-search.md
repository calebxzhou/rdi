### `GET /block-search?text=query`

Resolve a player-visible block name, Chinese name, English name, tooltip word, mod name, or partial ID into loaded block IDs. Use this before block actions when the user did not provide an exact `namespace:path`.

Optional: `modId=namespace`, `limit=1..50`.

Returns ranked candidates with `id`, names, mod info, `score`, and `match`.
