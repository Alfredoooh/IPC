// LoginActivity.kt
package com.ipc.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.caverock.androidsvg.SVG
import com.ipc.app.MainActiviy
import com.ipc.app.R
import com.ipc.app.data.AuthApiService
import com.ipc.app.data.AuthResult
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {

    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var passwordToggle: ImageView
    private lateinit var loginBtn: FrameLayout
    private lateinit var loginBtnText: TextView
    private lateinit var loginProgress: View
    private lateinit var errorText: TextView
    private lateinit var goRegister: TextView
    private lateinit var forgotPassword: TextView

    private var passwordVisible = false
    private var isLoading = false

    private val prefs by lazy { getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se já tem sessão, vai direto ao app
        if (prefs.getString("auth_token", null) != null) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        emailField     = findViewById(R.id.loginEmail)
        passwordField  = findViewById(R.id.loginPassword)
        passwordToggle = findViewById(R.id.loginPasswordToggle)
        loginBtn       = findViewById(R.id.loginBtn)
        loginBtnText   = findViewById(R.id.loginBtnText)
        loginProgress  = findViewById(R.id.loginProgress)
        errorText      = findViewById(R.id.loginError)
        goRegister     = findViewById(R.id.loginGoRegister)
        forgotPassword = findViewById(R.id.loginForgotPassword)

        applyFonts()
        setupLogo()
        setupPasswordToggle()
        setupActions()
    }

    private fun applyFonts() {
        runCatching {
            val tf = Typeface.createFromAsset(assets, "fonts/pattern/times_new_roman.ttf")
            findViewById<TextView>(R.id.loginTitle).typeface =
                Typeface.create(tf, Typeface.BOLD)
        }
    }

    private fun setupLogo() {
        runCatching {
            val bmp = assets.open("icons/png/logo.png").use { BitmapFactory.decodeStream(it) }
            findViewById<ImageView>(R.id.loginLogo).setImageBitmap(bmp)
        }
    }

    private fun setupPasswordToggle() {
        val tint = ContextCompat.getColor(this, R.color.icon_tint_secondary)
        updatePasswordToggleIcon(tint)
        passwordToggle.setOnClickListener {
            passwordVisible = !passwordVisible
            val selStart = passwordField.selectionStart
            val selEnd   = passwordField.selectionEnd
            passwordField.inputType = if (passwordVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordField.setSelection(selStart, selEnd)
            updatePasswordToggleIcon(tint)
        }
    }

    private fun updatePasswordToggleIcon(tint: Int) {
        val icon = if (passwordVisible) "icons/svg/lock_open.svg" else "icons/svg/lock.svg"
        passwordToggle.setImageDrawable(svgDrawable(icon, 20, tint))
    }

    private fun setupActions() {
        loginBtn.setOnClickListener {
            if (isLoading) return@setOnClickListener
            val email = emailField.text.toString().trim()
            val pass  = passwordField.text.toString()
            if (email.isEmpty() || pass.isEmpty()) {
                showError("Preenche todos os campos.")
                return@setOnClickListener
            }
            doLogin(email, pass)
        }

        goRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        forgotPassword.setOnClickListener {
            // TODO: implementar recuperação de password
        }
    }

    private fun doLogin(email: String, password: String) {
        setLoading(true)
        hideError()
        lifecycleScope.launch {
            when (val result = AuthApiService.login(email, password)) {
                is AuthResult.Success -> {
                    prefs.edit()
                        .putString("auth_token", result.data.token)
                        .putString("auth_user_id", result.data.id)
                        .putString("auth_user_name", result.data.name)
                        .putString("auth_user_email", result.data.email)
                        .apply()
                    goToMain()
                }
                is AuthResult.Error -> {
                    setLoading(false)
                    showError(result.message)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        loginBtnText.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        loginProgress.visibility = if (loading) View.VISIBLE else View.GONE
        loginBtn.alpha = if (loading) 0.7f else 1f
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorText.alpha = 0f
        errorText.visibility = View.VISIBLE
        errorText.animate()
            .alpha(1f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun hideError() {
        errorText.visibility = View.GONE
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActiviy::class.java))
        finish()
    }

    fun svgDrawable(path: String, sizeDp: Int, tint: Int): BitmapDrawable {
        val px  = (sizeDp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        runCatching {
            SVG.getFromAsset(assets, path).apply {
                documentWidth  = px.toFloat()
                documentHeight = px.toFloat()
                renderToCanvas(Canvas(bmp))
            }
        }
        return BitmapDrawable(resources, bmp).also {
            it.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        }
    }
}