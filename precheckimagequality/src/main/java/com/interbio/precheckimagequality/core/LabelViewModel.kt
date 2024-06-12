package com.interbio.precheckimagequality.core

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LabelViewModel : ViewModel() {
    val label: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }
}