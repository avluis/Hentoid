package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.GroupItem
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.util.AttributeQueryResult
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.addContentToAttributeGroup
import me.devsaki.hentoid.util.getOrCreateNoArtistGroup
import me.devsaki.hentoid.util.removeContentFromGrouping
import me.devsaki.hentoid.util.updateGroupsJson
import me.devsaki.hentoid.workers.UpdateJsonWorker
import me.devsaki.hentoid.workers.data.UpdateJsonData
import timber.log.Timber
import java.time.Instant


class MetadataEditViewModel(
    application: Application,
    private val dao: CollectionDAO
) : AndroidViewModel(application) {

    // LIVEDATAS
    private val contentList = MutableLiveData<List<Content>>()
    private val attributeTypes = MutableLiveData<List<AttributeType>>()
    private val contentAttributes = MutableLiveData<List<Attribute>>()
    private val libraryAttributes = MutableLiveData<AttributeQueryResult>()
    private val resetSelectionFilter = MutableLiveData<Int>()


    init {
        contentAttributes.value = ArrayList()
        resetSelectionFilter.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
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

    fun getResetSelectionFilter(): LiveData<Int> {
        return resetSelectionFilter
    }

    /**
     * Allow the Activity to tell the BottomSheetFragment to reset its filter
     * Needed because the callback from AttributeTypePickerDialogFragment is done on the activity
     */
    fun resetSelectionFilter() {
        resetSelectionFilter.postValue(resetSelectionFilter.value!! + 1)
    }

    /**
     * Load the given list of Content
     *
     * @param contentId  IDs of the Contents to load
     */
    fun loadContent(contentId: LongArray) {
        val contents = dao.selectContent(contentId.filter { id -> id > 0 }.toLongArray())
        val rawAttrs = ArrayList<Attribute>()
        contents.forEach { c ->
            rawAttrs.addAll(c.attributes)
        }
        val attrsCount = rawAttrs.groupingBy { a -> a }.eachCount()
        attrsCount.entries.forEach { it.key.count = it.value }

        contentList.postValue(contents)
        contentAttributes.postValue(attrsCount.keys.toList())
    }

    fun setCover(order: Int) {
        val content = contentList.value?.get(0) ?: return
        val imageFiles = content.imageFiles ?: return

        val img = imageFiles.find { it.order == order }
        if (img != null) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        ContentHelper.setContentCover(content, imageFiles, img)
                        contentList.postValue(mutableListOf(content))
                    } catch (t: Throwable) {
                        Timber.e(t)
                    }
                }
            }
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val result = dao.selectAttributeMasterDataPaged(
                    attributeTypes.value!!,
                    query,
                    -1,
                    emptySet(),
                    ContentHelper.Location.ANY,
                    ContentHelper.Type.ANY,
                    true,
                    pageNum,
                    itemsPerPage,
                    Preferences.getSearchAttributesSortOrder()
                )
                libraryAttributes.postValue(result)
            }
        }
    }

    /**
     * Replace the given attribute with the other given attribute to the selected books
     * NB : Replacement will be done only on books where 'toBeReplaced' is set; _not_ on all books of the collection
     *
     * @param idToBeReplaced ID of the Attribute to be replaced in the current book selection
     * @param toReplaceWith Attribute to replace with in the current book selection
     */
    fun replaceContentAttribute(idToBeReplaced: Long, toReplaceWith: Attribute) {
        val toBeReplaced = dao.selectAttribute(idToBeReplaced)
        if (toBeReplaced != null) setAttr(toReplaceWith, toBeReplaced, true)
    }

    /**
     * Add the given attribute to the selected books
     *
     * @param attr Attribute to add to current selection
     */
    fun addContentAttribute(attr: Attribute) {
        setAttr(attr, null)
    }

    /**
     * Remove the given attribute from the selected books
     *
     * @param attr Attribute to remove to current selection
     */
    fun removeContentAttribute(attr: Attribute) {
        setAttr(null, attr)
    }

    fun createAssignNewAttribute(attrName: String, type: AttributeType): Attribute {
        val attr = ContentHelper.addAttribute(type, attrName, dao)
        addContentAttribute(attr)
        return attr
    }

    /**
     * Add and remove the given attributes from the selected books
     *
     * @param toAdd Attribute to add to current selection
     * @param toRemove Attribute to remove to current selection
     * @param replaceMode True to add only where removed items are present; false to apply to all books
     */
    private fun setAttr(toAdd: Attribute?, toRemove: Attribute?, replaceMode: Boolean = false) {
        // Update displayed attributes
        val newAttrs = ArrayList<Attribute>()
        if (contentAttributes.value != null) newAttrs.addAll(contentAttributes.value!!) // Create new instance to make ListAdapter.submitList happy

        val toAddNew: Attribute?
        if (toAdd != null) {
            if (newAttrs.contains(toAdd)) newAttrs.remove(toAdd)
            // Create new instance for list differs to detect changes (if not, attributes are changed both on the old and the updated object)
            toAddNew = Attribute(toAdd)
            newAttrs.add(toAddNew)
            dao.insertAttribute(toAdd) // Add new attribute to DB for it to appear on the attribute search
        } else toAddNew = null
        if (toRemove != null) newAttrs.remove(toRemove)

        // Update Contents
        var newCount = 0
        val contents = ArrayList<Content>()
        if (contentList.value != null) {
            contents.addAll(contentList.value!!)
            contents.forEach {
                val attrs = it.attributes
                if (toAddNew != null) {
                    if (attrs.contains(toAddNew)) attrs.remove(toAddNew)
                    if (!replaceMode || (toRemove != null && attrs.contains(toRemove))) {
                        attrs.add(toAddNew)
                        newCount++
                    }
                }
                if (toRemove != null) attrs.remove(toRemove)
                it.putAttributes(attrs)
            }
            if (newCount > 0) toAddNew?.count = newCount
            contentList.postValue(contents)
        }

        contentAttributes.value = newAttrs
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    doSaveContent()
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }
    }

    private fun doSaveContent() {
        // Update DB
        contentList.value?.forEach {
            it.lastEditDate = Instant.now().toEpochMilli()
            it.computeAuthor()

            // Save Content itself
            it.imageFiles?.let { imgs ->
                dao.insertImageFiles(imgs)
            }
            dao.insertContent(it)

            // Assign Content to each artist/circle group
            removeContentFromGrouping(
                me.devsaki.hentoid.enums.Grouping.ARTIST,
                it,
                dao
            )
            var artistFound = false
            it.attributes.forEach { attr ->
                if (attr.type == AttributeType.ARTIST || attr.type == AttributeType.CIRCLE) {
                    addContentToAttributeGroup(attr.group.target, attr, it, dao)
                    artistFound = true
                }
            }
            if (!artistFound) {
                // Add to the "no artist" group if no artist has been found
                val group = getOrCreateNoArtistGroup(getApplication(), dao)
                val item = GroupItem(it, group, -1)
                dao.insertGroupItem(item)
            }
        }

        contentList.value?.let {
            // Save all JSONs
            val builder = UpdateJsonData.Builder()
            builder.setContentIds(it.map { c -> c.id }.toLongArray())
            builder.setUpdateGroups(true)

            val workManager = WorkManager.getInstance(getApplication())
            workManager.enqueueUniqueWork(
                R.id.udpate_json_service.toString(),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<UpdateJsonWorker>()
                    .setInputData(builder.data)
                    .build()
            )
        }
    }

    fun renameAttribute(newName: String, id: Long, createRule: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    doRenameAttribute(newName, id, createRule)
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }
    }

    private fun doRenameAttribute(newName: String, id: Long, createRule: Boolean) {
        val attr = dao.selectAttribute(id) ?: return

        // Update displayed attributes
        val newAttrs = ArrayList<Attribute>()
        if (contentAttributes.value != null) newAttrs.addAll(contentAttributes.value!!) // Create new instance to make ListAdapter.submitList happy
        newAttrs.remove(attr)

        // Persist rule
        if (createRule) {
            val newRule = RenamingRule(attr.type, attr.name, newName)
            val existingRules = HashSet(dao.selectRenamingRules(AttributeType.UNDEFINED, null))
            if (!existingRules.contains(newRule)) {
                dao.insertRenamingRule(newRule)
                Helper.updateRenamingRulesJson(getApplication(), dao)
            }
        }

        // Update attribute
        attr.name = newName
        attr.displayName = newName
        dao.insertAttribute(attr)

        newAttrs.add(attr)
        contentAttributes.postValue(newAttrs)

        // Update corresponding group if needed
        val group = attr.linkedGroup
        if (group != null) {
            group.name = newName
            dao.insertGroup(group)
            updateGroupsJson(getApplication(), dao)
        }

        // Mark all related books for update
        val contents = attr.contents
        if (contents != null && !contents.isEmpty()) {
            contents.forEach {
                // Update the 'author' pre-calculated field for all related books if needed
                if (attr.type.equals(AttributeType.ARTIST) || attr.type.equals(AttributeType.CIRCLE)) {
                    it.computeAuthor()
                    ContentHelper.persistJson(getApplication(), it)
                }
                it.lastEditDate = Instant.now().toEpochMilli()
                dao.insertContent(it)
            }
        }
    }
}