package me.devsaki.hentoid.viewmodels

import android.util.SparseIntArray
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner


@RunWith(MockitoJUnitRunner::class)
class SearchViewModelTest : AbstractObjectBoxTest() {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    fun prepareDB(): CollectionDAO {
        val attrs1 = ArrayList<Attribute>()
        attrs1.add(Attribute(AttributeType.ARTIST, "artist1"))
        attrs1.add(Attribute(AttributeType.LANGUAGE, "english"))

        val attrs2 = ArrayList<Attribute>()
        attrs2.add(Attribute(AttributeType.ARTIST, "artist2"))
        attrs2.add(Attribute(AttributeType.LANGUAGE, "english"))

        val mockObjectBoxDAO = ObjectBoxDAO(store)
        mockObjectBoxDAO.insertContent(Content().setTitle("").setStatus(StatusContent.DOWNLOADED).setSite(Site.ASMHENTAI).addAttributes(attrs1))
        mockObjectBoxDAO.insertContent(Content().addAttributes(attrs1))
        mockObjectBoxDAO.insertContent(Content().addAttributes(attrs2))

        return mockObjectBoxDAO
    }

    @Test
    fun `verify initial state`() {
        val viewModel = SearchViewModel(prepareDB())

        viewModel.selectedAttributesData.shouldNotBeNull()
    }

    @Test
    fun `query simple`() {
        val viewModel = SearchViewModel(prepareDB())
        prepareDB()

        viewModel.emptyStart()
        val initialAttrs = viewModel.attributesCountData.value as SparseIntArray

        initialAttrs.size().shouldBe(3) // Artist, language and source
    }
}