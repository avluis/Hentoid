package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposables
import io.reactivex.schedulers.Schedulers
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.enums.AttributeType
import timber.log.Timber


class RulesEditViewModel(
    application: Application,
    private val dao: CollectionDAO
) : AndroidViewModel(application) {

    // Disposables (to cleanup Rx calls and avoid memory leaks)
    private val compositeDisposable = CompositeDisposable()
    private var actionDisposable = Disposables.empty()
    private var filterDisposable = Disposables.empty()
    private var leaveDisposable = Disposables.empty()

    // VARS
    private var query = ""

    // LIVEDATAS
    private var currentRulesSource: LiveData<List<RenamingRule>>? = null
    private val rulesList = MediatorLiveData<List<RenamingRule>>()
    private val attributeTypeFilter = MutableLiveData<AttributeType>()


    init {
        attributeTypeFilter.value = AttributeType.UNDEFINED
        loadRules()
    }

    override fun onCleared() {
        super.onCleared()
        filterDisposable.dispose()
        actionDisposable.dispose()
        dao.cleanup()
        compositeDisposable.clear()
    }


    fun getRules(): LiveData<List<RenamingRule>> {
        return rulesList
    }

    fun getAttributeTypeFilter(): LiveData<AttributeType> {
        return attributeTypeFilter
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
        actionDisposable = Completable.fromRunnable { doCreateRule(type, source, target) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { actionDisposable.dispose() }
            ) { t: Throwable? -> Timber.e(t) }
    }

    private fun doCreateRule(type: AttributeType, source: String, target: String) {
        dao.insertRenamingRule(RenamingRule(type, source, target))
    }

    fun editRule(id: Long, source: String, target: String) {
        actionDisposable = Completable.fromRunnable { doEditRule(id, source, target) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { actionDisposable.dispose() }
            ) { t: Throwable? -> Timber.e(t) }
    }

    private fun doEditRule(id: Long, source: String, target: String) {
        val existingRule = dao.selectRenamingRule(id) ?: return
        existingRule.sourceName = source
        existingRule.targetName = target
        dao.insertRenamingRule(existingRule)
    }

    fun removeRules(itemIds: List<Long>) {
        actionDisposable = Completable.fromRunnable { doRemoveRules(itemIds) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { actionDisposable.dispose() }
            ) { t: Throwable? -> Timber.e(t) }
    }

    private fun doRemoveRules(itemIds: List<Long>) {
        dao.deleteRenamingRules(itemIds)
    }
}