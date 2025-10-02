package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DeviceListActivity"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DeviceAdapter
    private val devices = mutableListOf<NetworkClient.Device>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        recyclerView = findViewById(R.id.recyclerDevices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter(devices) { device ->
            Log.i(TAG, "Device selected: ${device.id} / ${device.name}")
            // Persist selected device id for location updates
            getSharedPreferences("ARIA_PREFS", MODE_PRIVATE)
                .edit()
                .putInt("selected_device_id", device.id)
                .apply()
            showDeviceDashboard(device)
        }
        recyclerView.adapter = adapter

        fetchDevices()
    }

    private fun fetchDevices() {
        Log.i(TAG, "Fetching devices from server...")
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = NetworkClient.fetchDevices() // suspend function
                if (result.isSuccess) {
                    val deviceList = result.getOrNull() ?: emptyList()
                    Log.i(TAG, "Fetched ${deviceList.size} devices")
                    devices.clear()
                    devices.addAll(deviceList)
                    adapter.notifyDataSetChanged()

                    if (devices.size == 1) {
                        showDeviceDashboard(devices.first())
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to fetch devices"
                    Log.w(TAG, errorMsg)
                    Toast.makeText(this@DeviceListActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error fetching devices: ${ex.message}", ex)
                Toast.makeText(this@DeviceListActivity, "Error fetching devices", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeviceDashboard(device: NetworkClient.Device) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("DEVICE_ID", device.id)
            putExtra("DEVICE_NAME", device.name)
        }
        startActivity(intent)
        finish()
    }
}
