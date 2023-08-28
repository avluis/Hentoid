package me.devsaki.hentoid.core

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricApi
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import me.devsaki.hentoid.R

fun <T : Fragment> T.withArguments(bundleBlock: Bundle.() -> Unit): T {
    arguments = Bundle().apply(bundleBlock)
    return this
}

fun FragmentActivity.startBiometric(
    biometricAuthRequest: BiometricAuthRequest,
    lock: Boolean,
    resultHandler: Consumer<Boolean>,
    silentAuth: Boolean = false,
) {
    if (!BiometricManagerCompat.isBiometricReadyForUsage(biometricAuthRequest)) {
        if (!BiometricManagerCompat.isHardwareDetected(biometricAuthRequest))
            showAlertDialog(
                this,
                "No hardware for ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
            )
        else if (!BiometricManagerCompat.hasEnrolled(biometricAuthRequest)) {
            val result =
                BiometricManagerCompat.openSettings(this, biometricAuthRequest)
            showAlertDialog(
                this,
                "No enrolled biometric for - ${biometricAuthRequest.api}/${biometricAuthRequest.type}\nTrying to open system settings - $result",
            )
        } else if (BiometricManagerCompat.isLockOut(biometricAuthRequest))
            showAlertDialog(
                this,
                "Biometric sensor temporary locked for ${biometricAuthRequest.api}/${biometricAuthRequest.type}\nTry again later",
            )
        else if (BiometricManagerCompat.isBiometricSensorPermanentlyLocked(biometricAuthRequest))
            showAlertDialog(
                this,
                "Biometric sensor permanently locked for ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
            )
        resultHandler.invoke(false)
        return
    }

    BiometricLoggerImpl.e("CheckBiometric.start() for $biometricAuthRequest")
    val biometricPromptCompat = BiometricPromptCompat.Builder(
        biometricAuthRequest,
        this
    )
        .setTitle(R.string.biometric_dialog_title)
        .setSubtitle(if (lock) R.string.biometric_dialog_subtitle_lock else R.string.biometric_dialog_subtitle_unlock)
//        .setDescription("Biometric Description: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more")
//        .setNegativeButtonText("Cancel: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more")
        .also {
            if (silentAuth) it.enableSilentAuth()
        }
        .build()


    biometricPromptCompat.authenticate(object : BiometricPromptCompat.AuthenticationCallback() {
        override fun onSucceeded(confirmed: Set<AuthenticationResult>) {
            super.onSucceeded(confirmed)
//            ToastHelper.toast("Success")
            resultHandler.invoke(true)
        }

        override fun onCanceled() {
            BiometricLoggerImpl.e("CheckBiometric.onCanceled()")
//            ToastHelper.toast("Canceled")
            resultHandler.invoke(false)
        }

        override fun onFailed(
            reason: AuthenticationFailureReason?,
            dialogDescription: CharSequence?
        ) {
            BiometricLoggerImpl.e("CheckBiometric.onFailed() - $reason")
            resultHandler.invoke(false)
        }

        override fun onUIOpened() {
            BiometricLoggerImpl.e("CheckBiometric.onUIOpened()")
            //ToastHelper.toast("onUIOpened")
        }

        override fun onUIClosed() {
            BiometricLoggerImpl.e("CheckBiometric.onUIClosed()")
            //ToastHelper.toast("onUIClosed")
        }
    })
}

private fun showAlertDialog(context: Context, msg: String) {
    AlertDialog.Builder(context).setTitle(R.string.error).setMessage(msg)
        .setNegativeButton(android.R.string.cancel, null).show()
}

object BiometricsHelper {
    fun detectBestBiometric(): BiometricAuthRequest? {
        val iris = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_IRIS,
            BiometricConfirmation.ANY
        )
        val faceId = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_FACE,
            BiometricConfirmation.ANY
        )
        val fingerprint = BiometricAuthRequest(
            BiometricApi.AUTO,
            BiometricType.BIOMETRIC_FINGERPRINT,
            BiometricConfirmation.ANY
        )

        if (BiometricManagerCompat.isHardwareDetected(iris)
            && BiometricManagerCompat.hasEnrolled(iris)
        ) return iris

        if (BiometricManagerCompat.isHardwareDetected(faceId)
            && BiometricManagerCompat.hasEnrolled(faceId)
        ) return faceId

        if (BiometricManagerCompat.isHardwareDetected(fingerprint)
            && BiometricManagerCompat.hasEnrolled(fingerprint)
        ) return fingerprint

        return null
    }
}