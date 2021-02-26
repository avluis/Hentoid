package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;

import me.devsaki.hentoid.parsers.ParseHelper;

class ParseProgress {

    private String url;
    private int currentStep;
    private int maxSteps;
    private boolean hasStarted = false;

    void start(@NonNull final String url, int maxSteps) {
        this.url = url;
        currentStep = 0;
        this.maxSteps = maxSteps;
        ParseHelper.signalProgress(url, currentStep, maxSteps);
        hasStarted = true;
    }

    boolean hasStarted() {
        return hasStarted;
    }

    void advance() {
        ParseHelper.signalProgress(url, ++currentStep, maxSteps);
    }

    void complete() {
        ParseHelper.signalProgress(url, maxSteps, maxSteps);
    }
}
