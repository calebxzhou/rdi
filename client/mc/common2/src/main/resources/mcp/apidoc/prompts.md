### `GET /prompts`

Use to read the compact RMCP API summary. This endpoint intentionally returns a small prompt instead of the full API manual.

Returns Markdown:

```http
HTTP/1.1 200 OK
Content-Type: text/markdown; charset=utf-8

<summary markdown>
```

Use the summary to choose the right endpoint. For detailed docs, call `/apidoc/{file}` using the file name listed in the summary.
