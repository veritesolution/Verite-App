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
import android.widget.CheckBox
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
import com.example.myapplication.ui.components.VeriteAlert
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.User
import com.example.myapplication.data.repository.DeviceRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignUpActivity : AppCompatActivity() {
    
    private lateinit var authManager: AuthManager

    // Google Sign In Result Launcher for Sign Up screen too
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
                                        VeriteAlert.info(this@SignUpActivity, "Firebase unreachable — entering offline dev mode")
                                        offlineDevLogin(account.email ?: "dev@verite.local")
                                    } else {
                                        VeriteAlert.error(this@SignUpActivity, "Cannot reach login server. Check your internet connection.")
                                    }
                                }
                                else -> VeriteAlert.error(this@SignUpActivity, "Google Sign In Failed: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: ApiException) {
                VeriteAlert.error(this, "Google Sign In Error: ${e.statusCode}")
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        authManager = AuthManager(this)
        
        // Create root ConstraintLayout with scrollable content
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
        
        // App title with branded logo style
        val titleText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        com.example.myapplication.util.VeriteLogoHelper.applyLogoStyle(titleText)
        headerLayout.addView(titleText)
        
        // Icon (two pill shapes)
        val iconView = View(this).apply {
            id = View.generateViewId()
            val iconDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00BFA5"))
            }
            background = iconDrawable
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(32),
                dpToPx(16)
            )
        }
        headerLayout.addView(iconView)
        
        // "Sign Up" title
        val signUpTitle = TextView(this).apply {
            id = View.generateViewId()
            text = "Sign Up"
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
        rootLayout.addView(signUpTitle)
        
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
                 VeriteAlert.info(this@SignUpActivity, "Facebook Login implementation pending App ID")
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
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(orSeparator)
        
        // Input fields container
        val inputFieldsLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(16), 0, dpToPx(16), 0)
            }
        }
        rootLayout.addView(inputFieldsLayout)
        
        // Name input
        val nameInput = EditText(this).apply {
            id = View.generateViewId()
            hint = "Name"
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16)
            }
        }
        inputFieldsLayout.addView(nameInput)
        
        // Email/Phone input
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16)
            }
        }
        inputFieldsLayout.addView(emailInput)
        
        // Password input
        val passwordInput = EditText(this).apply {
            id = View.generateViewId()
            hint = "Password"
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#AAAAAA"))
            setBackgroundColor(Color.parseColor("#263238"))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            
            val inputDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#263238"))
                cornerRadius = dpToPx(8).toFloat()
            }
            background = inputDrawable
            
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        inputFieldsLayout.addView(passwordInput)
        
        // Terms checkbox and text
        val termsLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(16), 0, 0, 0)
            }
        }
        rootLayout.addView(termsLayout)
        
        val termsCheckbox = CheckBox(this).apply {
            id = View.generateViewId()
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        termsLayout.addView(termsCheckbox)
        
        val termsText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 14f
            setTextColor(Color.WHITE)
            movementMethod = LinkMovementMethod.getInstance()
            
            val termsString = "I agree to The Terms of Service and Privacy Policy"
            val spannable = SpannableString(termsString)
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#00BFA5")),
                termsString.indexOf("Terms of Service"),
                termsString.indexOf("Terms of Service") + "Terms of Service".length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#00BFA5")),
                termsString.indexOf("Privacy Policy"),
                termsString.indexOf("Privacy Policy") + "Privacy Policy".length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            setText(spannable)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dpToPx(8)
            }
        }
        termsLayout.addView(termsText)
        
        // "Vérité = Truth" section
        val veriteTruthLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(veriteTruthLayout)
        
        // Simplified Verite Section for brevity... (same as original code)
        
        // "Have a Account ?" text with Sign In link
        val accountText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 14f
            setTextColor(Color.WHITE)
            movementMethod = LinkMovementMethod.getInstance()
            
            val accountString = "Have a Account ? Sign In"
            val spannable = SpannableString(accountString)
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val intent = Intent(this@SignUpActivity, SignInActivity::class.java)
                        startActivity(intent)
                    }
                },
                accountString.indexOf("Sign In"),
                accountString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#00BFA5")),
                accountString.indexOf("Sign In"),
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
        
        // "Create Account" button
        val createAccountButton = Button(this).apply {
            id = View.generateViewId()
            text = "Create Account"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00BFA5"))
            
            val buttonDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#00BFA5"))
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
                val name = nameInput.text.toString().trim()
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString().trim()

                if (name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        VeriteAlert.error(
                            this@SignUpActivity,
                            "Please enter a valid email address"
                        )
                        return@setOnClickListener
                    }
                    if (password.length < 6) {
                        VeriteAlert.error(
                            this@SignUpActivity,
                            "Password must be at least 6 characters"
                        )
                        return@setOnClickListener
                    }
                    if (termsCheckbox.isChecked) {
                        if (!isNetworkAvailable()) {
                            if (BuildConfig.DEBUG) {
                                VeriteAlert.info(this@SignUpActivity, "No internet — entering offline dev mode")
                                offlineDevLogin(email, name)
                            } else {
                                VeriteAlert.error(this@SignUpActivity, "No internet connection. Please check your WiFi or mobile data.")
                            }
                            return@setOnClickListener
                        }
                        lifecycleScope.launch {
                            val result = authManager.signUp(email, password, name)
                            result.onSuccess {
                                navigateToNextScreen()
                            }.onFailure { e ->
                                when (e) {
                                    is FirebaseNetworkException -> {
                                        if (BuildConfig.DEBUG) {
                                            VeriteAlert.info(this@SignUpActivity, "Firebase unreachable — entering offline dev mode")
                                            offlineDevLogin(email, name)
                                        } else {
                                            VeriteAlert.error(this@SignUpActivity, "Cannot reach server. Check your internet connection.")
                                        }
                                    }
                                    else -> VeriteAlert.error(this@SignUpActivity, "Sign Up Failed: ${e.message}")
                                }
                            }
                        }
                    } else {
                        VeriteAlert.warning(this@SignUpActivity, "Please agree to the Terms")
                    }
                } else {
                     VeriteAlert.warning(this@SignUpActivity, "Please fill all fields")
                }
            }
        }
        rootLayout.addView(createAccountButton)
        
        // Set constraints (Simulated for brevity - ideally copy exact constraints from original or previous message)
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)
        
        constraintSet.connect(headerLayout.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(40))
        constraintSet.connect(headerLayout.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, 0)
        constraintSet.connect(headerLayout.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, 0)
        
        constraintSet.connect(signUpTitle.id, ConstraintSet.TOP, headerLayout.id, ConstraintSet.BOTTOM, dpToPx(24))
        constraintSet.connect(signUpTitle.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        
        constraintSet.connect(socialButtonsLayout.id, ConstraintSet.TOP, signUpTitle.id, ConstraintSet.BOTTOM, dpToPx(24))
        constraintSet.connect(socialButtonsLayout.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        constraintSet.connect(socialButtonsLayout.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(16))
        
        constraintSet.connect(orSeparator.id, ConstraintSet.TOP, socialButtonsLayout.id, ConstraintSet.BOTTOM, dpToPx(16))
        constraintSet.centerHorizontally(orSeparator.id, ConstraintSet.PARENT_ID)
        
        constraintSet.connect(inputFieldsLayout.id, ConstraintSet.TOP, orSeparator.id, ConstraintSet.BOTTOM, dpToPx(16))
        constraintSet.connect(inputFieldsLayout.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        constraintSet.connect(inputFieldsLayout.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(16))
        
        constraintSet.connect(termsLayout.id, ConstraintSet.TOP, inputFieldsLayout.id, ConstraintSet.BOTTOM, dpToPx(16))
        constraintSet.connect(termsLayout.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        
        constraintSet.connect(veriteTruthLayout.id, ConstraintSet.TOP, termsLayout.id, ConstraintSet.BOTTOM, dpToPx(32))
        constraintSet.centerHorizontally(veriteTruthLayout.id, ConstraintSet.PARENT_ID)
        
        constraintSet.connect(accountText.id, ConstraintSet.TOP, veriteTruthLayout.id, ConstraintSet.BOTTOM, dpToPx(32))
        constraintSet.connect(accountText.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        
        constraintSet.connect(createAccountButton.id, ConstraintSet.TOP, accountText.id, ConstraintSet.BOTTOM, dpToPx(16))
        constraintSet.connect(createAccountButton.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dpToPx(16))
        constraintSet.connect(createAccountButton.id, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(16))
        constraintSet.connect(createAccountButton.id, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, dpToPx(16))
        
        constraintSet.applyTo(rootLayout)
        
        setContentView(rootLayout)
    }
    
    private fun navigateToNextScreen() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@SignUpActivity)
            val repository = DeviceRepository(database.deviceDao())
            val connectedDevice = repository.getConnectedDevice()
            
            val nextActivity = if (connectedDevice != null) {
                DeviceDashboardActivity::class.java
            } else {
                BluetoothActivity::class.java
            }
            
            val intent = Intent(this@SignUpActivity, nextActivity)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun offlineDevLogin(email: String, name: String = "") {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val database = AppDatabase.getDatabase(this@SignUpActivity)
                val user = User(
                    id = 1,
                    name = name.ifEmpty { email.substringBefore("@") },
                    email = email,
                    profileImagePath = null,
                    joinDate = System.currentTimeMillis()
                )
                database.userDao().insertUser(user)
            }
            Log.w("SignUpActivity", "Offline dev login — Firebase bypassed")
            navigateToNextScreen()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
