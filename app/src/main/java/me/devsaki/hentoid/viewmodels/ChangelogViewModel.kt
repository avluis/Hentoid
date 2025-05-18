package me.devsaki.hentoid.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.devsaki.hentoid.json.GithubRelease
import me.devsaki.hentoid.retrofit.GithubServer

class ChangelogViewModel : ViewModel() {

    val successValueLive = MutableLiveData<List<GithubRelease>>()

    val errorValueLive = MutableLiveData<Throwable>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                successValueLive.postValue(GithubServer.api.releases.execute().body())
            } catch (e: Exception) {
                errorValueLive.postValue(e)
            }
        }
    }
}