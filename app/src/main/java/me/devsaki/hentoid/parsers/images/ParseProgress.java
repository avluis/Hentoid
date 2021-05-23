package me.devsaki.hentoid.parsers.images;

import me.devsaki.hentoid.parsers.ParseHelper;

class ParseProgress {

    private long contentId;
    private int currentStep;
    private int maxSteps;
    private boolean hasStarted = false;

    void start(long contentId, int maxSteps) {
        this.contentId = contentId;
        currentStep = 0;
        this.maxSteps = maxSteps;
        ParseHelper.signalProgress(contentId, currentStep, maxSteps);
        hasStarted = true;
    }

    boolean hasStarted() {
        return hasStarted;
    }

    void advance() {
        ParseHelper.signalProgress(contentId, ++currentStep, maxSteps);
    }

    void complete() {
        ParseHelper.signalProgress(contentId, maxSteps, maxSteps);
    }
}
