package com.example.walkingdogapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.naver.maps.geometry.LatLng


class WalkingService : Service() {
    private lateinit var locationRequest: LocationRequest
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var builder : NotificationCompat.Builder
    private var miscount = 0

    companion object {
        val coordList = MutableLiveData<MutableList<LatLng>>()
        val isTracking = MutableLiveData<Boolean>()
        var totalDistance = 0f
        var totalTime = 0
    }

    private fun postInitialValue() {
        coordList.postValue(mutableListOf())
        isTracking.postValue(false)
        totalDistance = 0f
        totalTime = 0
    }

    // 위치 업데이트
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            if (isTracking.value == true) {
                for (location in locationResult.locations) {
                    if (location != null) {
                        val pos = LatLng(location.latitude, location.longitude)

                        // 위치 업데이트 간의 거리가 클 경우 업데이트 안함 및 이 상황이 2번일 경우는 통과
                        if (coordList.value!!.isNotEmpty() &&
                            coordList.value!!.last().distanceTo(pos) > 5f &&
                            miscount < 2)
                        {
                            miscount++
                            Log.d(
                                "miscount", "$miscount"
                            )
                            return
                        }
                        coordList.value!!.apply {
                            add(pos)
                            coordList.postValue(this)
                            miscount = 0
                            Log.d("current coord", "$pos")
                        }
                        if (coordList.value!!.size > 1) {
                            totalDistance += coordList.value!!.last()
                                .distanceTo(coordList.value!![coordList.value!!.size - 2]).toFloat()
                            Log.d("current coord", "distance: $totalDistance")
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        postInitialValue()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent != null) {
            val action = intent.action
            if (action != null) {
                when(action) {
                    Constant.ACTION_START_Walking_SERVICE -> {
                        startLocationService()
                    }

                    Constant.ACTION_STOP_Walking_SERVICE -> {
                        stopLocationService()
                    }

                    Constant.ACTION_START_Walking_Tracking -> {
                        startTracking()
                    }

                    Constant.ACTION_STOP_Walking_Tracking -> {
                        stopTracking()
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun startLocationService() {
        val channelId = "WalkingDogApp_Channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val resultIntent = Intent(applicationContext, WalkingActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_IMMUTABLE)
        builder = NotificationCompat.Builder(applicationContext, channelId)
        builder.setSmallIcon(R.mipmap.ic_launcher)
        builder.setContentTitle("털뭉치")
        builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        builder.setContentText("산책중 이에요.")
        builder.setContentIntent(pendingIntent)
        builder.setAutoCancel(false)
        builder.priority = NotificationCompat.PRIORITY_MAX

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val notificationChannel = NotificationChannel(
                    channelId,
                    "Walking Service",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationChannel.description = "This channel is used by walking service"
                notificationManager.createNotificationChannel(notificationChannel)
            }
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        { return }

        locationRequest = LocationRequest.Builder(0)
            .setMinUpdateIntervalMillis(2500)
            .setMaxUpdateDelayMillis(2500)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationProviderClient.requestLocationUpdates(locationRequest,
            locationCallback,
            Looper.getMainLooper())

        isTracking.postValue(true)
        startForeground(Constant.Walking_SERVICE_ID, builder.build())
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        isTracking.postValue(true)
        fusedLocationProviderClient.requestLocationUpdates(locationRequest,
            locationCallback,
            Looper.getMainLooper()) // 위치 업데이트 재개
        Log.d("current coord", "Start Tracking")
    }

    private fun stopTracking() {
        isTracking.postValue(false)
        fusedLocationProviderClient.removeLocationUpdates(locationCallback) // 위치 업데이트 중단
        Log.d("current coord", "Stop Tracking")
    }

    private fun stopLocationService() {
        isTracking.postValue(false)
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        postInitialValue()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}