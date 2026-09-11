可以，测试要分三层写，不要只测 command happy path。

测试计划

1. Shared core unit tests: mc/firmsection
    - FirmSectionState 是最重要的纯逻辑层，优先覆盖这里。
    - cases：
        - 同一玩家 set 同一个 section -> ALREADY_PRESENT
        - 不同玩家 set 同一个 section -> OCCUPIED_BY_OTHER
        - 不同玩家 set 不同 section -> ADDED
        - unset 只删除当前玩家自己的 section
        - unset 后其他玩家可以重新 set
        - maxPerson 触发顺序：先 duplicate/occupied，再 personal limit
        - maxTotal 触发顺序：先 duplicate/occupied，再 total limit
        - loadPlayer 读取历史重复数据 -> first-owner-wins
        - autoSet=true 但没有 sections 时 player entry 保留
        - autoSet=false 且 sections 空时 player entry 清理
        - allSections() / totalCount() 在重复数据清理后计数一致
        - 不同 dimension 的相同 x/y/z 不冲突
        - 负坐标 section key 正常排序/查找

2. SavedData serialization tests
    - 目标是防止 NBT schema 改坏。
    - 如果版本依赖太重，先只测能轻量构造的版本；不能轻量跑的放到 runtime test。
    - cases：
        - save -> load 后 sections/autoSet 不丢
        - invalid UUID entry 被跳过
        - 历史重复 section load 后只保留 first owner
        - legacy:<dimensionId> 和 modern dimension string 不被改写
        - 空 data 保存/读取不崩

3. Service behavior tests
    - 这里重点不是 Minecraft 本体，而是确认调用顺序。
    - 最好抽一个很薄的 helper 或 fake wrapper，不大改架构。
    - cases：
        - set 返回 ADDED 时：
            - 先写入 firm state
            - 当前 chunk 被标记 dirty/unsaved
            - 立即调用真实 chunk save
            - 立即 flush firmsection saved data
            - 发送 sync packet

        - ALREADY_PRESENT / OCCUPIED_BY_OTHER / limit reached 时：
            - 不保存 chunk
            - 不 flush saved data
            - 不广播

        - unset 成功时广播，失败时不广播

4. Terrain cache policy tests
    - 这里覆盖这次 hotfix 的核心风险。
    - cases：
        - 真实 world chunk 存在时：永远优先真实 world，不读 cache
        - 真实 world chunk 不存在且不是 firm chunk：允许 cache fallback
        - 真实 world chunk 不存在但已经是 firm chunk：禁止 cache fallback
        - unsafe cache path 和 world path overlap 时禁用 cache
        - non-firm save 被拦截时仍写 terrain cache
        - firm save 不写 terrain cache，走真实保存
        - modern cache 写入不保存 entities，但保留 block entity 数据

5. Runtime/integration tests，按 MC 版本手动或专门 test host
    - 这些最好作为回归脚本/测试步骤，不一定全自动。
    - cases：
        - chunk 从 cache fallback 加载，玩家不修改，直接 set firmsection，重启后真实 world 有 chunk NBT
        - chunk 从 cache fallback 加载，玩家修改 block entity，再 set firmsection，重启后 block entity data 保留
        - set firmsection 后立刻 stop server，重启后 firm metadata 和 chunk NBT 都存在
        - 已经有真实 firm chunk 时，放一个旧 cache 文件，重启后不会被旧 cache 覆盖
        - 真实 world 缺 chunk 但 firm metadata 存在时，不读 cache，走 vanilla 行为
        - 非 firm chunk 仍然可以从 cache 恢复 terrain，确认没有破坏 cache 原用途

落地顺序

1. 先补 mc/firmsection pure unit tests，成本最低，能覆盖 ownership/limit/load 边界。
2. 再补 saved data serialization tests，防止历史重复数据和 autoSet 读写回归。
3. 再写 service/cache policy tests；如果 Minecraft 类太难 fake，就先把测试点集中到小 helper，不为了测试大改结构。
4. 最后补每个版本的 runtime checklist，特别是 1.7.10 / 1.20.1 / 1.21.1 各跑一遍 cache-loaded chunk -> set
   firm -> restart。
