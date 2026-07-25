package calebxzhou.rdi.client.ui

import androidx.compose.ui.graphics.Color

/**
 * Material Design Color Palette for Compose UI2.
 * @see https://m2.material.io/design/color/the-color-system.html#tools-for-picking-colors
 */
@SuppressWarnings("unused")
enum class MaterialColor(val color: Color) {
    // Red
    RED_50(parseColor("FFEBEE")),
    RED_100(parseColor("FFCDD2")),
    RED_200(parseColor("EF9A9A")),
    RED_300(parseColor("E57373")),
    RED_400(parseColor("EF5350")),
    RED_500(parseColor("F44336")),
    RED_600(parseColor("E53935")),
    RED_700(parseColor("D32F2F")),
    RED_800(parseColor("C62828")),
    RED_900(parseColor("B71C1C")),
    RED_A100(parseColor("FF8A80")),
    RED_A200(parseColor("FF5252")),
    RED_A400(parseColor("FF1744")),
    RED_A700(parseColor("D50000")),

    // Pink
    PINK_50(parseColor("FCE4EC")),
    PINK_100(parseColor("F8BBD0")),
    PINK_200(parseColor("F48FB1")),
    PINK_300(parseColor("F06292")),
    PINK_400(parseColor("EC407A")),
    PINK_500(parseColor("E91E63")),
    PINK_600(parseColor("D81B60")),
    PINK_700(parseColor("C2185B")),
    PINK_800(parseColor("AD1457")),
    PINK_900(parseColor("880E4F")),
    PINK_A100(parseColor("FF80AB")),
    PINK_A200(parseColor("FF4081")),
    PINK_A400(parseColor("F50057")),
    PINK_A700(parseColor("C51162")),

    // Purple
    PURPLE_50(parseColor("F3E5F5")),
    PURPLE_100(parseColor("E1BEE7")),
    PURPLE_200(parseColor("CE93D8")),
    PURPLE_300(parseColor("BA68C8")),
    PURPLE_400(parseColor("AB47BC")),
    PURPLE_500(parseColor("9C27B0")),
    PURPLE_600(parseColor("8E24AA")),
    PURPLE_700(parseColor("7B1FA2")),
    PURPLE_800(parseColor("6A1B9A")),
    PURPLE_900(parseColor("4A148C")),
    PURPLE_A100(parseColor("EA80FC")),
    PURPLE_A200(parseColor("E040FB")),
    PURPLE_A400(parseColor("D500F9")),
    PURPLE_A700(parseColor("AA00FF")),

    // Deep Purple
    DEEP_PURPLE_50(parseColor("EDE7F6")),
    DEEP_PURPLE_100(parseColor("D1C4E9")),
    DEEP_PURPLE_200(parseColor("B39DDB")),
    DEEP_PURPLE_300(parseColor("9575CD")),
    DEEP_PURPLE_400(parseColor("7E57C2")),
    DEEP_PURPLE_500(parseColor("673AB7")),
    DEEP_PURPLE_600(parseColor("5E35B1")),
    DEEP_PURPLE_700(parseColor("512DA8")),
    DEEP_PURPLE_800(parseColor("4527A0")),
    DEEP_PURPLE_900(parseColor("311B92")),
    DEEP_PURPLE_A100(parseColor("B388FF")),
    DEEP_PURPLE_A200(parseColor("7C4DFF")),
    DEEP_PURPLE_A400(parseColor("651FFF")),
    DEEP_PURPLE_A700(parseColor("6200EA")),

    // Indigo
    INDIGO_50(parseColor("E8EAF6")),
    INDIGO_100(parseColor("C5CAE9")),
    INDIGO_200(parseColor("9FA8DA")),
    INDIGO_300(parseColor("7986CB")),
    INDIGO_400(parseColor("5C6BC0")),
    INDIGO_500(parseColor("3F51B5")),
    INDIGO_600(parseColor("3949AB")),
    INDIGO_700(parseColor("303F9F")),
    INDIGO_800(parseColor("283593")),
    INDIGO_900(parseColor("1A237E")),
    INDIGO_A100(parseColor("8C9EFF")),
    INDIGO_A200(parseColor("536DFE")),
    INDIGO_A400(parseColor("3D5AFE")),
    INDIGO_A700(parseColor("304FFE")),

