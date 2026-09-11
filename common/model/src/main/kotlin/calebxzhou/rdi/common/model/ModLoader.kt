package calebxzhou.rdi.common.model

enum class ModLoader {
    forge,neoforge;
    companion object{
        fun from(name:String):ModLoader?{
            val normalized = name.trim().substringBefore('-')
            return entries.find { it.name.equals(normalized, true) }
        }
    }
    class Version(
        val loader: ModLoader,
        //version目录的名字
        val dirName: String,
        val installerUrl: String,
        val installerSha1: String
    ){
        //1.18.2-40.3.12
        val id get() = dirName.replace("${loader.name}-","")
        //40.3.12
        val ver get() = dirName.split("-").lastOrNull()?:""
        //1.16-
        val serverJarName get() ="${loader.name}-${id}.jar"
        //1.18+
        val serverArgsPath get() = { unix: Boolean ->
             when (loader) {
                neoforge -> "@libraries/net/neoforged/neoforge/"
                forge -> "@libraries/net/minecraftforge/forge/"
                 else -> ""
            } + "${id}/${if(unix) "unix" else "win"}_args.txt"
        }
    }
}
