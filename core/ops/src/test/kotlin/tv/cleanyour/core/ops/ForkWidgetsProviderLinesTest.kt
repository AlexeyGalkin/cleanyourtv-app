package tv.cleanyour.core.ops

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.FileRoute
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.Sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Fork (Clean Your TV RU): the Widgets add-on's settings go through its own provider, the launcher's way, and only
// its config.json.
class ForkWidgetsProviderLinesTest {
    private val renderer = ShellRenderer()
    private val config = RemotePath(RemoteDir.WIDGETS, listOf("config.json"))
    private val sha = Sha256("b".repeat(64))
    private val blob = BlobRef(sha, 120, "blobs/" + sha.hex)

    @Test fun theAddOnsSettingsGoThroughItsProvider() {
        assertTrue(ThemeProviderLines.isThemeFile(config))
        assertEquals("content://tv.cleanyour.ru.widgets.config/config.json", ThemeProviderLines.uri(config))
        assertEquals(RemotePath(RemoteDir.TMP, listOf("widgets-config.json")), ThemeProviderLines.stage(config))
        assertTrue("content write --uri content://tv.cleanyour.ru.widgets.config/config.json < /data/local/tmp/cleanyourtv/widgets-config.json" in
            ThemeProviderLines.write(config))
        val r = renderer.render(Op.PushAsset(blob, config, sha, FileRoute.THEME_PROVIDER))
        assertEquals(Transfer.Push(blob, RemotePath(RemoteDir.TMP, listOf("widgets-config.json")), 0x1A4), r.transfer)
        assertEquals("content read --uri content://tv.cleanyour.ru.widgets.config/config.json | sha256sum", ThemeProviderLines.read(config))
    }

    @Test fun nothingElseOfTheAddOn() {
        for (p in listOf(RemotePath(RemoteDir.WIDGETS, listOf("weather.json")), RemotePath(RemoteDir.WIDGETS, listOf("wallpaper.jpg")))) {
            assertFalse(ThemeProviderLines.isThemeFile(p), p.absolute)
            assertFailsWith<RenderRefused> { renderer.render(Op.PushAsset(blob, p, sha, FileRoute.THEME_PROVIDER)) }
        }
    }
}