    // Blue
    BLUE_50(parseColor("E3F2FD")),
    BLUE_100(parseColor("BBDEFB")),
    BLUE_200(parseColor("90CAF9")),
    BLUE_300(parseColor("64B5F6")),
    BLUE_400(parseColor("42A5F5")),
    BLUE_500(parseColor("2196F3")),
    BLUE_600(parseColor("1E88E5")),
    BLUE_700(parseColor("1976D2")),
    BLUE_800(parseColor("1565C0")),
    BLUE_900(parseColor("0D47A1")),
    BLUE_A100(parseColor("82B1FF")),
    BLUE_A200(parseColor("448AFF")),
    BLUE_A400(parseColor("2979FF")),
    BLUE_A700(parseColor("2962FF")),

    // Light Blue
    LIGHT_BLUE_50(parseColor("E1F5FE")),
    LIGHT_BLUE_100(parseColor("B3E5FC")),
    LIGHT_BLUE_200(parseColor("81D4FA")),
    LIGHT_BLUE_300(parseColor("4FC3F7")),
    LIGHT_BLUE_400(parseColor("29B6F6")),
    LIGHT_BLUE_500(parseColor("03A9F4")),
    LIGHT_BLUE_600(parseColor("039BE5")),
    LIGHT_BLUE_700(parseColor("0288D1")),
    LIGHT_BLUE_800(parseColor("0277BD")),
    LIGHT_BLUE_900(parseColor("01579B")),
    LIGHT_BLUE_A100(parseColor("80D8FF")),
    LIGHT_BLUE_A200(parseColor("40C4FF")),
    LIGHT_BLUE_A400(parseColor("00B0FF")),
    LIGHT_BLUE_A700(parseColor("0091EA")),

    // Cyan
    CYAN_50(parseColor("E0F7FA")),
    CYAN_100(parseColor("B2EBF2")),
    CYAN_200(parseColor("80DEEA")),
    CYAN_300(parseColor("4DD0E1")),
    CYAN_400(parseColor("26C6DA")),
    CYAN_500(parseColor("00BCD4")),
    CYAN_600(parseColor("00ACC1")),
    CYAN_700(parseColor("0097A7")),
    CYAN_800(parseColor("00838F")),
    CYAN_900(parseColor("006064")),
    CYAN_A100(parseColor("84FFFF")),
    CYAN_A200(parseColor("18FFFF")),
    CYAN_A400(parseColor("00E5FF")),
    CYAN_A700(parseColor("00B8D4")),

    // Teal
    TEAL_50(parseColor("E0F2F1")),
    TEAL_100(parseColor("B2DFDB")),
    TEAL_200(parseColor("80CBC4")),
    TEAL_300(parseColor("4DB6AC")),
    TEAL_400(parseColor("26A69A")),
    TEAL_500(parseColor("009688")),
    TEAL_600(parseColor("00897B")),
    TEAL_700(parseColor("00796B")),
    TEAL_800(parseColor("00695C")),
    TEAL_900(parseColor("004D40")),
    TEAL_A100(parseColor("A7FFEB")),
    TEAL_A200(parseColor("64FFDA")),
    TEAL_A400(parseColor("1DE9B6")),
    TEAL_A700(parseColor("00BFA5")),

    // Green
    GREEN_50(parseColor("E8F5E9")),
    GREEN_100(parseColor("C8E6C9")),
    GREEN_200(parseColor("A5D6A7")),
    GREEN_300(parseColor("81C784")),
    GREEN_400(parseColor("66BB6A")),
    GREEN_500(parseColor("4CAF50")),
    GREEN_600(parseColor("43A047")),
    GREEN_700(parseColor("388E3C")),
    GREEN_800(parseColor("2E7D32")),
    GREEN_900(parseColor("1B5E20")),
    GREEN_A100(parseColor("B9F6CA")),
    GREEN_A200(parseColor("69F0AE")),
    GREEN_A400(parseColor("00E676")),
    GREEN_A700(parseColor("00C853")),

