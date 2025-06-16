package com.example.rastreamentoinfantil.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.rastreamentoinfantil.repository.FirebaseRepository

class FamilyViewModelFactory(
    private val repository: FirebaseRepository,
    private val currentUserId: String,
    private val currentUserEmail: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FamilyViewModel::class.java)) {
            return FamilyViewModel(repository, currentUserId, currentUserEmail) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

