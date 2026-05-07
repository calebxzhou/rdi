### `GET /apidoc/{file}`

Use to read the detailed Markdown document for one RMCP endpoint without loading the full API manual.

File names are endpoint paths without the leading slash, with `/` replaced by `$`.

Examples:

```text
/apidoc/inventory$move.md
/apidoc/place$batch.md
```

Returns Markdown:

```http
HTTP/1.1 200 OK
Content-Type: text/markdown; charset=utf-8

<endpoint markdown>
```

Errors:

- `missing_apidoc`: no file name was provided after `/apidoc/`.
- `bad_apidoc`: the file name is empty, unsafe, or does not end with `.md`.
- `unknown_apidoc`: no matching endpoint doc file exists.
