package me.devsaki.hentoid.parsers;

class ParseProgress {

    private int currentStep;
    private int maxSteps;

    void progressStart(int maxSteps) {
        currentStep = 0;
        this.maxSteps = maxSteps;
        ParseHelper.signalProgress(currentStep, maxSteps);
    }

    void progressPlus() {
        ParseHelper.signalProgress(++currentStep, maxSteps);
    }

    void progressComplete() {
        ParseHelper.signalProgress(maxSteps, maxSteps);
    }
}
