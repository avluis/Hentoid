package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.annimon.stream.Stream
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposables
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.SearchHelper.AttributeQueryResult
import org.threeten.bp.Instant


class MetadataEditViewModel(
    application: Application,
    private val dao: CollectionDAO
) : AndroidViewModel(application) {

    // Disposables (to cleanup Rx calls and avoid memory leaks)
    private val compositeDisposable = CompositeDisposable()
    private val countDisposable = Disposables.empty()
    private var filterDisposable = Disposables.empty()

    // LIVEDATAS
    private val contentList = MutableLiveData<List<Content>>()
    private val attributeTypes = MutableLiveData<List<AttributeType>>()
    private val contentAttributes = MutableLiveData<List<Attribute>>()
    private val libraryAttributes = MutableLiveData<AttributeQueryResult>()


    init {
        contentAttributes.value = ArrayList()
    }

    override fun onCleared() {
        super.onCleared()
        filterDisposable.dispose()
        countDisposable.dispose()
        dao.cleanup()
        compositeDisposable.clear()
    }


    fun getContent(): LiveData<List<Content>> {
        return contentList
    }

    fun getAttributeTypes(): LiveData<List<AttributeType>> {
        return attributeTypes
    }

    fun getContentAttributes(): LiveData<List<Attribute>> {
        return contentAttributes
    }

    fun getLibraryAttributes(): LiveData<AttributeQueryResult> {
        return libraryAttributes
    }


    /**
     * Load the given list of Content
     *
     * @param contentId  IDs of the Contents to load
     */
    fun loadContent(contentId: LongArray) {
        val contents = dao.selectContent(contentId.filter { id -> id > 0 }.toLongArray())
        val attrs = ArrayList<Attribute>()
        contents.forEach { c ->
            attrs.addAll(c.attributes)
        }
        contentList.postValue(contents)
        contentAttributes.postValue(attrs)
    }

    fun setCover(order: Int) {
        val content = contentList.value?.get(0)
        if (content != null) {
            content.imageFiles?.forEach {
                if (it.order == order) {
                    it.setIsCover(true)
                    content.coverImageUrl = it.url
                } else {
                    it.setIsCover(false)
                }
            }
            contentList.postValue(Stream.of(content).toList())
        }
    }

    /**
     * Set the attributes type to search in the Atttribute search
     *
     * @param value Attribute types the searches will be performed for
     */
    fun setAttributeTypes(value: List<AttributeType>) {
        attributeTypes.postValue(value)
    }

    /**
     * Set and run the query to perform the Attribute search
     *
     * @param query        Content of the attribute name to search (%s%)
     * @param pageNum      Number of the "paged" result to fetch
     * @param itemsPerPage Number of items per result "page"
     */
    fun setAttributeQuery(query: String, pageNum: Int, itemsPerPage: Int) {
        filterDisposable.dispose()
        filterDisposable = dao.selectAttributeMasterDataPaged(
            attributeTypes.value!!,
            query,
            -1,
            emptyList(),
            ContentHelper.Location.ANY,
            ContentHelper.Type.ANY,
            pageNum,
            itemsPerPage,
            Preferences.getSearchAttributesSortOrder()
        ).subscribe { value: AttributeQueryResult? ->
            libraryAttributes.postValue(value)
        }
    }

    /**
     * Add the given attribute to the attribute selection for the Content and Attribute searches
     * - Only books tagged with all selected attributes will be among Content search results
     * - Only attributes contained in these books will be among Attribute search results
     *
     * @param attr Attribute to add to current selection
     */
    fun addContentAttribute(attr: Attribute) {
        val newAttrs = ArrayList<Attribute>()
        if (contentAttributes.value != null) newAttrs.addAll(contentAttributes.value!!) // Create new instance to make ListAdapter.submitList happy

        newAttrs.add(attr)
        setAttrList(newAttrs)
    }

    // TODO doc
    fun removeContentAttribute(attr: Attribute) {
        val newAttrs = ArrayList<Attribute>()
        if (contentAttributes.value != null) newAttrs.addAll(contentAttributes.value!!) // Create new instance to make ListAdapter.submitList happy

        newAttrs.remove(attr)
        setAttrList(newAttrs)
    }

    private fun setAttrList(value: List<Attribute>) {
        contentAttributes.value = value

        // Update Contents
        val contents = ArrayList<Content>()
        if (contentList.value != null) {
            contents.addAll(contentList.value!!)
            contents.forEach { c -> c.putAttributes(value) }
            contentList.postValue(contents)
        }
    }

    fun setTitle(value: String) {
        // Update Contents
        val contents = ArrayList<Content>()
        if (contentList.value != null) {
            contents.addAll(contentList.value!!)
            contents.forEach { c -> c.title = value }
            contentList.postValue(contents)
        }
    }

    fun saveContent() {
        contentList.value?.forEach {
            it.lastEditDate = Instant.now().toEpochMilli()
            dao.insertContent(it)
        }
    }
}