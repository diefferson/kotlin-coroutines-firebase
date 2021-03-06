package me.arthurnagy.example

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.arthurnagy.kotlincoroutines.firebasecore.awaitResult
import me.arthurnagy.kotlincoroutines.firestore.awaitDeleteResult
import me.arthurnagy.kotlincoroutines.firestore.awaitGetResult
import me.arthurnagy.kotlincoroutines.firestore.awaitSetResult
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainViewModel : ViewModel(), CoroutineScope {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseFirestore = FirebaseFirestore.getInstance()
    private val userCollection = firebaseFirestore.collection(User.REFERENCE)
    val loggedInUser = MutableLiveData<User>()
    val isUserLoggedIn = MutableLiveData<Boolean>()
    val users = MutableLiveData<List<User>>()

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job


    init {
        loggedInUser.observeForever { isUserLoggedIn.value = it != null }
    }

    fun signIn(authCredential: AuthCredential) {
        launch {
            val firebaseUser: FirebaseUser
            /* Firestore with Kotlin coroutines .awaitX() approach
                In this case .await method can throw an exception, so we need to wrap them in a try catch block
            */
//            try {
//                val authenticatedUser = firebaseAuth.signInWithCredential(authCredential).await()
//                Log.d("MainViewModel", "signIn: successfully authenticated: ${authenticatedUser.user}")
//                firebaseUser = authenticatedUser.user
//                val user = User.create(firebaseUser)
//
//                userCollection.document(user.id).awaitSet(user)
//                Log.d("MainViewModel", "signIn: successfully created: $user")
//                loggedInUser.value = user
//            } catch (exception: Exception) {
//                Log.d("MainViewModel", "signIn: something went wrong: $exception")
//            }

            /* Firestore with Kotlin coroutines .awaitXResult() approach
                Here we get a Result<T> object which wraps the Success or Error of our coroutine call
             */
            val authenticatedUserResult = firebaseAuth.signInWithCredential(authCredential).awaitResult()
            when (authenticatedUserResult) {
                is me.arthurnagy.kotlincoroutines.firebasecore.Result.Success<AuthResult> -> {
                    firebaseUser = authenticatedUserResult.value.user
                    val user = User.create(firebaseUser)
                    val userResult = userCollection.document(user.id).awaitSetResult(user)
                    when (userResult) {
                        is me.arthurnagy.kotlincoroutines.firebasecore.Result.Success<*> -> loggedInUser.value = user
                        is me.arthurnagy.kotlincoroutines.firebasecore.Result.Error -> Log.d(
                            "MainViewModel",
                            "signIn: create user failed: ${userResult.exception}"
                        )
                    }
                }
                is me.arthurnagy.kotlincoroutines.firebasecore.Result.Error -> Log.d(
                    "MainViewModel",
                    "signIn: signInWithCredential failed: ${authenticatedUserResult.exception}"
                )
            }
        }
    }

    fun getUsers() {
        launch {
            //            try {
//                val userList: List<User> = userCollection.get().awaitGet()
//                users.value = userList
//            } catch (exception: Exception) {
//                Log.d("MainViewModel", "loadUsers: failed to get users: $exception")
//            }
            val usersResult: me.arthurnagy.kotlincoroutines.firebasecore.Result<List<User>> = userCollection.get().awaitGetResult()
            when (usersResult) {
                is me.arthurnagy.kotlincoroutines.firebasecore.Result.Success -> users.value = usersResult.value
                is me.arthurnagy.kotlincoroutines.firebasecore.Result.Error -> Log.d(
                    "MainViewModel",
                    "loadUsers: failed to get users: ${usersResult.exception}"
                )
            }
        }
    }

    fun addUser() {
        launch {
            val newUser = createUser()
            val newUserResult = userCollection.document(newUser.id).awaitSetResult(newUser)
            when (newUserResult) {
                is me.arthurnagy.kotlincoroutines.firebasecore.Result.Success<*> -> {
                    users.value?.let { currentUsers ->
                        val position = if (currentUsers.isNotEmpty()) (0..currentUsers.size).randomPosition() else 0
                        val newUsers = currentUsers.toMutableList().apply { add(position, newUser) }
                        users.value = newUsers
                    }
                }
                is me.arthurnagy.kotlincoroutines.firebasecore.Result.Error -> Log.d(
                    "MainViewModel",
                    "addUser: failed to save user: ${newUserResult.exception}"
                )
            }
        }
    }

    fun removeUser() {
        launch {
            users.value?.let { currentUsers ->
                if (currentUsers.isNotEmpty()) {
                    val position = (0..currentUsers.size).randomPosition()
                    val newUsers = currentUsers.toMutableList()
                    val userToRemove = newUsers.removeAt(position)
                    val removeUserResult = userCollection.document(userToRemove.id).awaitDeleteResult()
                    when (removeUserResult) {
                        is me.arthurnagy.kotlincoroutines.firebasecore.Result.Success -> users.value = newUsers
                        is me.arthurnagy.kotlincoroutines.firebasecore.Result.Error -> Log.d(
                            "MainViewModel",
                            "removeUser: failed to remove user: ${removeUserResult.exception}"
                        )
                    }
                }
            }
        }
    }

    private suspend fun createUser() = suspendCoroutine<User> { continuation ->
        val id = letters.randomString(16)
        val displayName = "${letters.randomString(6)} ${letters.randomString(4)}"
        val email = "${letters.randomString(5)}@${letters.randomString(5)}.com"
        continuation.resume(User(id, displayName, email, "https://source.unsplash.com/collection/888146/300x300"))
    }

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }


    private fun IntRange.randomPosition() = random.nextInt(endInclusive - start) + start

    private fun ClosedRange<Char>.randomString(length: Int) =
        (1..length).map { (random.nextInt(endInclusive.toInt() - start.toInt()) + start.toInt()).toChar() }.joinToString("")

    companion object {
        private val random = Random()
        private val letters = ('a'..'z')
    }

}