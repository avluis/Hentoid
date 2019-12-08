package me.devsaki.hentoid.parsers.images;

import me.devsaki.hentoid.parsers.ParseHelper;

class ParseProgress {

    private int currentStep;
    private int maxSteps;
    private boolean hasStarted = false;

    void start(int maxSteps) {
        currentStep = 0;
        this.maxSteps = maxSteps;
        ParseHelper.signalProgress(currentStep, maxSteps);
        hasStarted = true;
    }

    boolean hasStarted() {
        return hasStarted;
    }

    void advance() {
        ParseHelper.signalProgress(++currentStep, maxSteps);
    }

    void complete() {
        ParseHelper.signalProgress(maxSteps, maxSteps);
    }
}
