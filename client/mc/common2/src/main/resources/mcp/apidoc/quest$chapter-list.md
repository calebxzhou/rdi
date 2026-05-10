### `GET /quest/chapter-list`

Use to read all FTB Quests chapters known by the current client, with current-player progress and visibility summary.

Returns `RQuestChapterList`:

```json
{
  "code": "ok",
  "data": {
    "total": 2,
    "visible": 1,
    "chapters": [
      {
        "id": "0123456789ABCDEF",
        "title": "Getting Started",
        "groupId": "1111111111111111",
        "groupTitle": "Main",
        "visible": true,
        "started": true,
        "completed": false,
        "progress": 35,
        "questCount": 12,
        "visibleQuestCount": 8,
        "startableQuestCount": 3,
        "completedQuestCount": 4
      }
    ]
  }
}
```

Use this endpoint as a lightweight quest-book index. It intentionally does not include every quest description, task, or reward. Call a future quest-detail endpoint when exact quest contents are needed.

Common errors:

- `no_player`: the local player is not in a loaded world.
- `quest_data_not_loaded`: FTB Quests has not synced quest data to the client yet.
