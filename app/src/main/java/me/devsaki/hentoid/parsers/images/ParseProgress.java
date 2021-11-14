package me.devsaki.hentoid.parsers.images;

import me.devsaki.hentoid.parsers.ParseHelper;

class ParseProgress {

    private long contentId;
    private long storedId;
    private int currentStep;
    private int maxSteps;
    private boolean hasStarted = false;

    void start(long contentId, long storedId, int maxSteps) {
        this.contentId = contentId;
        this.storedId = storedId;
        currentStep = 0;
        this.maxSteps = maxSteps;
        ParseHelper.signalProgress(contentId, storedId, currentStep, maxSteps);
        hasStarted = true;
    }

    boolean hasStarted() {
        return hasStarted;
    }

    void advance() {
        ParseHelper.signalProgress(contentId, storedId, ++currentStep, maxSteps);
    }

    void complete() {
        ParseHelper.signalProgress(contentId, storedId, maxSteps, maxSteps);
    }
}
