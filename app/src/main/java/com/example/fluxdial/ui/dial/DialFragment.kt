package com.example.fluxdial.ui.dial

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.fluxdial.OutgoingCallActivity
import com.example.fluxdial.databinding.FragmentDialBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DialFragment : Fragment() {

    private var _binding: FragmentDialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DialViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCall.setOnClickListener {
            val username = binding.etTargetUsername.text.toString().trim()
            if (username.isNotEmpty()) {
                viewModel.makeCall(username)
            } else {
                Toast.makeText(context, "Enter @username", Toast.LENGTH_SHORT).show()
            }
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.callInitiated.collectLatest { pair ->
                if (pair != null) {
                    val (callId, calleeUsername) = pair
                    val intent = Intent(requireContext(), OutgoingCallActivity::class.java).apply {
                        putExtra("callId", callId)
                        putExtra("calleeUsername", calleeUsername)
                    }
                    startActivity(intent)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { error ->
                if (error != null) {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.pbLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.btnCall.isEnabled = !isLoading
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
