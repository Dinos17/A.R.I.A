package com.lostmode.client

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray

/**
 * Displays all devices linked to the user's account.
 * Allows selecting a device to track or send commands.
 */
class DeviceListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DeviceAdapter
    private val devices = mutableListOf<Device>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        recyclerView = findViewById(R.id.recyclerDevices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter(devices) { device ->
            // Open MainActivity for selected device
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("DEVICE_ID", device.id)
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        fetchDevices()
    }

    private fun fetchDevices() {
        NetworkClient.fetchDevices { jsonArray ->
            if (jsonArray == null) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to fetch devices", Toast.LENGTH_SHORT).show()
                }
                return@fetchDevices
            }

            devices.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                devices.add(Device(obj.getString("id"), obj.getString("name")))
            }

            runOnUiThread { adapter.notifyDataSetChanged() }
        }
    }
}

/**
 * Simple Device model
 */
data class Device(val id: String, val name: String)
