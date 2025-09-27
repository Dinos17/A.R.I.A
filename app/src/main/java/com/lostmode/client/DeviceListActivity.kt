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

/**
 * Displays all devices linked to the user's account.
 * Allows selecting a device to track or send commands.
 */
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
                    devices.add(Device(id, name))
                    Log.d(TAG, "Device loaded: $id / $name")
                }

                runOnUiThread { adapter.notifyDataSetChanged() }
            } catch (e: JSONException) {
                Log.e(TAG, "Failed to parse devices JSON", e)
                runOnUiThread {
                    Toast.makeText(this, "Error parsing devices data", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/**
 * Simple Device model
 */
data class Device(val id: String, val name: String)