    // Light Green
    LIGHT_GREEN_50(parseColor("F1F8E9")),
    LIGHT_GREEN_100(parseColor("DCEDC8")),
    LIGHT_GREEN_200(parseColor("C5E1A5")),
    LIGHT_GREEN_300(parseColor("AED581")),
    LIGHT_GREEN_400(parseColor("9CCC65")),
    LIGHT_GREEN_500(parseColor("8BC34A")),
    LIGHT_GREEN_600(parseColor("7CB342")),
    LIGHT_GREEN_700(parseColor("689F38")),
    LIGHT_GREEN_800(parseColor("558B2F")),
    LIGHT_GREEN_900(parseColor("33691E")),
    LIGHT_GREEN_A100(parseColor("CCFF90")),
    LIGHT_GREEN_A200(parseColor("B2FF59")),
    LIGHT_GREEN_A400(parseColor("76FF03")),
    LIGHT_GREEN_A700(parseColor("64DD17")),

    // Lime
    LIME_50(parseColor("F9FBE7")),
    LIME_100(parseColor("F0F4C3")),
    LIME_200(parseColor("E6EE9C")),
    LIME_300(parseColor("DCE775")),
    LIME_400(parseColor("D4E157")),
    LIME_500(parseColor("CDDC39")),
    LIME_600(parseColor("C0CA33")),
    LIME_700(parseColor("AFB42B")),
    LIME_800(parseColor("9E9D24")),
    LIME_900(parseColor("827717")),
    LIME_A100(parseColor("F4FF81")),
    LIME_A200(parseColor("EEFF41")),
    LIME_A400(parseColor("C6FF00")),
    LIME_A700(parseColor("AEEA00")),

    // Yellow
    YELLOW_50(parseColor("FFFDE7")),
    YELLOW_100(parseColor("FFF9C4")),
    YELLOW_200(parseColor("FFF59D")),
    YELLOW_300(parseColor("FFF176")),
    YELLOW_400(parseColor("FFEE58")),
    YELLOW_500(parseColor("FFEB3B")),
    YELLOW_600(parseColor("FDD835")),
    YELLOW_700(parseColor("FBC02D")),
    YELLOW_800(parseColor("F9A825")),
    YELLOW_900(parseColor("F57F17")),
    YELLOW_A100(parseColor("FFFF8D")),
    YELLOW_A200(parseColor("FFFF00")),
    YELLOW_A400(parseColor("FFEA00")),
    YELLOW_A700(parseColor("FFD600")),

    // Amber
    AMBER_50(parseColor("FFF8E1")),
    AMBER_100(parseColor("FFECB3")),
    AMBER_200(parseColor("FFE082")),
    AMBER_300(parseColor("FFD54F")),
    AMBER_400(parseColor("FFCA28")),
    AMBER_500(parseColor("FFC107")),
    AMBER_600(parseColor("FFB300")),
    AMBER_700(parseColor("FFA000")),
    AMBER_800(parseColor("FF8F00")),
    AMBER_900(parseColor("FF6F00")),
    AMBER_A100(parseColor("FFE57F")),
    AMBER_A200(parseColor("FFD740")),
    AMBER_A400(parseColor("FFC400")),
    AMBER_A700(parseColor("FFAB00")),

    // Orange
    ORANGE_50(parseColor("FFF3E0")),
    ORANGE_100(parseColor("FFE0B2")),
    ORANGE_200(parseColor("FFCC80")),
    ORANGE_300(parseColor("FFB74D")),
    ORANGE_400(parseColor("FFA726")),
    ORANGE_500(parseColor("FF9800")),
    ORANGE_600(parseColor("FB8C00")),
    ORANGE_700(parseColor("F57C00")),
    ORANGE_800(parseColor("EF6C00")),
    ORANGE_900(parseColor("E65100")),
    ORANGE_A100(parseColor("FFD180")),
    ORANGE_A200(parseColor("FFAB40")),
    ORANGE_A400(parseColor("FF9100")),
    ORANGE_A700(parseColor("FF6D00")),

