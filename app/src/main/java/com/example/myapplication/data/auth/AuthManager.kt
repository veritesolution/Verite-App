package com.example.myapplication.data.auth

import android.content.Context
import android.content.Intent
import com.example.myapplication.R
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.local.UserDao
import com.example.myapplication.data.model.User
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val userDao: UserDao = AppDatabase.getDatabase(context).userDao()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun signIn(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            result.user?.let { user ->
                syncUserToLocalDb(user)
                Result.success(user)
            } ?: Result.failure(Exception("Sign in failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(email: String, pass: String, name: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            result.user?.let { user ->
                syncUserToLocalDb(user, name)
                Result.success(user)
            } ?: Result.failure(Exception("Sign up failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getGoogleSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()
            result.user?.let { user ->
                syncUserToLocalDb(user)
                Result.success(user)
            } ?: Result.failure(Exception("Google sign in failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncUserToLocalDb(firebaseUser: FirebaseUser, name: String? = null) {
        withContext(Dispatchers.IO) {
            // Check if user exists locally
            val localUser = userDao.getUser(1).firstOrNull()
            
            val newName = name ?: firebaseUser.displayName ?: localUser?.name ?: "User"
            val newEmail = firebaseUser.email ?: localUser?.email ?: ""
            val imagePath = localUser?.profileImagePath // Keep local image for now
            
            val user = User(
                id = 1,
                name = newName,
                email = newEmail,
                profileImagePath = imagePath,
                joinDate = localUser?.joinDate ?: System.currentTimeMillis()
            )
            
            userDao.insertUser(user)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
