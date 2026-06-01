package calebxzhou.rdi.common.service

object Mcmod {

    const val SERVER_PAGE = "https://play.mcmod.cn/sv20188037.html"
    const val searchUrl = "https://search.mcmod.cn/s?key="

/*
    suspend fun search(id: ResourceLocation, name: String): String? {
        val resp = calebxzhou.rdi.net.httpRequest {
            url(searchUrl + "${id.namespace} $name".urlEncoded)
            method = io.ktor.http.HttpMethod.Get
            Mcmod.headers.forEach { (name, value) -> header(name, value) }
        }.bodyAsText()
        val resultItems = Jsoup.parse(resp).select(".result-item")
        lgr.info { "搜到了${resultItems.size}个" }
        // Variable to store the first href
        var firstHref: String? = resultItems.firstOrNull()?.select(".head a")?.attr("href")

        for (item in resultItems) {
            // Extract the registration name
            val registrationNameElement = item.select(".body p").first()
            val registrationName = registrationNameElement?.text()?.trim()?.substringAfter("注册名: ")
            lgr.info { "注册名 $registrationName " }
            // Check if the registration name matches the target
            if (registrationName == id.toString()) {
                // Extract the href attribute from the <a> tag in the head section
                val href = item.select(".head a").attr("href")
                lgr.info { "找到了更合适的 $id $href" }
                return href
            }
        }
        return firstHref
    }

    fun getServerInfo() = ioScope.launch{
        calebxzhou.rdi.net.httpRequest {
            url(SERVER_PAGE)
            method = io.ktor.http.HttpMethod.Get
            Mcmod.headers.forEach { (name, value) -> header(name, value) }
        }
    }
    fun searchItemOpen(item: Item){
        ioScope.launch {
            search(item.id, item.chineseName.string)?.openAsUri()
                ?: search(item.id, item.englishName.string)?.openAsUri()
                ?: uiThread {  alertOk("没有在mc百科搜到${item.chineseName.string}") }
        }
    }
    fun onKeyPressIngame(){
        //先打开所看的 再打开所持的
        mc.player?.lookingAtBlock?.let { searchItemOpen(it.block.asItem()) }?:mc.player?.mainHandItem?.item?.let { searchItemOpen(it.asItem()) }
    }
    fun onKeyPressGui() {
        RJeiPlugin.jeiRuntime?.let { jei ->
            mc.screen?.let { screen ->
                val  screenItems :List<Optional<ItemStack>> = jei.screenHelper
                    .getClickableIngredientUnderMouse(screen, MouseX.toDouble(), MouseY.toDouble())
                    .map{it.typedIngredient}
                    .map(ITypedIngredient<*>::getItemStack).toList();
                val itemStack = jei.recipesGui.getIngredientUnderMouse(VanillaTypes.ITEM_STACK).getOrNull()?:
                jei.bookmarkOverlay.getIngredientUnderMouse(VanillaTypes.ITEM_STACK)?:
                    jei.ingredientListOverlay.getIngredientUnderMouse(VanillaTypes.ITEM_STACK)?: screenItems.firstOrNull()?.getOrNull()
                itemStack?.item?.let { item->

                    searchItemOpen(item)

                }
                *//*.findFirst().getOrNull()?.let {
                val ist: ItemStack = it.ingredientType.castIngredient(VanillaTypes.ITEM_STACK)

            } *//*
            }
        }
    }*/
}
