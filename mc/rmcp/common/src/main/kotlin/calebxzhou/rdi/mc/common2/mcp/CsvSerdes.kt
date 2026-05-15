package calebxzhou.rdi.mc.common2.mcp

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.csv.Csv

@OptIn(ExperimentalSerializationApi::class)
val csv = Csv {
    hasHeaderRecord = true
    ignoreEmptyLines = true
}