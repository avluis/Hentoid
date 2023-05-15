package me.devsaki.hentoid.util

class TinyObservable<T>(val onNext: (Unit) -> T, val onComplete: () -> Unit)