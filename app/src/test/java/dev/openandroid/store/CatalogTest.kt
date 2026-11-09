package dev.openandroid.store

import dev.openandroid.store.core.AppVersion
import dev.openandroid.store.core.Catalog
import dev.openandroid.store.core.IndexParser
import dev.openandroid.store.core.InstallState
import dev.openandroid.store.core.Installed
import dev.openandroid.store.core.StoreApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogTest {
    private fun version(code: Long, minSdk: Int = 26, signer: String = "aa") =
        AppVersion("v$code", code, minSdk, "https://example.com/$code.apk", 1000, "ff", signer, emptyList(), "", "")

    private fun app(name: String, vararg versions: AppVersion, category: String = "Music") =
        StoreApp("dev.$name", name, "summary of $name", "", category, "", "MIT", "me", null, versions.toList())

    @Test
    fun installStates() {
        val a = app("a", version(2), version(1))
        assertEquals(InstallState.NOT_INSTALLED, Catalog.stateOf(a, null, 34))
        assertEquals(InstallState.INSTALLED, Catalog.stateOf(a, Installed(2, "v2", "aa"), 34))
        assertEquals(InstallState.UPDATE_AVAILABLE, Catalog.stateOf(a, Installed(1, "v1", "AA"), 34))
        assertEquals(InstallState.SIGNATURE_MISMATCH, Catalog.stateOf(a, Installed(1, "v1", "bb"), 34))
        assertEquals(InstallState.UPDATE_AVAILABLE, Catalog.stateOf(a, Installed(1, "v1", null), 34))
    }

    @Test
    fun versionsThatNeedNewerAndroidAreSkipped() {
        val a = app("a", version(3, minSdk = 35), version(2, minSdk = 26))
        assertEquals(2L, a.latestFor(30)?.versionCode)
        assertEquals(3L, a.latestFor(35)?.versionCode)
        val onlyNew = app("b", version(1, minSdk = 35))
        assertNull(onlyNew.latestFor(30))
        assertEquals(InstallState.INCOMPATIBLE, Catalog.stateOf(onlyNew, null, 30))
    }

    @Test
    fun searchIgnoresCaseAndAccentsAndFiltersCategory() {
        val apps = listOf(app("Résonance"), app("Notes", category = "Tools"))
        assertEquals(listOf("Résonance"), Catalog.search(apps, "resonance", null).map { it.name })
        assertEquals(listOf("Notes"), Catalog.search(apps, "", "Tools").map { it.name })
        assertEquals(2, Catalog.search(apps, "  ", null).size)
        assertEquals(listOf("Music", "Tools"), Catalog.categories(apps))
    }

    @Test
    fun parsesIndexAndSkipsBrokenEntries() {
        val json = """
            {"format":1,"generatedAt":5,"apps":[
              {"package":"dev.a","name":"A","category":"Music","icon":"icons/a.png","versions":[
                {"versionName":"1.0","versionCode":1,"apk":"https://x/1.apk","sha256":"AB","size":10},
                {"versionName":"2.0","versionCode":2,"apk":"https://x/2.apk","sha256":"CD","minSdk":30,"permissions":["android.permission.CAMERA"]},
                {"versionName":"bad","versionCode":3,"apk":"http://x/3.apk","sha256":"EE"}
              ]},
              {"name":"no package"}
            ]}
        """.trimIndent()
        val index = IndexParser.parse(json, "https://example.github.io/Index/index.json")
        assertEquals(1, index.apps.size)
        val a = index.apps[0]
        assertEquals(listOf(2L, 1L), a.versions.map { it.versionCode })
        assertEquals("ab", a.versions[1].sha256)
        assertEquals("https://example.github.io/Index/icons/a.png", a.iconUrl)
        assertEquals("CAMERA", Catalog.permissionLabel(a.versions[0].permissions[0]))
    }

    @Test
    fun findsPermissionsAddedByAnUpdate() {
        val v = AppVersion("2", 2, 26, "https://x/2.apk", 1, "ff", "aa", listOf("p.CAMERA", "p.MIC", "p.WAKE"), "", "")
        val installed = Installed(1, "1", "aa", setOf("p.WAKE", "p.MIC"))
        assertEquals(listOf("p.CAMERA"), Catalog.newPermissions(installed, v))
        assertEquals(emptyList<String>(), Catalog.newPermissions(null, v))
    }

    @Test
    fun formatsSizes() {
        assertEquals("", Catalog.formatSize(0))
        assertEquals("2 KB", Catalog.formatSize(2048))
        assertEquals("1.5 MB", Catalog.formatSize(1_572_864))
    }
}
