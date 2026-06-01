原因基本确定：ResourceScreen 现在用 client/ui/src/commonMain/kotlin/calebxzhou/rdi/client/ui/screen/
ResourceScreen.kt:80 按 category 只组合当前 tab。切到别的 tab 后，旧 pane 会离开 composition，所以 pane
内部大量 remember 状态会丢，比如：

- RemoteModScreen 的搜索结果、loading、offset、totalHits 等在 client/ui/src/commonMain/kotlin/calebxzhou/
  rdi/client/ui/screen/RemoteModScreen.kt:83

- RemoteModpackScreen 的列表、筛选、分页状态在 client/ui/src/commonMain/kotlin/calebxzhou/rdi/client/ui/
  screen/RemoteModpackScreen.kt:89

- InstalledResourcePane 的选中包、配置编辑器、弹窗状态在 client/ui/src/commonMain/kotlin/calebxzhou/rdi/
  client/ui/screen/InstalledResourcePane.kt:94

推荐方案：KeepAlive Tab Host
把 AnimatedContent(targetState = category) 改成一个 keep-alive 容器：所有 tab pane 都保留在 composition
里，只切换可见性。

实现思路：

1. 在 ResourceScreen 里维护已打开过的 tabs：

   val visitedTabs = remember { mutableStateListOf(initialCategory) }
   LaunchedEffect(category) {
   if (category !in visitedTabs) visitedTabs += category
   }

2. 用 Box(Modifier.fillMaxSize()) 渲染 visitedTabs，每个 tab 一个 pane。
3. 当前 tab：
    - alpha(1f)
    - zIndex(1f)
    - 可接收点击

4. 非当前 tab：
    - alpha(0f)
    - zIndex(0f)
    - 禁止 pointer input，避免透明 pane 抢点击
    - 仍保留 composition，所以内部 remember、滚动、已加载列表都不丢

5. 把现在 when (activeCategory) 抽成一个小函数：

   @Composable
   private fun ResourceTabPane(tab: ResourceTab, ...)

   这样 ResourceScreen 不会变成一大坨重复代码。

动画处理
如果还要保留切 tab 动画，不继续用 AnimatedContent 直接包 pane，因为它会 dispose 旧内容。可以只给每个
keep-alive pane 加：

animateFloatAsState(if (tab == category) 1f else 0f)

做淡入淡出。横向 slide 可以后面再补，第一步先保证状态不丢。

不推荐方案
只用 rememberSaveableStateHolder 不够。它只能保住 rememberSaveable，但这些 pane 里很多关键数据是普通
remember，例如网络结果列表、loading、offset、选中对象等，仍然会被重建。除非把每个 pane 全部改成外部 state
holder，但改动会大很多。

最小落地范围
第一步只改 client/ui/src/commonMain/kotlin/calebxzhou/rdi/client/ui/screen/ResourceScreen.kt:50：

- 移除 AnimatedContent
- 新增 visitedTabs
- 新增 ResourceTabPane
- 用 keep-alive Box 保留已访问 pane

不碰 RemoteModScreen、RemoteModpackScreen、InstalledResourcePane 内部状态结构。