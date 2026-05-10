### `GET /quest/reachable`

Returns every FTB Quests quest the current player can start working on now.

A quest is included only when it is visible to the player's team, not completed, and FTB Quests says its tasks can be started.

Returns `RReachableQuestList`:

```json
{
  "code": "ok",
  "data": {
    "total": 2,
    "quests": [
      {
        "id": "1A2B3C4D",
        "title": "First Steps",
        "subtitle": "",
        "chapterId": "01020304",
        "chapterTitle": "Getting Started",
        "groupId": "00000000",
        "groupTitle": "Quests",
        "x": 1.5,
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

Common errors:

- `no_player`: no world is loaded or no current player exists.
- `quest_data_not_loaded`: FTB Quests client data has not been received yet.
