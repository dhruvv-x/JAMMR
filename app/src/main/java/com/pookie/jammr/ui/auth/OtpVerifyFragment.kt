package com.pookie.jammr.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.pookie.jammr.R
import com.pookie.jammr.viewmodel.OtpUiState
import com.pookie.jammr.viewmodel.OtpViewModel

class OtpVerifyFragment : Fragment() {

    private val otpViewModel: OtpViewModel by viewModels()

    private lateinit var etPhone: TextInputEditText
    private lateinit var etOtp: TextInputEditText
    private lateinit var tilOtp: TextInputLayout
    private lateinit var btnAction: MaterialButton
    private var currentVerificationId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_otp_verify, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etPhone = view.findViewById(R.id.etPhone)
        etOtp = view.findViewById(R.id.etOtp)
        tilOtp = view.findViewById(R.id.tilOtp)
        btnAction = view.findViewById(R.id.btnAction)

        btnAction.setOnClickListener {
            val state = otpViewModel.uiState.value
            if (state is OtpUiState.EnterCode) {
                val code = etOtp.text.toString().trim()
                if (code.length == 6) {
                    otpViewModel.verifyCode(state.verificationId, state.phoneNumber, code)
                } else {
                    Toast.makeText(requireContext(), "Enter the 6-digit code", Toast.LENGTH_SHORT).show()
                }
            } else {
                val phone = etPhone.text.toString().trim()
                if (phone.isNotEmpty()) {
                    otpViewModel.sendOtp(phone, requireActivity())
                } else {
                    Toast.makeText(requireContext(), "Enter your phone number", Toast.LENGTH_SHORT).show()
                }
            }
        }

        view.findViewById<android.widget.TextView>(R.id.tvSkip).setOnClickListener {
            findNavController().navigate(R.id.action_otpVerifyFragment_to_homeFragment)
        }

        observeState()
    }

    private fun observeState() {
        otpViewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is OtpUiState.EnterPhone -> {
                    btnAction.isEnabled = true
                    btnAction.text = "Send Code"
                    tilOtp.visibility = View.GONE
                }
                is OtpUiState.SendingCode -> {
                    btnAction.isEnabled = false
                    btnAction.text = "Sending..."
                }
                is OtpUiState.EnterCode -> {
                    currentVerificationId = state.verificationId
                    btnAction.isEnabled = true
                    btnAction.text = "Verify Code"
                    tilOtp.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Code sent! (use your Firebase test code)", Toast.LENGTH_LONG).show()
                }
                is OtpUiState.Verifying -> {
                    btnAction.isEnabled = false
                    btnAction.text = "Verifying..."
                }
                is OtpUiState.Verified -> {
                    Toast.makeText(requireContext(), "Phone verified!", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_otpVerifyFragment_to_homeFragment)
                }
                is OtpUiState.Error -> {
                    btnAction.isEnabled = true
                    btnAction.text = if (currentVerificationId != null) "Verify Code" else "Send Code"
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}