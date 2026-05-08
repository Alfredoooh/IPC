package com.nexa.ipc.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nexa.ipc.app.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActiviy : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClick.setOnClickListener {
            Snackbar.make(binding.root, "Botão clicado!", Snackbar.LENGTH_SHORT).show()
        }
    }
}