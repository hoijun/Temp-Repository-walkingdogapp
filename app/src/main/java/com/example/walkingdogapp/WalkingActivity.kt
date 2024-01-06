package com.example.walkingdogapp

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.walkingdogapp.databinding.ActivityWalkingBinding
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay

class WalkingActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityWalkingBinding
    private lateinit var mynavermap: NaverMap
    private lateinit var walkViewModel: LocateInfoViewModel

    private var coordList = mutableListOf<LatLng>()
    private var isTracking = false
    private var trackingMarker = Marker()
    private var trackingPath = PathOverlay()
    private lateinit var trackingCamera : CameraUpdate

    // 뒤로 가기
    private val BackPressCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            stopWalkingService()
            goHome()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWalkingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 보류
        walkViewModel = ViewModelProvider(this).get(LocateInfoViewModel::class.java)
        walkViewModel.getLastLocation()

        // 백그라운드 위치 서비스 시작
        startWalkingService()

        val mapFragment: MapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as MapFragment
        mapFragment.getMapAsync(this)

        walkViewModel.currentCoord.observe(this) {
            walkViewModel.getCurrentAddress { locateName ->
                binding.textLocation.text = locateName
            }
        }

        this.onBackPressedDispatcher.addCallback(this, BackPressCallback)

        // 트래킹 모드 여부 확인 및 여부에 따른 버튼과 텍스트 변경
        WalkingService.isTracking.observe(this) {
            this.isTracking = it
            binding.apply {
                if (isTracking) {
                    textIsTracking.text = "산책 중지하기"
                    btnStart.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    textIsTracking.text = "산책 시작하기"
                    btnStart.setImageResource(android.R.drawable.ic_media_play)
                }
            }
            Log.d("current coord", this.isTracking.toString())
        }
        
        // 버튼 이벤트 설정
        binding.apply {
            btnBack.setOnClickListener{
                stopWalkingService()
                goHome()
            }

            btnStart.setOnClickListener{
                if (isWalkingServiceRunning()) {
                    if(isTracking) {
                        stopTracking()
                    }
                    else {
                        startTracking()
                    }
                }
            }
        }
    }

    override fun onMapReady(map: NaverMap) {
        this.mynavermap = map
        mynavermap.uiSettings.isRotateGesturesEnabled = false
        mynavermap.uiSettings.isCompassEnabled = false

        trackingMarker.icon = OverlayImage.fromResource(R.mipmap.ic_launcher_round)
        trackingMarker.width = 100
        trackingMarker.height = 100
        trackingMarker.anchor = PointF(0.5f, 0.5f)

        trackingPath.width = 15
        trackingPath.color = Color.YELLOW

        walkViewModel.currentCoord.observe(this) {
            val firstCamera = CameraUpdate.scrollAndZoomTo(LatLng(it.latitude, it.longitude),
                17.0)
            mynavermap.moveCamera(firstCamera)
            trackingMarker.position = LatLng(it.latitude, it.longitude)
            trackingMarker.map = mynavermap
        }

        // 위치 업데이트 이벤트
        WalkingService.coordList.observe(this) {
            coordList = it
            if (coordList.isNotEmpty()) {
                trackingMarker.map = null
                trackingMarker.position = coordList.last()  // 현재 위치에 아이콘 설정
                trackingMarker.map = mynavermap
                trackingCamera =
                    CameraUpdate.scrollTo(coordList.last()).animate(CameraAnimation.Easing)  // 현재 위치로 카메라 이동
                mynavermap.moveCamera(trackingCamera)
                if (coordList.size > 1) {
                    trackingPath.map = null
                    trackingPath.coords = coordList // 이동 경로 그림
                    trackingPath.map = mynavermap
                    binding.InfoDistance.text = getString(R.string.totaldistance,
                        WalkingService.totalDistance / 1000.0)
                }
            }
        }

        WalkingService.walkTime.observe(this) {
            val minutes = it / 60
            val seconds = it % 60
            binding.InfoTime.text = getString(R.string.totaltime, minutes, seconds)
        }
    }

    private fun isWalkingServiceRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (myService in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (WalkingService::class.java.name == myService.service.className) {
                if (myService.foreground) {
                    return true
                }
            }
            return false
        }
        return false
    }

    private fun startWalkingService() {
        if (!isWalkingServiceRunning()) {
            Log.d("WalkingService", "start")
            val serviceIntent = Intent(this, WalkingService::class.java)
            serviceIntent.action = Constant.ACTION_START_Walking_SERVICE
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private fun stopWalkingService() {
        if (isWalkingServiceRunning()) {
            Log.d("WalkingService", "stop")
            val serviceIntent = Intent(this, WalkingService::class.java)
            serviceIntent.action = Constant.ACTION_STOP_Walking_SERVICE
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private fun startTracking() {
        val serviceIntent = Intent(this, WalkingService::class.java)
        serviceIntent.action = Constant.ACTION_START_Walking_Tracking
        startService(serviceIntent)
    }

    private fun stopTracking() {
        val serviceIntent = Intent(this, WalkingService::class.java)
        serviceIntent.action = Constant.ACTION_STOP_Walking_Tracking
        startService(serviceIntent)
    }

    private fun goHome() {
        val backIntent = Intent(this@WalkingActivity, MainActivity::class.java)
        startActivity(backIntent)
        finish()
    }
}