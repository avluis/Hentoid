package me.devsaki.hentoid.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner


@RunWith(MockitoJUnitRunner::class)
class SearchViewModelTest : AbstractObjectBoxTest() {

    companion object {
        lateinit var mockObjectBoxDAO: CollectionDAO

        @BeforeClass @JvmStatic
        fun prepareDB() {
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
            mockObjectBoxDAO.insertContent(Content().setTitle("").setStatus(StatusContent.DOWNLOADED).setSite(Site.ASMHENTAI).addAttributes(attrs1))
            mockObjectBoxDAO.insertContent(Content().setTitle("").setStatus(StatusContent.DOWNLOADED).setSite(Site.HITOMI).addAttributes(attrs1))
            mockObjectBoxDAO.insertContent(Content().setTitle("").setStatus(StatusContent.DOWNLOADED).setSite(Site.ASMHENTAI).addAttributes(attrs2))
            mockObjectBoxDAO.insertContent(Content().setTitle("").setStatus(StatusContent.ONLINE).setSite(Site.HITOMI).addAttributes(attrs3))
        }
    }

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()


    @Test
    fun `verify initial state`() {
        val viewModel = SearchViewModel(mockObjectBoxDAO)

        viewModel.selectedAttributesData.shouldNotBeNull()
    }

    @Test
    fun `count category attributes unfiltered`() {
        val viewModel = SearchViewModel(mockObjectBoxDAO)
        viewModel.initAndStart(1)

        val attrs = viewModel.attributesCountData.value
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
    }

    @Test
    fun `list category attributes unfiltered`() {
        val viewModel = SearchViewModel(mockObjectBoxDAO)
        viewModel.initAndStart(1)

        val typeList = ArrayList<AttributeType>()
        typeList.add(AttributeType.ARTIST)
        viewModel.onCategoryChanged(typeList)
        viewModel.onCategoryFilterChanged("", 1, 40)

        val attrs = viewModel.proposedAttributesData.value
        attrs.shouldNotBeNull()
        attrs.attributes.shouldNotBeNull()

        attrs.totalSelectedAttributes.shouldBe(2)
        attrs.attributes.size.shouldBe(2)
        attrs.attributes[0].name.shouldBe("artist1")
        attrs.attributes[0].count.shouldBe(2)
        attrs.attributes[1].name.shouldBe("artist2")
        attrs.attributes[1].count.shouldBe(1)
    }

    @Test
    fun `count category attributes filtered`() {
        // TODO
    }
}