package com.chris.m3usuite.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn

class StartViewModel : ViewModel() {
    val query = MutableStateFlow("")
    val debouncedQuery = query
        .debounce(300)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
}

