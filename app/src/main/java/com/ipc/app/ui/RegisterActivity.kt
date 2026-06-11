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
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.caverock.androidsvg.SVG
import com.ipc.app.MainActiviy
import com.ipc.app.R
import com.ipc.app.data.AuthApiService
import com.ipc.app.data.AuthResult
import kotlinx.coroutines.launch

class RegisterActivity : BaseActivity() {

    private lateinit var nameField: EditText
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var passwordConfirmField: EditText
    private lateinit var passwordToggle: ImageView
    private lateinit var passwordConfirmToggle: ImageView
    private lateinit var registerBtn: FrameLayout
    private lateinit var registerBtnText: TextView
    private lateinit var registerProgress: View
    private lateinit var errorText: TextView
    private lateinit var goLogin: TextView
    private lateinit var backBtn: ImageView
    private lateinit var scrollView: ScrollView
    private lateinit var googleBtn: FrameLayout
    private lateinit var googleIcon: ImageView

    private var passwordVisible = false
    private var passwordConfirmVisible = false
    private var isLoading = false

    private val prefs by lazy { getSharedPreferences("ipc_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        nameField             = findViewById(R.id.registerName)
        emailField            = findViewById(R.id.registerEmail)
        passwordField         = findViewById(R.id.registerPassword)
        passwordConfirmField  = findViewById(R.id.registerPasswordConfirm)
        passwordToggle        = findViewById(R.id.registerPasswordToggle)
        passwordConfirmToggle = findViewById(R.id.registerPasswordConfirmToggle)
        registerBtn           = findViewById(R.id.registerBtn)
        registerBtnText       = findViewById(R.id.registerBtnText)
        registerProgress      = findViewById(R.id.registerProgress)
        errorText             = findViewById(R.id.registerError)
        goLogin               = findViewById(R.id.registerGoLogin)
        backBtn               = findViewById(R.id.registerBack)
        scrollView            = findViewById(R.id.registerScrollView)
        googleBtn             = findViewById(R.id.registerGoogleBtn)
        googleIcon            = findViewById(R.id.registerGoogleIcon)

        applyFonts()
        setupToggles()
        setupBackBtn()
        setupGoogleBtn()
        setupActions()
        setupStatusBarInsets()   // ✅ resolve AppBar atrás da status bar
        setupKeyboardScroll()    // ✅ animação leve do teclado
    }

    private fun applyFonts() {
        runCatching {
            val tf = Typeface.createFromAsset(assets, "fonts/pattern/times_new_roman.ttf")
            findViewById<TextView>(R.id.registerTitle).typeface = Typeface.create(tf, Typeface.BOLD)
        }
    }

    private fun setupToggles() {
        val tint = ContextCompat.getColor(this, R.color.icon_tint_secondary)
        updateToggleIcon(passwordToggle, passwordVisible, tint)
        updateToggleIcon(passwordConfirmToggle, passwordConfirmVisible, tint)
        passwordToggle.setOnClickListener {
            passwordVisible = !passwordVisible
            setPasswordVisibility(passwordField, passwordVisible)
            updateToggleIcon(passwordToggle, passwordVisible, tint)
        }
        passwordConfirmToggle.setOnClickListener {
            passwordConfirmVisible = !passwordConfirmVisible
            setPasswordVisibility(passwordConfirmField, passwordConfirmVisible)
            updateToggleIcon(passwordConfirmToggle, passwordConfirmVisible, tint)
        }
    }

    private fun setPasswordVisibility(field: EditText, visible: Boolean) {
        val sel = field.selectionEnd
        field.inputType = if (visible)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        else
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        field.setSelection(sel.coerceAtLeast(0))
    }

    private fun updateToggleIcon(view: ImageView, visible: Boolean, tint: Int) {
        val icon = if (visible) "icons/svg/eye.svg" else "icons/svg/eye_closed.svg"
        view.setImageDrawable(svgDrawable(icon, 20, tint))
    }

    private fun setupBackBtn() {
        val tint = ContextCompat.getColor(this, R.color.icon_tint)
        backBtn.setImageDrawable(svgDrawable("icons/svg/back_arrow.svg", 18, tint))
        backBtn.setOnClickListener { finish() }
    }

    private fun setupGoogleBtn() {
        runCatching {
            val bmp = assets.open("icons/png/google.png").use { BitmapFactory.decodeStream(it) }
            googleIcon.setImageBitmap(bmp)
        }
        googleBtn.isClickable = false
        googleBtn.isFocusable = false
        googleBtn.alpha = 0.5f
    }

    private fun setupActions() {
        registerBtn.setOnClickListener {
            if (isLoading) return@setOnClickListener
            val name     = nameField.text.toString().trim()
            val email    = emailField.text.toString().trim()
            val pass     = passwordField.text.toString()
            val passConf = passwordConfirmField.text.toString()
            when {
                name.isEmpty() || email.isEmpty() || pass.isEmpty() || passConf.isEmpty() ->
                    showError("Preenche todos os campos.")
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    showError("Email inválido.")
                pass.length < 6 ->
                    showError("A password deve ter pelo menos 6 caracteres.")
                pass != passConf ->
                    showError("As passwords não coincidem.")
                else -> doRegister(name, email, pass)
            }
        }
        goLogin.setOnClickListener { finish() }
    }

    private fun setupStatusBarInsets() {
        val rootLayout = findViewById<View>(R.id.registerRoot)   // adicionaremos id ao root no XML
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, statusBarHeight, v.paddingRight, v.paddingBottom)
            insets
        }
    }

    private fun setupKeyboardScroll() {
        var lastTranslation = 0f
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val target = if (imeHeight > 0) -((imeHeight - navHeight).toFloat() * 0.12f) else 0f

            if (target != lastTranslation) {
                lastTranslation = target
                v.animate()
                    .translationY(target)
                    .setDuration(280)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            insets
        }
    }

    private fun doRegister(name: String, email: String, password: String) {
        setLoading(true)
        hideError()
        lifecycleScope.launch {
            when (val result = AuthApiService.register(name, email, password)) {
                is AuthResult.Success -> {
                    prefs.edit()
                        .putString("auth_token", result.data.token)
                        .putString("auth_user_id", result.data.id)
                        .putString("auth_user_name", result.data.name)
                        .putString("auth_user_email", result.data.email)
                        .apply()
                    startActivity(Intent(this@RegisterActivity, MainActiviy::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
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
        registerBtnText.visibility  = if (loading) View.INVISIBLE else View.VISIBLE
        registerProgress.visibility = if (loading) View.VISIBLE else View.GONE
        registerBtn.alpha = if (loading) 0.7f else 1f
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorText.alpha = 0f
        errorText.visibility = View.VISIBLE
        errorText.animate().alpha(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun hideError() { errorText.visibility = View.GONE }

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
        return BitmapDrawable(resources, bmp).also { it.setColorFilter(tint, PorterDuff.Mode.SRC_IN) }
    }
}