package com.lostmode.client

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val devices: List<NetworkClient.Device>,
    private val onClick: (NetworkClient.Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    companion object {
        private const val TAG = "DeviceAdapter"
    }

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtDeviceName)
        val imgPhoto: ImageView = view.findViewById(R.id.imgDevicePhoto)

        init {
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val device = devices[position]
                    Log.i(TAG, "Device clicked: ${device.id} / ${device.name}")
                    onClick(device)
                }
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
}