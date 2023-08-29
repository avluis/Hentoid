package me.devsaki.hentoid.dao

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.mocks.AbstractObjectBoxTest
import me.devsaki.hentoid.util.ImportHelper
import net.lachlanmckee.timberjunit.TimberTestRule
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner


@RunWith(RobolectricTestRunner::class)
class BookmarksTest : AbstractObjectBoxTest() {

    @get:Rule
    val logAllAlwaysRule: TimberTestRule? = TimberTestRule.logAllAlways()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    companion object {
        lateinit var dao: CollectionDAO

        @BeforeClass
        @JvmStatic
        fun prepareDB() {
            println(">> Preparing DB...")
            dao = ObjectBoxDAO(store)
            dao.insertBookmark(SiteBookmark(Site.ASMHENTAI, "aaa", Site.ASMHENTAI.url + "/stuff1"))
            dao.insertBookmark(SiteBookmark(Site.ASMHENTAI, "aaa", Site.ASMHENTAI.url + "/stuff2/"))
            println(">> DB prepared")
        }
    }

    @Test
    fun `test create`() {
        println(">> test create START")
        val bookmarks = dao.selectBookmarks(Site.ASMHENTAI)

        val existingUrl1 = Site.ASMHENTAI.url + "/stuff1"
        val existingUrl2 = Site.ASMHENTAI.url + "/stuff1/"
        val existingUrl3 = Site.ASMHENTAI.url + "/stuff2"
        val existingUrl4 = Site.ASMHENTAI.url + "/stuff2/"

        var currentBookmark: SiteBookmark? = bookmarks.firstOrNull { b: SiteBookmark ->
            SiteBookmark.urlsAreSame(
                b.url,
                existingUrl1
            )
        }
        Assert.assertTrue(currentBookmark != null)
        currentBookmark = bookmarks.firstOrNull { b: SiteBookmark ->
            SiteBookmark.urlsAreSame(
                b.url,
                existingUrl2
            )
        }
        Assert.assertTrue(currentBookmark != null)
        currentBookmark = bookmarks.firstOrNull { b: SiteBookmark ->
            SiteBookmark.urlsAreSame(
                b.url,
                existingUrl3
            )
        }
        Assert.assertTrue(currentBookmark != null)
        currentBookmark = bookmarks.firstOrNull { b: SiteBookmark ->
            SiteBookmark.urlsAreSame(
                b.url,
                existingUrl4
            )
        }
        Assert.assertTrue(currentBookmark != null)
        println(">> test create END")
    }

    @Test
    fun `test import`() {
        println(">> test import START")

        val bookmarksToImport = ArrayList<SiteBookmark>()
        bookmarksToImport.add(SiteBookmark(Site.ASMHENTAI, "aaa", Site.ASMHENTAI.url + "/stuff1"))
        bookmarksToImport.add(SiteBookmark(Site.ASMHENTAI, "aaa", Site.ASMHENTAI.url + "/stuff1/"))
        bookmarksToImport.add(SiteBookmark(Site.ASMHENTAI, "aaa", Site.ASMHENTAI.url + "/stuff2"))
        bookmarksToImport.add(SiteBookmark(Site.ASMHENTAI, "aaa", Site.ASMHENTAI.url + "/stuff2/"))

        Assert.assertEquals(0, ImportHelper.importBookmarks(dao, bookmarksToImport))

        bookmarksToImport.add(SiteBookmark(Site.ASMHENTAI, "aaa", Site.ASMHENTAI.url + "/stuff3/"))
        Assert.assertEquals(1, ImportHelper.importBookmarks(dao, bookmarksToImport))

        val bookmarksToImportSet = HashSet<SiteBookmark>(bookmarksToImport)
        Assert.assertEquals(3, bookmarksToImportSet.size)

        println(">> test import END")
    }
}