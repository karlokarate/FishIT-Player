package com.chris.m3usuite.ui.actions

object MediaActionDefaults {
    fun testTagFor(id: MediaActionId): String = when (id) {
        MediaActionId.Resume -> "Action-Resume"
        MediaActionId.Play -> "Action-Play"
        MediaActionId.Trailer -> "Action-Trailer"
        MediaActionId.AddToList -> "Action-AddToList"
        MediaActionId.RemoveFromList -> "Action-RemoveFromList"
        MediaActionId.OpenEpg -> "Action-OpenEPG"
        MediaActionId.Share -> "Action-Share"
    }
}

