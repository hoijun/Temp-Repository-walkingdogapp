package com.example.walkingdogapp

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.walkingdogapp.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val myViewModel: LocateInfoViewModel by activityViewModels()


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), 999)
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater,container, false)
        
        //MainActivity에서 업데이트한 좌표
        myViewModel.currentCoord.observe(viewLifecycleOwner) {
            myViewModel.getCurrentAddress {
                binding.textLocation.text = it
            }
        }

        // 산책 시작
        binding.btnWalk.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                val builder = AlertDialog.Builder(requireContext())
                builder.setTitle("산책을 위해 위치 권한을 \n항상 허용으로 해주세요!")
                val listener = DialogInterface.OnClickListener { _, ans ->
                    when (ans) {
                        DialogInterface.BUTTON_POSITIVE -> {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            intent.data = Uri.fromParts("package", requireContext().packageName, null)
                            startActivity(intent)
                        }
                    }
                }
                builder.setPositiveButton("네", listener)
                builder.setNegativeButton("아니오", null)
                builder.show()
                return@setOnClickListener
            }
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
                == PackageManager.PERMISSION_GRANTED
            ) {
                val intent = Intent(requireContext(), WalkingActivity::class.java)
                startActivity(intent)
            } else {
                val builder = AlertDialog.Builder(requireContext())
                builder.setTitle("산책을 하기 위해 위치 권한을 \n항상 허용으로 해주세요!")
                val listener = DialogInterface.OnClickListener { _, ans ->
                    when (ans) {
                        DialogInterface.BUTTON_POSITIVE -> {
                            ActivityCompat.requestPermissions(
                                requireActivity(),
                                arrayOf(
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                ), 998
                            )
                        }
                    }
                }
                builder.setPositiveButton("네", listener)
                builder.setNegativeButton("아니오", null)
                builder.show()
            }
        }
        return binding.root
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}