package tv.cleanyour.core.planner

import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.FileRoute
import tv.cleanyour.core.model.Module
import tv.cleanyour.core.model.ModuleVersion
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Tier
import tv.cleanyour.core.planner.PlannerFixtures.installedLauncher
import tv.cleanyour.core.planner.PlannerFixtures.plan
import tv.cleanyour.core.planner.PlannerFixtures.refused
import tv.cleanyour.core.planner.PlannerFixtures.snapshot
import tv.cleanyour.core.spec.LauncherConfigDoc
import tv.cleanyour.testing.fixtures.FakeConfigTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Fork (Clean Your TV RU): a Make it yours send with the Widgets add-on: added from the app's pinned copy when the
// TV lacks it, then its settings through its provider, before the launcher's look (so game_day finds the package).
class ForkWidgetsPlanTest {
    private val pkg = PackageName(DefaultPlanner.WIDGETS_PKG)
    private val signer = Sha256("e".repeat(64))
    private val apkSha = Sha256("d".repeat(64))
    private val version = ModuleVersion(1, "1.0", "", ArtifactRef("bundled-fork:tv.cleanyour.ru.widgets.apk", 5000, apkSha, signer),
        emptyList(), "net", 26, emptyList(), emptyList())
    private val module = Module("cyt-widgets", "Widgets", "one", "desc", pkg, "app", Tier.VERIFIED, "pub", "src", "MIT", null, null, listOf(version))
    private val apk = BlobRef(apkSha, 5000, "bundled/widgets")
    private val settings = BlobRef(Sha256("f".repeat(64)), 120, "cfg-widgets")
    private val config = BlobRef(Sha256("c".repeat(64)), 100, "cfg")
    private val widgetsConfig = RemotePath(RemoteDir.WIDGETS, listOf("config.json"))

    // Android 10 or later: `content write` exists, so the settings can go through the provider.
    private fun launcherSnap(vararg extra: Pair<PackageName, PackageState>) = snapshot(extra = mapOf(installedLauncher(), *extra))
        .let { s -> s.copy(facts = s.facts.copy(sdk = 30)).let { it.copy(contentHash = tv.cleanyour.core.store.SnapshotCodec.hash(it)) } }

    @Test fun androidNineGetsTheAddOnButNoSettingsWrite() {
        val p = plan(PlanRequest.ApplySettings(LauncherConfigDoc(), config, PlanRequest.WidgetsPush(module, version, apk, settings)),
            snapshot(extra = mapOf(installedLauncher())))
        assertTrue(p.steps.any { val o = it.op; (o is Op.InstallStream && o.pkg == pkg) || (o is Op.Install && o.pkg == pkg) })
        assertTrue(p.steps.none { (it.op as? Op.PushAsset)?.dest == widgetsConfig })
    }

    @Test fun aFreshAddOnIsAddedThenGetsItsSettingsThenTheLook() {
        val transport = FakeConfigTransport()
        val req = PlanRequest.ApplySettings(LauncherConfigDoc(), config, PlanRequest.WidgetsPush(module, version, apk, settings))
        val p = plan(req, launcherSnap(), planner = PlannerFixtures.planner(transport))
        val ops = p.steps.map { it.op }
        val install = ops.indexOfFirst { it is Op.InstallStream && it.pkg == pkg || it is Op.Install && it.pkg == pkg }
        val push = ops.indexOfFirst { it is Op.PushAsset && it.dest == widgetsConfig }
        // The look's steps (the transport's) come after the add-on's.
        assertTrue(install >= 0 && push > install && ops.drop(push + 1).isNotEmpty(), ops.toString())
        assertTrue(p.steps.drop(push + 1).all { it.group == DefaultPlanner.GROUP_THEME }, ops.toString())
        val pushOp = ops[push] as Op.PushAsset
        assertEquals(FileRoute.THEME_PROVIDER, pushOp.route)
        assertEquals(settings.sha256, pushOp.sha256)
        assertEquals(PlanKind.THEME, p.kind)
    }

    @Test fun anInstalledAddOnOnlyGetsItsSettings() {
        val have = pkg to PackageState(EnabledState.ENABLED, false, 1, signer, false, false, "Widgets", false)
        val p = plan(PlanRequest.ApplySettings(LauncherConfigDoc(), config, PlanRequest.WidgetsPush(module, version, apk, settings)), launcherSnap(have))
        assertTrue(p.steps.none { val o = it.op; o is Op.InstallStream && o.pkg == pkg })
        assertTrue(p.steps.any { (it.op as? Op.PushAsset)?.dest == widgetsConfig })
    }

    @Test fun withoutTheAddOnAndNoCopyNoSettingsAreWritten() {
        val p = plan(PlanRequest.ApplySettings(LauncherConfigDoc(), config, PlanRequest.WidgetsPush(null, null, null, settings)), launcherSnap())
        assertTrue(p.steps.none { (it.op as? Op.PushAsset)?.dest == widgetsConfig })
    }

    @Test fun aCopyThatDoesNotMatchOrAnotherKeyIsRefused() {
        assertTrue(refused(PlanRequest.ApplySettings(LauncherConfigDoc(), config,
            PlanRequest.WidgetsPush(module, version, apk.copy(size = 1), settings)), launcherSnap()).single().contains("pinned copy"))
        val other = pkg to PackageState(EnabledState.ENABLED, false, 1, Sha256("1".repeat(64)), false, false, "Widgets", false)
        assertTrue(refused(PlanRequest.ApplySettings(LauncherConfigDoc(), config,
            PlanRequest.WidgetsPush(module, version.copy(versionCode = 2), apk, settings)), launcherSnap(other)).single().contains("another key"))
    }
}
