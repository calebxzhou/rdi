### `GET /langkey-search?text=query`

Use to discover possible language keys from English or Chinese text. Chinese matching is prioritized over English. Fuzzy matching is supported. At most 10 candidates are returned.

Example:

```text
/langkey-search?text=金合欢牌子
```

Returns:

```json
{
  "code": "ok",
  "data": {
    "text": "金合欢牌子",
    "results": [
      {
        "langkey": "block.minecraft.acacia_sign",
        "englishName": "Acacia Sign",
        "chineseName": "金合欢木告示牌"
      }
    ]
  }
}
```

