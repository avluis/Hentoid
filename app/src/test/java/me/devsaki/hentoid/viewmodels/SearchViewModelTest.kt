package me.devsaki.hentoid.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.test.core.app.ApplicationProvider
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.shouldBe
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.mocks.AbstractObjectBoxTest
import me.devsaki.hentoid.util.ContentHelper
import net.lachlanmckee.timberjunit.TimberTestRule
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest : AbstractObjectBoxTest() {

    @get:Rule
    val logAllAlwaysRule: TimberTestRule? = TimberTestRule.logAllAlways()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    companion object {
        lateinit var mockObjectBoxDAO: CollectionDAO

        @BeforeClass
        @JvmStatic
        fun prepareDB() {
            println(">> Preparing DB...")

            val attrs1 = ArrayList<Attribute>()
            attrs1.add(Attribute(AttributeType.ARTIST, "artist1"))
            attrs1.add(Attribute(AttributeType.LANGUAGE, "english"))

            val attrs2 = ArrayList<Attribute>()
            attrs2.add(Attribute(AttributeType.ARTIST, "artist2"))
            attrs2.add(Attribute(AttributeType.LANGUAGE, "english"))

            val attrs3 = ArrayList<Attribute>()
            attrs3.add(Attribute(AttributeType.ARTIST, "artist3"))
            attrs3.add(Attribute(AttributeType.LANGUAGE, "english"))

            mockObjectBoxDAO = ObjectBoxDAO(store)
            mockObjectBoxDAO.insertContent(
                Content().setTitle("").setStatus(StatusContent.DOWNLOADED).setSite(Site.ASMHENTAI)
                    .addAttributes(attrs1)
            )
            mockObjectBoxDAO.insertContent(
                Content().setTitle("").setStatus(StatusContent.DOWNLOADED).setSite(Site.HITOMI)
                    .addAttributes(attrs1)
            )
            mockObjectBoxDAO.insertContent(
                Content().setTitle("").setStatus(StatusContent.DOWNLOADED).setSite(Site.ASMHENTAI)
                    .addAttributes(attrs2)
            )
            mockObjectBoxDAO.insertContent(
                Content().setTitle("").setStatus(StatusContent.ONLINE).setSite(Site.HITOMI)
                    .addAttributes(attrs3)
            )
            println(">> DB prepared")
        }
    }

    private fun lookForAttr(type: AttributeType, name: String): Attribute {
        val result = mockObjectBoxDAO.selectAttributeMasterDataPaged(
            listOf(type),
            name,
            -1,
            null,
            ContentHelper.Location.ANY,
            ContentHelper.Type.ANY,
            true,
            1,
            40,
            0
        ).blockingGet()
        return result.attributes[0]
    }

    fun <T> LiveData<T>.observeForTesting(block: () -> Unit) {
        val observer = Observer<T> { }
        try {
            observeForever(observer)
            block()
        } finally {
            removeObserver(observer)
        }
    }

    /**
     * Gets the value of a [LiveData] or waits for it to have one, with a timeout.
     *
     * Use this extension from host-side (JVM) tests. It's recommended to use it alongside
     * `InstantTaskExecutorRule` or a similar mechanism to execute tasks synchronously.
     */
    fun <T> LiveData<T>.getOrAwaitValue(
        time: Long = 2,
        timeUnit: TimeUnit = TimeUnit.SECONDS,
        afterObserve: () -> Unit = {}
    ): T {
        var data: T? = null
        val latch = CountDownLatch(1)
        val observer = object : Observer<T> {
            override fun onChanged(o: T?) {
                data = o
                latch.countDown()
                this@getOrAwaitValue.removeObserver(this)
            }
        }
        this.observeForever(observer)

        afterObserve.invoke()

        // Don't wait indefinitely if the LiveData is not set.
        if (!latch.await(time, timeUnit)) {
            this.removeObserver(observer)
            throw TimeoutException("LiveData value was never set.")
        }

        @Suppress("UNCHECKED_CAST")
        return data as T
    }

    @Test
    fun `verify initial state`() {
        println(">> verify initial state START")
        val viewModel = SearchViewModel(ApplicationProvider.getApplicationContext(), mockObjectBoxDAO, 1)

        viewModel.selectedAttributes.shouldNotBeNull()
        println(">> verify initial state END")
    }

    @Test
    fun `count category attributes unfiltered`() {
        println(">> count category attributes unfiltered START")
        val viewModel = SearchViewModel(ApplicationProvider.getApplicationContext(), mockObjectBoxDAO, 1)
        viewModel.update()

        val attrs = viewModel.nbAttributesPerType.value
        attrs.shouldNotBeNull()

        // General attributes
        attrs.size().shouldBe(3)
        attrs.indexOfKey(AttributeType.ARTIST.code).shouldBeGreaterThanOrEqual(0)
        attrs.indexOfKey(AttributeType.LANGUAGE.code).shouldBeGreaterThanOrEqual(0)
        attrs.indexOfKey(AttributeType.SOURCE.code).shouldBeGreaterThanOrEqual(0)

        // Details
        attrs[AttributeType.ARTIST.code].shouldBe(2)
        attrs[AttributeType.LANGUAGE.code].shouldBe(1)
        attrs[AttributeType.SOURCE.code].shouldBe(2)
        println(">> count category attributes unfiltered END")
    }

    @Test
    fun `list category attributes unfiltered`() {
        println(">> list category attributes unfiltered START")
        val viewModel = SearchViewModel(ApplicationProvider.getApplicationContext(), mockObjectBoxDAO, 1)
        viewModel.update()

        val typeList = ArrayList<AttributeType>()
        typeList.add(AttributeType.ARTIST)
        viewModel.setAttributeTypes(typeList)
        viewModel.setAttributeQuery("", 1, 40)

        val attrs = viewModel.availableAttributes.value
        attrs.shouldNotBeNull()
        attrs.attributes.shouldNotBeNull()

        attrs.totalSelectedAttributes.shouldBe(2)
        attrs.attributes.size.shouldBe(2)
        attrs.attributes[0].name.shouldBe("artist1")
        attrs.attributes[0].count.shouldBe(2)
        attrs.attributes[1].name.shouldBe("artist2")
        attrs.attributes[1].count.shouldBe(1)
        println(">> list category attributes unfiltered END")
    }

    @Test
    fun `count category attributes filtered`() {
        println(">> count category attributes filtered START")
        val viewModel = SearchViewModel(ApplicationProvider.getApplicationContext(), mockObjectBoxDAO, 1)
        val searchAttr = lookForAttr(AttributeType.ARTIST, "artist1")
        searchAttr.shouldNotBeNull()
        viewModel.addSelectedAttribute(searchAttr)

        val attrs = viewModel.nbAttributesPerType.value
        attrs.shouldNotBeNull()

        // General attributes
        attrs.size().shouldBe(3)
        attrs.indexOfKey(AttributeType.ARTIST.code).shouldBeGreaterThanOrEqual(0)
        attrs.indexOfKey(AttributeType.LANGUAGE.code).shouldBeGreaterThanOrEqual(0)
        attrs.indexOfKey(AttributeType.SOURCE.code).shouldBeGreaterThanOrEqual(0)

        // Details
        attrs[AttributeType.ARTIST.code].shouldBe(0) // Once we select one artist, any other artist is unavailable
        attrs[AttributeType.LANGUAGE.code].shouldBe(1)
        attrs[AttributeType.SOURCE.code].shouldBe(2)

        println(">> count category attributes filtered END")
    }

    @Test
    fun `count books unfiltered`() {
        println(">> count books unfiltered START")
        val viewModel = SearchViewModel(ApplicationProvider.getApplicationContext(), mockObjectBoxDAO, 1)
        viewModel.update()

        val typeList = ArrayList<AttributeType>()
        typeList.add(AttributeType.ARTIST)
        viewModel.setAttributeTypes(typeList)
        viewModel.setAttributeQuery("", 1, 40)

        val books = viewModel.selectedContentCount.getOrAwaitValue {}
        books.shouldBe(3) // One of the books is not in the DOWNLOADED state

        println(">> count books unfiltered END")
    }

    @Test
    fun `count books filtered`() {
        println(">> count books filtered START")
        val viewModel = SearchViewModel(ApplicationProvider.getApplicationContext(), mockObjectBoxDAO, 1)

        val searchAttr = lookForAttr(AttributeType.ARTIST, "artist1")
        searchAttr.shouldNotBeNull()
        viewModel.addSelectedAttribute(searchAttr)

        val books = viewModel.selectedContentCount.getOrAwaitValue {}
        books.shouldBe(2)

        println(">> count books filtered END")
    }
}