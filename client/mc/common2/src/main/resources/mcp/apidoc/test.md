### `GET /test`

Use for a cheap health check and to know whether a Minecraft world is loaded.

Returns:

```json
{
  "code": "ok",
  "data": {
    "mod": "rdi",
    "mcVersion": "1.21.1",
    "side": "client",
    "worldLoaded": true
  }
}
```

Use this before planning game actions when you are not sure whether the client is connected to a world.
