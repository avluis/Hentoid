package me.devsaki.hentoid.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import me.devsaki.hentoid.json.GithubRelease
import me.devsaki.hentoid.retrofit.GithubServer

class ChangelogViewModel : ViewModel() {

    val successValueLive = MutableLiveData<List<GithubRelease>>()

    val errorValueLive = MutableLiveData<Throwable>()

    private val disposable = GithubServer.api.releases
        .observeOn(mainThread())
        .subscribe(successValueLive::setValue, errorValueLive::setValue)

    override fun onCleared() = disposable.dispose()
}