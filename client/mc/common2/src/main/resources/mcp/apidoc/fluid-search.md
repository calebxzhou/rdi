### `GET /fluid-search?text=query`

Resolve a fluid name, Chinese name, English name, mod name, or partial ID into loaded fluid IDs.

Optional: `modId=namespace`, `limit=1..50`.

Returns ranked candidates with `id`, names, mod info, `score`, and `match`.
