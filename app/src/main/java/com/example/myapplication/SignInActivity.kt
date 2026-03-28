package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.auth.AuthManager
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.User
import com.example.myapplication.data.repository.DeviceRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignInActivity : AppCompatActivity() {
    
    private var isPasswordVisible = false
    private lateinit var authManager: AuthManager

    // Google Sign In Result Launcher
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.let {
                    lifecycleScope.launch {
                        val authResult = authManager.signInWithGoogle(it)
                        authResult.onSuccess {
                            navigateToNextScreen()
                        }.onFailure { e ->
                            when (e) {
                                is FirebaseNetworkException -> {
                                    if (BuildConfig.DEBUG) {
                                        Toast.makeText(this@SignInActivity, "Firebase unreachable — entering offline dev mode", Toast.LENGTH_SHORT).show()
                                        offlineDevLogin(account.email ?: "dev@verite.local")
                                    } else {
                                        Toast.makeText(this@SignInActivity, "Cannot reach login server. Check your internet connection.", Toast.LENGTH_LONG).show()
                                    }
                                }
                                else -> Toast.makeText(this@SignInActivity, "Google Sign In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign In Error: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        authManager = AuthManager(this)

        // Check if user is already logged in
        if (authManager.currentUser != null) {
            navigateToNextScreen()
            return
        }
        
        // Create root ConstraintLayout
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#001A1A"))
        }
        
        // Header layout
        val headerLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(headerLayout)
        
        // Back button
        val backButton = Button(this).apply {
            id = View.generateViewId()
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                finish()
            }
        }
        headerLayout.addView(backButton)
        
        // App title
        val titleText = TextView(this).apply {
            id = View.generateViewId()
            text = "Vérité"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        headerLayout.addView(titleText)
        
        // "Sign In" title
        val signInTitle = TextView(this).apply {
            id = View.generateViewId()
            text = "Sign In"
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00BFA5"))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(16), 0, 0, 0)
            }
        }
        rootLayout.addView(signInTitle)
        
        // Icon (two pill shapes)
        val iconView = View(this).apply {
            id = View.generateViewId()
            val iconDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00BFA5"))
            }
            background = iconDrawable
            layoutParams = ConstraintLayout.LayoutParams(
                dpToPx(32),
                dpToPx(16)
            )
        }
        rootLayout.addView(iconView)
        
        // Social login buttons container
        val socialButtonsLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(16), 0, dpToPx(16), 0)
            }
        }
        rootLayout.addView(socialButtonsLayout)
        
        // Facebook button
        val facebookButton = Button(this).apply {
            id = View.generateViewId()
            text = "Facebook"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#263238"))
            
            val buttonDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#263238"))
                cornerRadius = dpToPx(8).toFloat()
            }
            background = buttonDrawable
            
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dpToPx(8)
            }
            setOnClickListener {
                 Toast.makeText(this@SignInActivity, "Facebook Login implementation pending App ID", Toast.LENGTH_SHORT).show()
            }
        }
        socialButtonsLayout.addView(facebookButton)
        
        // Google button
        val googleButton = Button(this).apply {
            id = View.generateViewId()
            text = "Google"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#263238"))
            
            val buttonDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#263238"))
                cornerRadius = dpToPx(8).toFloat()
            }
            background = buttonDrawable
            
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dpToPx(8)
            }
            setOnClickListener {
                val intent = authManager.getGoogleSignInIntent()
                googleSignInLauncher.launch(intent)
            }
        }
        socialButtonsLayout.addView(googleButton)
        
        // "Or" separator
        val orSeparator = TextView(this).apply {
            id = View.generateViewId()
            text = "Or"
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(orSeparator)
        
        // Email input
        val emailInput = EditText(this).apply {
            id = View.generateViewId()
            hint = "Email/Phone Number"
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#AAAAAA"))
            setBackgroundColor(Color.parseColor("#263238"))
            
            val inputDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#263238"))
                cornerRadius = dpToPx(8).toFloat()
            }
            background = inputDrawable
            
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(16))
            }
        }
        rootLayout.addView(emailInput)
        
        // Password input container
        val passwordContainer = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#263238"))
            
            val containerDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#263238"))
                cornerRadius = dpToPx(8).toFloat()
            }
            background = containerDrawable
            
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(16))
            }
        }
        rootLayout.addView(passwordContainer)
        
        val passwordInput = EditText(this).apply {
            id = View.generateViewId()
            hint = "Password"
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#AAAAAA"))
            setBackgroundColor(Color.TRANSPARENT)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            
            layoutParams = LinearLayout.LayoutParams(
                0,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        passwordContainer.addView(passwordInput)
        
        // Password visibility toggle
        val passwordToggle = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_view)
            setColorFilter(Color.parseColor("#AAAAAA"))
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(24),
                dpToPx(24)
            )
            setOnClickListener {
                isPasswordVisible = !isPasswordVisible
                passwordInput.inputType = if (isPasswordVisible) {
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }
        }
        passwordContainer.addView(passwordToggle)
        
        // Forgot password link
        val forgotPasswordText = TextView(this).apply {
            id = View.generateViewId()
            text = "Forgot Password?"
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.END
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, dpToPx(16), 0)
            }
            setOnClickListener {
                val email = emailInput.text.toString().trim()
                if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(
                        this@SignInActivity,
                        "Enter a valid email above to reset your password",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    val result = authManager.sendPasswordResetEmail(email)
                    result.onSuccess {
                        Toast.makeText(
                            this@SignInActivity,
                            "Password reset email sent",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { e ->
                        Toast.makeText(
                            this@SignInActivity,
                            "Reset failed: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        rootLayout.addView(forgotPasswordText)
        
        // App description section
        val descriptionLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(16), 0, dpToPx(16), 0)
            }
        }
        rootLayout.addView(descriptionLayout)
        
        val veriteTitle = TextView(this).apply {
            text = "Vérité"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        descriptionLayout.addView(veriteTitle)
        
        val whyTitle = TextView(this).apply {
            text = "Why was this formed?"
            textSize = 18f
            setTypeface(null, Typeface.NORMAL)
            setTextColor(Color.parseColor("#AAAAAA"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        descriptionLayout.addView(whyTitle)
        
        val descriptionText = TextView(this).apply {
            text = "\"This App is designed to help people manage stress early and naturally. By detecting tension, poor sleep, and mental strain through gentle monitoring, the wearable can offer calming guidance and prevent stress from building up. The aim is to restore balance in a comfortable, supportive way\""
            textSize = 14f
            setTextColor(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16)
            }
        }
        descriptionLayout.addView(descriptionText)
        
        val teamMessage = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            
            val teamString = "A Message from the Team of Vérité"
            val spannable = SpannableString(teamString)
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#00BFA5")),
                teamString.indexOf("Vérité"),
                teamString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            setText(spannable)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16)
            }
        }
        descriptionLayout.addView(teamMessage)
        
        // "Don't have a Account ?" text with Sign Up link
        val accountText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            movementMethod = LinkMovementMethod.getInstance()
            
            val accountString = "Don't have a Account ? Sign Up"
            val spannable = SpannableString(accountString)
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val intent = Intent(this@SignInActivity, SignUpActivity::class.java)
                        startActivity(intent)
                    }
                },
                accountString.indexOf("Sign Up"),
                accountString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#00BFA5")),
                accountString.indexOf("Sign Up"),
                accountString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            setText(spannable)
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(16), 0, 0, 0)
            }
        }
        rootLayout.addView(accountText)
        
        // "Log In" button
        val logInButton = Button(this).apply {
            id = View.generateViewId()
            text = "Log In"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1A3E3E"))
            
            val buttonDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#1A3E3E"))
                setStroke(dpToPx(2), Color.parseColor("#00BFA5"))
                cornerRadius = dpToPx(8).toFloat()
            }
            background = buttonDrawable
            
            setPadding(dpToPx(32), dpToPx(16), dpToPx(32), dpToPx(16))
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(16))
            }
            setOnClickListener {
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString().trim()

                if (email.isNotEmpty() && password.isNotEmpty()) {
                    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        Toast.makeText(
                            this@SignInActivity,
                            "Please enter a valid email address",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    // Pre-check network before calling Firebase
                    if (!isNetworkAvailable()) {
                        if (BuildConfig.DEBUG) {
                            // Dev bypass — proceed offline so app features can still be tested
                            Toast.makeText(this@SignInActivity, "No internet — entering offline dev mode", Toast.LENGTH_SHORT).show()
                            offlineDevLogin(email)
                        } else {
                            Toast.makeText(this@SignInActivity, "No internet connection. Please check your WiFi or mobile data.", Toast.LENGTH_LONG).show()
                        }
                        return@setOnClickListener
                    }

                    lifecycleScope.launch {
                        val result = authManager.signIn(email, password)
                        result.onSuccess {
                             navigateToNextScreen()
                        }.onFailure { e ->
                            when (e) {
                                is FirebaseNetworkException -> {
                                    if (BuildConfig.DEBUG) {
                                        Toast.makeText(this@SignInActivity, "Firebase unreachable — entering offline dev mode", Toast.LENGTH_SHORT).show()
                                        offlineDevLogin(email)
                                    } else {
                                        Toast.makeText(this@SignInActivity, "Cannot reach login server. Check your internet connection and try again.", Toast.LENGTH_LONG).show()
                                    }
                                }
                                else -> {
                                    Toast.makeText(this@SignInActivity, "Login Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } else {
                     Toast.makeText(this@SignInActivity, "Please enter email and password", Toast.LENGTH_SHORT).show()
                }
            }
        }
        rootLayout.addView(logInButton)
        
        // Set constraints
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)
        
        // Header
        constraintSet.connect(headerLayout.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(40))
        constraintSet.connect(headerLayout.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, 0)
        constraintSet.connect(headerLayout.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, 0)
        
        // Sign In title
        constraintSet.connect(signInTitle.id, ConstraintSet.TOP, headerLayout.id, ConstraintSet.BOTTOM, dpToPx(24))
        constraintSet.connect(signInTitle.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        
        // Icon
        constraintSet.connect(iconView.id, ConstraintSet.TOP, signInTitle.id, ConstraintSet.TOP, 0)
        constraintSet.connect(iconView.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(16))
        
        // Social buttons
        constraintSet.connect(socialButtonsLayout.id, ConstraintSet.TOP, signInTitle.id, ConstraintSet.BOTTOM, dpToPx(24))
        constraintSet.connect(socialButtonsLayout.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        constraintSet.connect(socialButtonsLayout.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(16))
        
        // Or separator
        constraintSet.connect(orSeparator.id, ConstraintSet.TOP, socialButtonsLayout.id, ConstraintSet.BOTTOM, dpToPx(16))
        constraintSet.centerHorizontally(orSeparator.id, ConstraintSet.PARENT_ID)
        
        // Email input
        constraintSet.connect(emailInput.id, ConstraintSet.TOP, orSeparator.id, ConstraintSet.BOTTOM, dpToPx(16))
        constraintSet.connect(emailInput.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        constraintSet.connect(emailInput.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(16))
        
        // Password container
        constraintSet.connect(passwordContainer.id, ConstraintSet.TOP, emailInput.id, ConstraintSet.BOTTOM, dpToPx(16))
        constraintSet.connect(passwordContainer.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        constraintSet.connect(passwordContainer.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(16))
        
        // Forgot password
        constraintSet.connect(forgotPasswordText.id, ConstraintSet.TOP, passwordContainer.id, ConstraintSet.BOTTOM, dpToPx(8))
        constraintSet.connect(forgotPasswordText.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(16))
        
        // Description layout
        constraintSet.connect(descriptionLayout.id, ConstraintSet.TOP, forgotPasswordText.id, ConstraintSet.BOTTOM, dpToPx(32))
        constraintSet.connect(descriptionLayout.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        constraintSet.connect(descriptionLayout.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(16))
        
        // Account text
        constraintSet.connect(accountText.id, ConstraintSet.TOP, descriptionLayout.id, ConstraintSet.BOTTOM, dpToPx(24))
        constraintSet.connect(accountText.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        
        // Log In button
        constraintSet.connect(logInButton.id, ConstraintSet.TOP, accountText.id, ConstraintSet.BOTTOM, dpToPx(16))
        constraintSet.connect(logInButton.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dpToPx(16))
        constraintSet.connect(logInButton.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        constraintSet.connect(logInButton.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(16))
        
        constraintSet.applyTo(rootLayout)
        
        setContentView(rootLayout)
    }
    
    private fun navigateToNextScreen() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@SignInActivity)
            val repository = DeviceRepository(database.deviceDao())
            val connectedDevice = repository.getConnectedDevice()
            
            val nextActivity = if (connectedDevice != null) {
                DeviceDashboardActivity::class.java
            } else {
                BluetoothActivity::class.java
            }
            
            val intent = Intent(this@SignInActivity, nextActivity)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
    
    /**
     * Check whether the device has an active internet-capable network.
     */
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Dev-mode offline login: creates a local-only user so the rest of the app
     * can be tested when Firebase Auth is unreachable (no internet / server down).
     * Only available in DEBUG builds.
     */
    private fun offlineDevLogin(email: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val database = AppDatabase.getDatabase(this@SignInActivity)
                val user = User(
                    id = 1,
                    name = email.substringBefore("@"),
                    email = email,
                    profileImagePath = null,
                    joinDate = System.currentTimeMillis()
                )
                database.userDao().insertUser(user)
            }
            Log.w("SignInActivity", "Offline dev login — Firebase bypassed")
            navigateToNextScreen()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}

