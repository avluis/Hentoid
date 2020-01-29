package me.devsaki.hentoid.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.kotlintest.matchers.types.shouldNotBeNull
import me.devsaki.hentoid.database.ObjectBoxDAO
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class SearchViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    lateinit var mockObjectBoxDAO: ObjectBoxDAO

    @Test
    fun `verify initial state`() {
        val viewModel = SearchViewModel(mockObjectBoxDAO)

        viewModel.selectedAttributesData.shouldNotBeNull()
    }
}