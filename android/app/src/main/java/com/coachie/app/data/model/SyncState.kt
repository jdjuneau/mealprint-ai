package com.coachie.app.data.model

/**
 * Represents the state of a health data sync operation
 */
enum class SyncState {
    Idle,      // No sync in progress
    Loading,   // Sync currently in progress
    Success,   // Sync completed successfully
    Error      // Sync failed with an error
}
