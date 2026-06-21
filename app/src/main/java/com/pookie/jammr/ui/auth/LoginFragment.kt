package com.pookie.jammr.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.pookie.jammr.R
import com.pookie.jammr.viewmodel.AuthState
import com.pookie.jammr.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private val authViewModel: AuthViewModel by viewModels()

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnGoogleSignIn: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etEmail = view.findViewById(R.id.etEmail)
        etPassword = view.findViewById(R.id.etPassword)
        btnLogin = view.findViewById(R.id.btnLogin)
        btnGoogleSignIn = view.findViewById(R.id.btnGoogleSignIn)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            authViewModel.loginWithEmail(email, password)
        }

        btnGoogleSignIn.setOnClickListener {
            launchGoogleSignIn()
        }

        view.findViewById<android.widget.TextView>(R.id.tvGoToRegister).setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        observeAuthState()
    }

    private fun launchGoogleSignIn() {
        val credentialManager = CredentialManager.create(requireContext())

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = requireContext()
                )
                authViewModel.signInWithGoogle(result.credential)
            } catch (e: GetCredentialException) {
                Log.e("LoginFragment", "Google Sign-In failed", e)
                Toast.makeText(requireContext(), "Google Sign-In cancelled or failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeAuthState() {
        authViewModel.authState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthState.Loading -> {
                    btnLogin.isEnabled = false
                    btnGoogleSignIn.isEnabled = false
                    btnLogin.text = "Logging in..."
                }
                is AuthState.Success -> {
                    btnLogin.isEnabled = true
                    btnGoogleSignIn.isEnabled = true
                    btnLogin.text = "Log In"
                    findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                }
                is AuthState.Error -> {
                    btnLogin.isEnabled = true
                    btnGoogleSignIn.isEnabled = true
                    btnLogin.text = "Log In"
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
                is AuthState.Idle -> {
                    btnLogin.isEnabled = true
                    btnGoogleSignIn.isEnabled = true
                    btnLogin.text = "Log In"
                }
            }
        }
    }
}