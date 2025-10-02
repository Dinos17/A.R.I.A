package com.lostmode.client

import android.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * DeviceAdapter
 *
 * Displays registered devices in a RecyclerView and allows:
 * - Normal click → select device
 * - Long press → unregister device from server
 */
class DeviceAdapter(
    private val devices: MutableList<NetworkClient.Device>,
    private val onClick: (NetworkClient.Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    companion object {
        private const val TAG = "DeviceAdapter"
    }

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtDeviceName)
        val imgPhoto: ImageView = view.findViewById(R.id.imgDevicePhoto)

        init {
            // Single click → select device
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val device = devices[position]
                    Log.i(TAG, "Device clicked: ${device.id} / ${device.name}")
                    onClick(device)
                }
            }

            // Long click → unregister device
            view.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val device = devices[position]
                    confirmUnregister(device, position)
                }
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun getItemCount(): Int = devices.size

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.txtName.text = device.name
        Log.d(TAG, "Binding device at position $position: ${device.id} / ${device.name}")

        // Default offline icon (update if server supports photo URLs)
        holder.imgPhoto.setImageResource(R.drawable.ic_device_offline)
        // Uncomment below if device.photoUrl is available
        // Glide.with(holder.itemView.context)
        //     .load(device.photoUrl)
        //     .circleCrop()
        //     .into(holder.imgPhoto)
    }

    /**
     * Prompt user to confirm device unregistration.
     */
    private fun confirmUnregister(device: NetworkClient.Device, position: Int) {
        val context = itemView.context
        AlertDialog.Builder(context)
            .setTitle("Unregister Device")
            .setMessage("Do you want to unregister '${device.name}' from your account?")
            .setPositiveButton("Yes") { _, _ ->
                unregisterDevice(device, position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Unregister device from server and remove from list
     */
    private fun unregisterDevice(device: NetworkClient.Device, position: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val commandJson = JSONObject().apply {
                    put("unregister", true)
                }
                val result = NetworkClient.sendDeviceCommand(device.id.toString(), commandJson)
                result.onSuccess { success ->
                    if (success) {
                        Toast.makeText(
                            itemView.context,
                            "Device '${device.name}' unregistered",
                            Toast.LENGTH_SHORT
                        ).show()
                        devices.removeAt(position)
                        notifyItemRemoved(position)
                        Log.i(TAG, "Device unregistered: ${device.id}")
                    } else {
                        Toast.makeText(
                            itemView.context,
                            "Failed to unregister device '${device.name}'",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }.onFailure { ex ->
                    Toast.makeText(
                        itemView.context,
                        "Error unregistering device: ${ex.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e(TAG, "Unregister device error", ex)
                }
            } catch (ex: Exception) {
                Toast.makeText(
                    itemView.context,
                    "Exception: ${ex.message}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(TAG, "Exception during unregister", ex)
            }
        }
    }
}
