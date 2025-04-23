package com.codingstuff.todolist;

/**
 * Interface for notifying when data has been refreshed
 */
public interface DataRefreshListener {
    /**
     * Called when data has been refreshed and UI should be updated
     */
    void onDataRefreshed();
}
