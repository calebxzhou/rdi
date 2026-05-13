### `GET /quest/detail/{id}`

Default response is compact. It includes quest state, rules, task/reward summaries, and `detail` refs. Use `GET /quest/detail/detail?ref=id` for Markdown, or `GET /quest/detail/detail.json?ref=id` for the exact quest JSON. `.detail.md` is a compatibility alias for Markdown. Use `view=full` only for debugging or extraction.

Returns full LLM-readable detail for one FTB Quests quest.

Use quest ids from `GET /quest/reachable` or `GET /quest/chapter/{id}`.

Returns `RQuest`:

```json
{
  "code": "ok",
  "data": {
    "id": "1A2B3C4D",
    "title": "First Steps",
    "subtitle": "",
    "chapterId": "01020304",
    "chapterTitle": "Getting Started",
    "groupId": "00000000",
    "groupTitle": "Quests",
    "position": {
      "x": 1.5,
      "y": 2.0
    },
    "state": {
      "visible": true,
      "started": false,
      "completed": false,
      "startable": true,
      "progress": 0,
      "cannotStartReason": null
    },
    "rules": {
      "progressionMode": "linear",
      "dependencyRequirement": "all_completed",
      "minRequiredDependencies": 0,
      "optional": false,
      "repeatable": false,
      "hideDetailsUntilStartable": false,
      "requireSequentialTasks": false
    },
    "description": [
      "Quest description line"
    ],
    "dependencies": [
      {
        "id": "00000001",
        "type": "quest",
        "title": "Previous Quest",
        "visible": true,
        "started": true,
        "completed": true
      }
    ],
    "tasks": [
      {
        "id": "00000002",
        "type": "ftbquests:item",
        "title": "Item Task",
        "completed": false,
        "progress": 0,
        "maxProgress": 1
      }
    ],
    "rewards": [
      {
        "id": "00000003",
        "type": "ftbquests:item",
        "title": "Item Reward",
        "claimed": false
      }
    ]
  }
}
```

`cannotStartReason` is `null` when `state.startable=true`. Otherwise it can be:

- `completed`
- `not_visible`
- `dependencies_or_repeat_blocked`

Common errors:

- `no_player`: no world is loaded or no current player exists.
- `quest_data_not_loaded`: FTB Quests client data has not been received yet.
- `missing_quest_id`: no quest id was included after `/quest/detail/`.
- `bad_quest_id`: the path id is not a valid FTB Quests hex id.
- `no_quest`: no quest has that id.
