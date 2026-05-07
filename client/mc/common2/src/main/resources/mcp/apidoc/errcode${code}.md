### `GET /errcode/{code}`

Use to get the exact description of one RMCP error code without reading the full error list.

Example:

```text
/errcode/no_player
```

Returns:

```json
{
  "code": "ok",
  "data": {
    "id": "no_player",
    "info": "the local player is not in a loaded world."
  }
}
```

Errors:

- `missing_errcode`: no code was provided after `/errcode/`.
- `bad_errcode`: the code segment is empty or invalid.
- `unknown_errcode`: the code is not defined in `RErrorCode.java`.
