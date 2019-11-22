package me.devsaki.hentoid.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import me.devsaki.hentoid.retrofit.GithubServer
import me.devsaki.hentoid.viewholders.GitHubRelease

class ChangelogViewModel : ViewModel() {

    val successValueLive = MutableLiveData<List<GitHubRelease.Struct>>()

    val errorValueLive = MutableLiveData<Throwable>()

    private val disposable = GithubServer.API.releases
        .observeOn(mainThread())
        .subscribe(successValueLive::setValue, errorValueLive::setValue)

    override fun onCleared() = disposable.dispose()
}