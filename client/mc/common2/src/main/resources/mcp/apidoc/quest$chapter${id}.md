### `GET /quest/chapter/{id}`

Use after `GET /quest/chapter-list` to read all quests directly inside one FTB Quests chapter.

Returns `RQuestChapter`:

```json
{
  "code": "ok",
  "data": {
    "id": "0123456789ABCDEF",
    "title": "Getting Started",
    "groupId": "1111111111111111",
    "groupTitle": "Main",
    "visible": true,
    "started": true,
    "completed": false,
    "progress": 35,
    "questCount": 2,
    "quests": [
      {
        "id": "2222222222222222",
        "title": "Collect Wood",
        "subtitle": "Start with logs",
        "x": 1.0,
        "y": 2.0,
        "visible": true,
        "started": false,
        "completed": false,
        "startable": true,
        "progress": 0,
        "taskCount": 1,
        "rewardCount": 1,
        "dependencyCount": 0,
        "dependencyIds": []
      }
    ]
  }
}
```

This endpoint is a brief chapter quest index. It does not include full quest descriptions, task details, reward details, or item requirements.

Common errors:

- `no_player`: the local player is not in a loaded world.
- `quest_data_not_loaded`: FTB Quests has not synced quest data to the client yet.
- `missing_quest_chapter_id`: no chapter id was included after `/quest/chapter/`.
- `bad_quest_chapter_id`: the path id is not a valid FTB Quests hex id.
- `no_quest_chapter`: no chapter has that id.
