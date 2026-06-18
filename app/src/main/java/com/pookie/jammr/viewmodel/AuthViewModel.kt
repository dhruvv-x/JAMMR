package com.pookie.jammr.viewmodel

import androidx.credentials.Credential
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.pookie.jammr.data.repository.AuthRepository
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    val currentUser get() = repository.currentUser

    fun loginWithEmail(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.loginWithEmail(email, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrNull()!!)
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    fun registerWithEmail(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.registerWithEmail(email, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrNull()!!)
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }

    fun signInWithGoogle(credential: Credential) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.signInWithGoogle(credential)
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrNull()!!)
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Google Sign-In failed")
            }
        }
    }

    fun signOut() {
        repository.signOut()
        _authState.value = AuthState.Idle
    }
}