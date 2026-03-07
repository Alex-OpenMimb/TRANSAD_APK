package com.transad.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.transad.app.api.ApiClient
import com.transad.app.api.LoginRequest
import com.transad.app.api.LoginResponse
import com.transad.app.databinding.ActivityLoginBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (SessionManager(this).isLoggedIn()) {
            irAMain()
            return
        }

        binding.btnLogin.setOnClickListener { intentarLogin() }
    }

    private fun intentarLogin() {
        val usuario = binding.etUsuario.text.toString().trim()
        val password = binding.etPassword.text.toString()

        binding.tilUsuario.error = null
        binding.tilPassword.error = null

        if (usuario.isEmpty() || password.isEmpty()) {
            if (usuario.isEmpty()) binding.tilUsuario.error = getString(R.string.login_error_campos)
            if (password.isEmpty()) binding.tilPassword.error = getString(R.string.login_error_campos)
            return
        }

        mostrarCargando(true)

        val request = LoginRequest(username = usuario, password = password)
        ApiClient.authApi.login(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(
                call: Call<LoginResponse>,
                response: Response<LoginResponse>
            ) {
                runOnUiThread { mostrarCargando(false) }
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val session = SessionManager(this@LoginActivity)
                        session.saveToken(body.accessToken, body.expiresIn)
                        runOnUiThread { irAMain() }
                    } else {
                        runOnUiThread { mostrarError(getString(R.string.login_error_credenciales)) }
                    }
                } else {
                    val msg = try {
                        response.errorBody()?.string() ?: getString(R.string.login_error_credenciales)
                    } catch (_: Exception) {
                        getString(R.string.login_error_credenciales)
                    }
                    runOnUiThread { mostrarError(msg) }
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                runOnUiThread {
                    mostrarCargando(false)
                    mostrarError(getString(R.string.login_error_red) + " " + t.message)
                }
            }
        })
    }

    private fun mostrarError(mensaje: String) {
        binding.tilPassword.error = mensaje
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    private fun irAMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun mostrarCargando(mostrar: Boolean) {
        binding.btnLogin.isEnabled = !mostrar
        binding.progressLogin.visibility = if (mostrar) View.VISIBLE else View.GONE
    }
}
