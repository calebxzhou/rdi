package calebxzhou.rdi.master.service.modpack

import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.master.DB
import calebxzhou.rdi.common.model.Task2
import com.mongodb.kotlin.client.coroutine.MongoCollection
import java.io.File

/** Shared persistence and test seams used by the focused modpack services. */
internal object ModpackServiceKernel {
    private val realDbcl = DB.getCollection<Modpack>("modpack")

    internal var testDbcl: MongoCollection<Modpack>? = null
    internal var testBuildVersionObserver: ((Modpack.Version) -> Unit)? = null
    internal var testServerModDownloadTaskFactory: ((List<Mod>) -> Task2)? = null
    internal var testServerModPreparer: (suspend (Mod) -> Unit)? = null
    internal var testClientModDownloadTaskFactory: ((List<Mod>) -> Task2)? = null
    internal var testGameLibsDirOverride: File? = null

    internal val dbcl: MongoCollection<Modpack>
        get() = testDbcl ?: realDbcl
}
