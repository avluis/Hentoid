package com.thin.downloadmanager;

/**
 * Created by maniselvaraj on 15/4/15.
 */
public interface RetryPolicy {

    /**
     * Returns the current timeout (used for logging).
     */
    public int getCurrentTimeout();

    /**
     * Returns the current retry count (used for logging).
     */
    public int getCurrentRetryCount();

    /**
     * Return back off multiplier
     */
    public float getBackOffMultiplier();


    public void retry() throws RetryError;


}
