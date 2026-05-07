### `GET /screenshot`
If you are not a multimodality AI you should not invoke this
Use to capture the current Minecraft client frame. This endpoint returns raw PNG bytes, not JSON, on success.
If screenshot capture fails or times out, the response uses the normal JSON error shape.
