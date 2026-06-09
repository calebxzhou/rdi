package calebxzhou.rdi.mc.rcmd.home

interface HomePlayer {
    fun currentLocation(): HomeLocation

    fun loadHomes(): MutableMap<String, HomeLocation>

    fun saveHomes(homes: MutableMap<String, HomeLocation>)

    fun teleportTo(location: HomeLocation): HomeResult
}
