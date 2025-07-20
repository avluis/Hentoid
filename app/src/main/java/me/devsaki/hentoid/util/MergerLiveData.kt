import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

// Inspired by https://medium.com/nerd-for-tech/merging-livedata-like-you-need-it-3abcf6b756ca
sealed class MergerLiveData<TargetType> : MediatorLiveData<TargetType>() {

    class Two<FirstSourceType, SecondSourceType, TargetType>(
        private val firstSource: LiveData<FirstSourceType>,
        private val secondSource: LiveData<SecondSourceType>,
        private val distinctUntilChanged: Boolean = true,
        private val merging: (FirstSourceType, SecondSourceType) -> TargetType
    ) : MediatorLiveData<TargetType>() {
        override fun onActive() {
            super.onActive()

            addSource(firstSource) { value ->
                val newValue = merging(value, secondSource.value ?: return@addSource)
                postValue(
                    distinctUntilChanged = distinctUntilChanged,
                    newValue = newValue
                )
            }

            addSource(secondSource) { value ->
                val newValue = merging(firstSource.value ?: return@addSource, value)
                postValue(
                    distinctUntilChanged = distinctUntilChanged,
                    newValue = newValue
                )
            }
        }

        override fun onInactive() {
            removeSource(firstSource)
            removeSource(secondSource)
            super.onInactive()
        }
    }
}

private fun <T> MediatorLiveData<T>.postValue(
    distinctUntilChanged: Boolean,
    newValue: T
) {
    val value = value ?: postValue(newValue)

    if (distinctUntilChanged && value == newValue) return

    postValue(newValue)
}