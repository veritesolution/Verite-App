package com.example.myapplication

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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet

class SignUpActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create root ConstraintLayout with scrollable content
        val rootLayout = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
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
        
        // App title with two-tone color
        val titleText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            
            val spannable = SpannableString("Vérité")
            spannable.setSpan(
                ForegroundColorSpan(Color.WHITE),
                0,
                2,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#00BFA5")),
                2,
                6,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            setText(spannable)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
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
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        // Handle Terms of Service click
                    }
                },
                termsString.indexOf("Terms of Service"),
                termsString.indexOf("Terms of Service") + "Terms of Service".length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#00BFA5")),
                termsString.indexOf("Terms of Service"),
                termsString.indexOf("Terms of Service") + "Terms of Service".length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        // Handle Privacy Policy click
                    }
                },
                termsString.indexOf("Privacy Policy"),
                termsString.indexOf("Privacy Policy") + "Privacy Policy".length,
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
        
        val veriteSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        veriteTruthLayout.addView(veriteSection)
        
        val veriteText = TextView(this).apply {
            text = "Vérité"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00BFA5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        veriteSection.addView(veriteText)
        
        val frenchText = TextView(this).apply {
            text = "French Derivative"
            textSize = 12f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        veriteSection.addView(frenchText)
        
        val equalsText = TextView(this).apply {
            text = "="
            textSize = 32f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(24), 0, dpToPx(24), 0)
            }
        }
        veriteTruthLayout.addView(equalsText)
        
        val truthSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        veriteTruthLayout.addView(truthSection)
        
        val truthText = TextView(this).apply {
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            
            val spannable = SpannableString("Truth")
            spannable.setSpan(
                ForegroundColorSpan(Color.WHITE),
                0,
                2,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#00BFA5")),
                2,
                5,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            setText(spannable)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        truthSection.addView(truthText)
        
        val englishText = TextView(this).apply {
            text = "English Meaning"
            textSize = 12f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        truthSection.addView(englishText)
        
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
        }
        rootLayout.addView(createAccountButton)
        
        // Set constraints
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)
        
        // Header
        constraintSet.connect(
            headerLayout.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP,
            dpToPx(40)
        )
        constraintSet.connect(
            headerLayout.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            0
        )
        constraintSet.connect(
            headerLayout.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
            0
        )
        
        // Sign Up title
        constraintSet.connect(
            signUpTitle.id,
            ConstraintSet.TOP,
            headerLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(24)
        )
        constraintSet.connect(
            signUpTitle.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            dpToPx(16)
        )
        
        // Social buttons
        constraintSet.connect(
            socialButtonsLayout.id,
            ConstraintSet.TOP,
            signUpTitle.id,
            ConstraintSet.BOTTOM,
            dpToPx(24)
        )
        constraintSet.connect(
            socialButtonsLayout.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            dpToPx(16)
        )
        constraintSet.connect(
            socialButtonsLayout.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
            dpToPx(16)
        )
        
        // Or separator
        constraintSet.connect(
            orSeparator.id,
            ConstraintSet.TOP,
            socialButtonsLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(16)
        )
        constraintSet.centerHorizontally(orSeparator.id, ConstraintSet.PARENT_ID)
        
        // Input fields
        constraintSet.connect(
            inputFieldsLayout.id,
            ConstraintSet.TOP,
            orSeparator.id,
            ConstraintSet.BOTTOM,
            dpToPx(16)
        )
        constraintSet.connect(
            inputFieldsLayout.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            dpToPx(16)
        )
        constraintSet.connect(
            inputFieldsLayout.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
            dpToPx(16)
        )
        
        // Terms
        constraintSet.connect(
            termsLayout.id,
            ConstraintSet.TOP,
            inputFieldsLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(16)
        )
        constraintSet.connect(
            termsLayout.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            dpToPx(16)
        )
        
        // Vérité = Truth section
        constraintSet.connect(
            veriteTruthLayout.id,
            ConstraintSet.TOP,
            termsLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(32)
        )
        constraintSet.centerHorizontally(veriteTruthLayout.id, ConstraintSet.PARENT_ID)
        
        // Account text
        constraintSet.connect(
            accountText.id,
            ConstraintSet.TOP,
            veriteTruthLayout.id,
            ConstraintSet.BOTTOM,
            dpToPx(32)
        )
        constraintSet.connect(
            accountText.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            dpToPx(16)
        )
        
        // Create Account button
        constraintSet.connect(
            createAccountButton.id,
            ConstraintSet.TOP,
            accountText.id,
            ConstraintSet.BOTTOM,
            dpToPx(16)
        )
        constraintSet.connect(
            createAccountButton.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM,
            dpToPx(16)
        )
        constraintSet.connect(
            createAccountButton.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT,
            dpToPx(16)
        )
        constraintSet.connect(
            createAccountButton.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT,
            dpToPx(16)
        )
        
        constraintSet.applyTo(rootLayout)
        
        setContentView(rootLayout)
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
