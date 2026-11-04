package tv.cleanyour.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import tv.cleanyour.core.flows.ForkWidgets
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.Module
import tv.cleanyour.core.model.ModuleVersion
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PermissionStatement
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Tier
import tv.cleanyour.core.registry.ArtifactFetcher
import tv.cleanyour.core.registry.FetchFailure
import tv.cleanyour.core.registry.FetchResult
import tv.cleanyour.core.registry.IndexState
import tv.cleanyour.core.registry.Registry
import tv.cleanyour.core.store.BlobStore
import java.io.FileNotFoundException

// Fork (Clean Your TV RU): the TV add-ons this app carries (tv-addons/, built and signed by
// scripts/fork/bundle-addons.sh into android/platform's assets/fork-addons/, see scripts/fork/ADDONS-CONTRACT.md).
// They show in Cards and modules like the index's modules and install the same way (ModulesMachine), and the
// Widgets page puts the Widgets add-on on the TV with a send. No minisign index: the trust is the signer pin below,
// checked here against index.json and again on the APK itself by the module install's inspector.
class ForkAddons(private val context: Context) {
    val modules: List<Module> by lazy { runCatching { load() }.onFailure { CytLog.w("fork add-ons not loaded", it) }.getOrDefault(emptyList()) }

    private fun load(): List<Module> {
        val text = try {
            context.assets.open("$DIR/index.json").use { it.readBytes().decodeToString() }
        } catch (e: FileNotFoundException) {
            return emptyList()   // a build without add-ons
        }
        val root = Json.parseToJsonElement(text).jsonObject
        require(root["spec"]?.jsonPrimitive?.content == "1") { "fork-addons index spec" }
        return root["addons"]!!.jsonArray.mapNotNull { e -> module(e.jsonObject) }
    }

    private fun module(o: JsonObject): Module? {
        fun s(k: String) = o[k]!!.jsonPrimitive.content
        val pkg = s("pkg")
        val signer = s("signer_sha256").lowercase()
        if (signer != SIGNER) { CytLog.w("fork add-on $pkg has another signer"); return null }
        if (pkg != ForkWidgets.PKG) return null   // the one add-on this app knows how to use
        val file = s("file")
        require(FILE_RE.matches(file)) { "fork add-on file name" }
        val res = context.resources
        val ref = ArtifactRef(SCHEME + file, o["size"]!!.jsonPrimitive.long, Sha256(s("sha256").lowercase()), Sha256(signer))
        val version = ModuleVersion(o["versionCode"]!!.jsonPrimitive.long, s("versionName"), "", ref,
            listOf(PermissionStatement("android.permission.INTERNET", res.getString(R.string.fork_widgets_internet))),
            res.getString(R.string.fork_widgets_network), 26, emptyList(), emptyList())
        return Module(ForkWidgets.MODULE_ID, res.getString(R.string.fork_widgets_name), res.getString(R.string.fork_widgets_one_line),
            res.getString(R.string.fork_widgets_description), PackageName(pkg), "app", Tier.VERIFIED, "Clean Your TV RU",
            "https://github.com/AlexeyGalkin/cleanyourtv-app/tree/ru/tv-addons/widgets", "MIT", "#E10600", null, listOf(version))
    }

    fun artifact(ref: ArtifactRef): String? =
        if (ref.url.startsWith(SCHEME) && modules.any { m -> m.versions.any { it.artifact == ref } }) ref.url.removePrefix(SCHEME) else null

    // The bundled APK into the BlobStore, checked by size and sha256 on the way in.
    suspend fun blob(blobs: BlobStore, ref: ArtifactRef, file: String): FetchResult = try {
        blobs.get(ref.sha256)?.takeIf { it.size == ref.size }?.let { FetchResult.Ok(it) }
            ?: context.assets.open("$DIR/$file").use { input -> FetchResult.Ok(blobs.put(ref.sha256, input, ref.size)) }
    } catch (e: Exception) {
        CytLog.w("fork add-on $file did not copy", e)
        FetchResult.Failed(FetchFailure.HASH_MISMATCH, null)
    }

    companion object {
        const val DIR = "fork-addons"
        const val SCHEME = "bundled-fork:"
        // The fork's release key (the app and its add-ons): apksigner's "Signer #1 certificate SHA-256 digest".
        const val SIGNER = "c02a76c8034b6ecd835dd88161e751fe5689c40b5f42d4715768769580a7051c"
        private val FILE_RE = Regex("^[a-z0-9.]{1,64}\\.apk$")
    }
}

// The index's modules with the fork's add-ons in front. An index not read yet stays Empty, so Cards and modules
// still reads it first; module() finds the fork's at once.
class ForkRegistry(private val inner: Registry, private val addons: ForkAddons, scope: CoroutineScope) : Registry {
    override val index: StateFlow<IndexState> = inner.index.map(::merge).stateIn(scope, SharingStarted.Eagerly, merge(inner.index.value))

    override suspend fun refresh(): IndexState = merge(inner.refresh())

    override fun module(id: String): Module? = addons.modules.firstOrNull { it.id == id } ?: inner.module(id)

    private fun merge(s: IndexState): IndexState {
        val fork = addons.modules
        if (fork.isEmpty()) return s
        return when (s) {
            IndexState.Empty -> s
            is IndexState.Ready -> s.copy(index = s.index.copy(modules = fork + s.index.modules.filter { m -> fork.none { it.id == m.id } }))
            is IndexState.Failed -> s.copy(last = s.last?.let { l -> l.copy(modules = fork + l.modules.filter { m -> fork.none { it.id == m.id } }) })
        }
    }
}

// The fork's add-ons come from the app's assets; everything else goes to the index's fetcher as before.
class ForkFetcher(private val inner: ArtifactFetcher, private val addons: ForkAddons, private val blobs: BlobStore) : ArtifactFetcher {
    override suspend fun fetch(ref: ArtifactRef, onProgress: (Long, Long) -> Unit): FetchResult {
        val file = addons.artifact(ref) ?: return inner.fetch(ref, onProgress)
        val r = addons.blob(blobs, ref, file)
        if (r is FetchResult.Ok) onProgress(ref.size, ref.size)
        return r
    }
}
