package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONException

class DeviceListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DeviceListActivity"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DeviceAdapter
    private val devices = mutableListOf<Device>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        recyclerView = findViewById(R.id.recyclerDevices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter(devices) { device ->
            Log.i(TAG, "Device selected: ${device.id} / ${device.name}")
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("DEVICE_ID", device.id)
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        fetchDevices()
    }

    private fun fetchDevices() {
        Log.i(TAG, "Fetching devices from server...")
        NetworkClient.fetchDevices { jsonArray ->
            if (jsonArray == null) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to fetch devices", Toast.LENGTH_SHORT).show()
                    Log.w(TAG, "fetchDevices returned null JSON array")
                }
                return@fetchDevices
            }

            try {
                devices.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", "unknown")
                    val name = obj.optString("name", "Unnamed Device")
                    val photoUrl = obj.optString("photo_url", "")
                    val device = Device(id, name, photoUrl)
                    devices.add(device)
                    Log.d(TAG, "Device loaded: $id / $name")
                }

                runOnUiThread {
                    adapter.notifyDataSetChanged()
                    // If only 1 device exists, go straight to dashboard
                    if (devices.size == 1) {
                        showDeviceDashboard(devices.first())
                    }
                }

            } catch (e: JSONException) {
                Log.e(TAG, "Failed to parse devices JSON", e)
                runOnUiThread {
                    Toast.makeText(this, "Error parsing devices data", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeviceDashboard(device: Device) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("DEVICE_ID", device.id)
            putExtra("DEVICE_NAME", device.name)
            putExtra("DEVICE_PHOTO", device.photoUrl)
        }
        startActivity(intent)
        finish()
    }
}

data class Device(val id: String, val name: String, val photoUrl: String = "")