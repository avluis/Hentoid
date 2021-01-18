package me.devsaki.hentoid.services;

import io.reactivex.disposables.Disposable;

public interface IDisposableHolder {
    void HoldDisposable(Disposable disposable);
}