    // Deep Orange
    DEEP_ORANGE_50(parseColor("FBE9E7")),
    DEEP_ORANGE_100(parseColor("FFCCBC")),
    DEEP_ORANGE_200(parseColor("FFAB91")),
    DEEP_ORANGE_300(parseColor("FF8A65")),
    DEEP_ORANGE_400(parseColor("FF7043")),
    DEEP_ORANGE_500(parseColor("FF5722")),
    DEEP_ORANGE_600(parseColor("F4511E")),
    DEEP_ORANGE_700(parseColor("E64A19")),
    DEEP_ORANGE_800(parseColor("D84315")),
    DEEP_ORANGE_900(parseColor("BF360C")),
    DEEP_ORANGE_A100(parseColor("FF9E80")),
    DEEP_ORANGE_A200(parseColor("FF6E40")),
    DEEP_ORANGE_A400(parseColor("FF3D00")),
    DEEP_ORANGE_A700(parseColor("DD2C00")),

    // Brown
    BROWN_50(parseColor("EFEBE9")),
    BROWN_100(parseColor("D7CCC8")),
    BROWN_200(parseColor("BCAAA4")),
    BROWN_300(parseColor("A1887F")),
    BROWN_400(parseColor("8D6E63")),
    BROWN_500(parseColor("795548")),
    BROWN_600(parseColor("6D4C41")),
    BROWN_700(parseColor("5D4037")),
    BROWN_800(parseColor("4E342E")),
    BROWN_900(parseColor("3E2723")),

    // Gray
    GRAY_50(parseColor("FAFAFA")),
    GRAY_100(parseColor("F5F5F5")),
    GRAY_200(parseColor("EEEEEE")),
    GRAY_300(parseColor("E0E0E0")),
    GRAY_400(parseColor("BDBDBD")),
    GRAY_500(parseColor("9E9E9E")),
    GRAY_600(parseColor("757575")),
    GRAY_700(parseColor("616161")),
    GRAY_800(parseColor("424242")),
    GRAY_900(parseColor("212121")),

    // Blue Gray
    BLUE_GRAY_50(parseColor("ECEFF1")),
    BLUE_GRAY_100(parseColor("CFD8DC")),
    BLUE_GRAY_200(parseColor("B0BEC5")),
    BLUE_GRAY_300(parseColor("90A4AE")),
    BLUE_GRAY_400(parseColor("78909C")),
    BLUE_GRAY_500(parseColor("607D8B")),
    BLUE_GRAY_600(parseColor("546E7A")),
    BLUE_GRAY_700(parseColor("455A64")),
    BLUE_GRAY_800(parseColor("37474F")),
    BLUE_GRAY_900(parseColor("263238")),

    // Black & White
    BLACK(parseColor("000000")),
    WHITE(parseColor("FFFFFF"));

    val isDarkColor: Boolean
        get() {
            val luminance = (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue)
            return luminance < 0.5f
        }

    companion object {
        fun rgb(red: Int, green: Int, blue: Int): Color {
            val value = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            return Color(value)
        }

        fun argb(alpha: Int, red: Int, green: Int, blue: Int): Color {
            val value = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            return Color(value)
        }


        val PRIMARY_COLORS = mapOf(
            "RED" to RED_500,
            "PINK" to PINK_500,
            "PURPLE" to PURPLE_500,
            "DEEP_PURPLE" to DEEP_PURPLE_500,
            "INDIGO" to INDIGO_500,
            "BLUE" to BLUE_500,
            "LIGHT_BLUE" to LIGHT_BLUE_500,
            "CYAN" to CYAN_500,
            "TEAL" to TEAL_500,
            "GREEN" to GREEN_500,
            "LIGHT_GREEN" to LIGHT_GREEN_500,
            "LIME" to LIME_500,
            "YELLOW" to YELLOW_500,
            "AMBER" to AMBER_500,
            "ORANGE" to ORANGE_500,
            "DEEP_ORANGE" to DEEP_ORANGE_500,
            "BROWN" to BROWN_500,
            "GRAY" to GRAY_500,
            "BLUE_GRAY" to BLUE_GRAY_500
        )
    }
}

fun parseColor(colorString: String): Color = parseColorInternal(colorString)
private fun parseColorInternal(hex: String): Color {
    val argb = when (hex.length) {
        6 -> "FF$hex"
        8 -> hex
        else -> throw IllegalArgumentException("Invalid color: $hex")
    }
    val intValue = argb.toLong(16).toInt()
    return Color(intValue)
}
