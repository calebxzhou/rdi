### `GET /langkey?key=language.key`

Use when you already know a language key and need English/Chinese display text.

Returns:

```json
{
  "code": "ok",
  "data": {
    "en": "Acacia Sign",
    "cn": "金合欢木告示牌"
  }
}
```

If only one language exists, the other field may be an empty string.

