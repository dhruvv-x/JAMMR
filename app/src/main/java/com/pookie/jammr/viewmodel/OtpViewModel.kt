package com.pookie.jammr.viewmodel

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pookie.jammr.data.repository.OtpEvent
import com.pookie.jammr.data.repository.PhoneAuthRepository
import kotlinx.coroutines.launch

sealed class OtpUiState {
    object EnterPhone : OtpUiState()
    object SendingCode : OtpUiState()
    data class EnterCode(val verificationId: String, val phoneNumber: String) : OtpUiState()
    object Verifying : OtpUiState()
    object Verified : OtpUiState()
    data class Error(val message: String) : OtpUiState()
}

class OtpViewModel : ViewModel() {

    private val repository = PhoneAuthRepository()

    private val _uiState = MutableLiveData<OtpUiState>(OtpUiState.EnterPhone)
    val uiState: LiveData<OtpUiState> = _uiState

    fun sendOtp(phoneNumber: String, activity: Activity) {
        _uiState.value = OtpUiState.SendingCode
        repository.startPhoneVerification(phoneNumber, activity) { event ->
            when (event) {
                is OtpEvent.CodeSent -> {
                    _uiState.value = OtpUiState.EnterCode(event.verificationId, phoneNumber)
                }
                is OtpEvent.AutoVerified -> {
                    _uiState.value = OtpUiState.Verified
                }
                is OtpEvent.Failed -> {
                    _uiState.value = OtpUiState.Error(event.message)
                }
            }
        }
    }

    fun verifyCode(verificationId: String, phoneNumber: String, code: String) {
        _uiState.value = OtpUiState.Verifying
        viewModelScope.launch {
            val result = repository.verifyOtpAndLink(verificationId, phoneNumber, code)
            _uiState.value = if (result.isSuccess) {
                OtpUiState.Verified
            } else {
                OtpUiState.Error(result.exceptionOrNull()?.message ?: "Invalid code")
            }
        }
    }

    fun resetToEnterPhone() {
        _uiState.value = OtpUiState.EnterPhone
    }
}