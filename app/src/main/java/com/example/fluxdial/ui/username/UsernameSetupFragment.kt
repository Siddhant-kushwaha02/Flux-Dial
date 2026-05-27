package com.example.fluxdial.ui.username

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.fluxdial.R
import com.example.fluxdial.databinding.FragmentUsernameSetupBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UsernameSetupFragment : Fragment() {

    private var _binding: FragmentUsernameSetupBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: UsernameViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsernameSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.etUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val username = s.toString().trim().lowercase()
                viewModel.checkAvailability(username)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSave.setOnClickListener {
            val username = binding.etUsername.text.toString().trim().lowercase()
            if (username.isNotEmpty()) {
                viewModel.saveUsername(username)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isAvailable.collectLatest { available ->
                when (available) {
                    true -> {
                        binding.tvAvailability.visibility = View.VISIBLE
                        binding.tvAvailability.text = "Username is available!"
                        binding.tvAvailability.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
                        binding.btnSave.isEnabled = true
                    }
                    false -> {
                        binding.tvAvailability.visibility = View.VISIBLE
                        binding.tvAvailability.text = "Username is already taken."
                        binding.tvAvailability.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
                        binding.btnSave.isEnabled = false
                    }
                    null -> {
                        binding.tvAvailability.visibility = View.GONE
                        binding.btnSave.isEnabled = false
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isSaving.collectLatest { isSaving ->
                binding.pbLoading.visibility = if (isSaving) View.VISIBLE else View.GONE
                binding.btnSave.isEnabled = !isSaving && viewModel.isAvailable.value == true
                binding.etUsername.isEnabled = !isSaving
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.saveSuccess.collectLatest { success ->
                if (success) {
                    Toast.makeText(context, "Username saved successfully!", Toast.LENGTH_SHORT).show()
                    // Navigate to next screen or finish
                    activity?.onBackPressed()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
