### `POST /sign/text`

Use to change text on a sign in the current player dimension. This is a server-side action API: it edits the sign block entity directly and does not open the sign GUI.

Request body:

```json
{
  "pos": { "x": 10, "y": 64, "z": -20 },
  "side": "front",
  "text": "{gold}**Base**\n{white}Iron: `32`\nFood: {yellow:low}\nGo north",
  "dryRun": false
}
```

`pos` is a block position in the current player dimension. `side` is optional and may be `front`, `back`, or `auto`; default is `front`. `auto` chooses the side the current player is facing.

`text` is Markdown and may contain at most 4 lines. Supported Markdown is intentionally small: `**bold**`, `*italic*`, `~~strike~~`, inline code, headings, quote/list prefixes, links with `http/https`, inline color spans like `{yellow:text}`, and color switches like `{gold}Title`. A color switch applies until another color switch or the end of that line; use `{reset}` to return to the sign's default text color. Colors use Minecraft `ChatFormatting` names only: `black`, `dark_blue`, `dark_green`, `dark_aqua`, `dark_red`, `dark_purple`, `gold`, `gray`, `dark_gray`, `blue`, `green`, `aqua`, `red`, `light_purple`, `yellow`, `white`.

Returns:

```json
{
  "code": "ok",
  "data": {
    "dryRun": false,
    "pos": { "x": 10, "y": 64, "z": -20 },
    "side": "front",
    "changed": true,
    "lines": ["Base", "Iron: 32", "Food: low", "Go north"]
  }
}
```

Use `dryRun=true` to preview the parsed plain text lines without changing the sign. If the response is successful and `changed=true`, re-read `/blockentity?pos=dim,x,y,z` only when exact sign NBT verification is needed.

Common errors:

- `bad_sign_text`: text is missing, empty, or has more than 4 lines.
- `bad_sign_side`: side is not `front`, `back`, or `auto`.
- `not_sign`: target block entity is not a sign.
- `sign_waxed`: target sign is waxed and cannot be edited.
- `chunk_not_loaded`: target chunk is not loaded.
- `too_far`: sign is more than 32 blocks away.
