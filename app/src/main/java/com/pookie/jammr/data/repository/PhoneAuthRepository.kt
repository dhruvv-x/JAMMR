package com.pookie.jammr.data.repository

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

sealed class OtpEvent {
    data class CodeSent(val verificationId: String) : OtpEvent()
    data class AutoVerified(val credential: PhoneAuthCredential) : OtpEvent()
    data class Failed(val message: String) : OtpEvent()
}

class PhoneAuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun startPhoneVerification(
        phoneNumber: String,
        activity: Activity,
        onEvent: (OtpEvent) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                onEvent(OtpEvent.AutoVerified(credential))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                onEvent(OtpEvent.Failed(e.message ?: "Verification failed"))
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                onEvent(OtpEvent.CodeSent(verificationId))
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /**
     * Confirms the OTP, links the credential to the signed-in account,
     * AND writes the verified phone number to the user's Firestore profile —
     * this last part was previously missing, which silently broke contact matching
     * since there was nothing in Firestore to match against.
     */
    suspend fun verifyOtpAndLink(verificationId: String, phoneNumber: String, code: String): Result<Unit> {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            val currentUser = auth.currentUser ?: return Result.failure(Exception("Not signed in"))
            currentUser.linkWithCredential(credential).await()

            db.collection("users").document(currentUser.uid)
                .update(
                    mapOf(
                        "phoneNumber" to phoneNumber,
                        "isPhoneVerified" to true
                    )
                ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}