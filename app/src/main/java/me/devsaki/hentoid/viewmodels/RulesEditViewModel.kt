package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.util.updateRenamingRulesJson


class RulesEditViewModel(
    application: Application, private val dao: CollectionDAO
) : AndroidViewModel(application) {
    // VARS
    private var query = ""

    // LIVEDATAS
    private var currentRulesSource: LiveData<List<RenamingRule>>? = null
    val rulesList = MediatorLiveData<List<RenamingRule>>()
    val attributeTypeFilter = MutableLiveData<AttributeType>()


    init {
        attributeTypeFilter.value = AttributeType.UNDEFINED
        loadRules()
    }


    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
    }


    fun setAttributeType(attributeType: AttributeType) {
        attributeTypeFilter.value = attributeType
        loadRules()
    }

    fun setQuery(data: String) {
        query = data
        loadRules()
    }

    fun loadRules() {
        val attributeType = attributeTypeFilter.value!!
        if (currentRulesSource != null) rulesList.removeSource(currentRulesSource!!)
        currentRulesSource = dao.selectRenamingRulesLive(attributeType, query)
        rulesList.addSource(currentRulesSource!!, rulesList::setValue)
    }

    fun createRule(type: AttributeType, source: String, target: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.insertRenamingRule(
                    RenamingRule(
                        attributeType = type,
                        sourceName = source,
                        targetName = target
                    )
                )
                updateRenamingRulesJson(
                    getApplication<Application>().applicationContext, dao
                )
            }
        }
    }

    fun editRule(id: Long, source: String, target: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existingRule = dao.selectRenamingRule(id)
                existingRule?.let {
                    existingRule.sourceName = source
                    existingRule.targetName = target
                    dao.insertRenamingRule(existingRule)
                    updateRenamingRulesJson(
                        getApplication<Application>().applicationContext, dao
                    )
                }
            }
        }
    }

    fun removeRules(itemIds: List<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteRenamingRules(itemIds)
                updateRenamingRulesJson(
                    getApplication<Application>().applicationContext, dao
                )
            }
        }
    }
}