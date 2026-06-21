package com.pookie.jammr.data.repository

import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.pookie.jammr.data.model.User
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun loginWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user!!
            createUserProfileIfNeeded(user.uid, user.displayName ?: "", user.email ?: email, user.photoUrl?.toString())
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerWithEmail(name: String, email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user!!
            createUserProfileIfNeeded(user.uid, name, email, null)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(credential: Credential): Result<FirebaseUser> {
        return try {
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(
                    googleIdTokenCredential.idToken, null
                )
                val result = auth.signInWithCredential(firebaseCredential).await()
                val user = result.user!!
                createUserProfileIfNeeded(
                    uid = user.uid,
                    name = googleIdTokenCredential.displayName ?: user.displayName ?: "",
                    email = user.email ?: "",
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString() ?: user.photoUrl?.toString()
                )
                Result.success(user)
            } else {
                Result.failure(Exception("Invalid credential type"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createUserProfileIfNeeded(uid: String, name: String, email: String, photoUrl: String?) {
        val userRef = db.collection("users").document(uid)
        val snapshot = userRef.get().await()
        if (!snapshot.exists()) {
            val newUser = User(
                uid = uid,
                name = name,
                email = email,
                photoUrl = photoUrl,
                bio = "",
                createdAt = System.currentTimeMillis()
            )
            userRef.set(newUser).await()
        }
    }

    fun signOut() {
        auth.signOut()
    }
}