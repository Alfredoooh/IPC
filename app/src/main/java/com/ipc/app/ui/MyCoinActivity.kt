// MyCoinActivity.kt
package com.ipc.app.ui

import android.os.Bundle
import com.ipc.app.R

class MyCoinActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_coin)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        // Slide de volta: sai pela direita, MainActivity volta da esquerda
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}