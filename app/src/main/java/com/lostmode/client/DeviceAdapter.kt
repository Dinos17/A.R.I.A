package com.lostmode.client

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class DeviceAdapter(
    private val devices: List<Device>,
    private val onClick: (Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtDeviceName)
        val imgPhoto: ImageView = view.findViewById(R.id.imgDevicePhoto)

        init {
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onClick(devices[position])
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

        // Load device photo using Glide if available
        if (device.photoUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(device.photoUrl)
                .circleCrop()
                .into(holder.imgPhoto)
        } else {
            holder.imgPhoto.setImageResource(R.drawable.ic_device_offline) // default icon
        }
    }
}