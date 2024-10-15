package com.bionime.blesamplecode.adapter

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bionime.blesamplecode.databinding.ItemDeviceListBinding

class BluetoothDeviceAdapter(
    private val deviceList: List<BluetoothDevice>,
    val onItemClickListener: OnItemClickListener
) : RecyclerView.Adapter<BluetoothDeviceAdapter.BluetoothDeviceViewHolder>() {
    interface OnItemClickListener {
        fun onItemClicked(device: BluetoothDevice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BluetoothDeviceViewHolder =
        BluetoothDeviceViewHolder(
            ItemDeviceListBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun getItemCount(): Int = deviceList.size

    override fun onBindViewHolder(holder: BluetoothDeviceViewHolder, position: Int) {
        val device = deviceList[position]
        holder.binding.textItemDeviceListName.text = device.name
    }

    inner class BluetoothDeviceViewHolder(val binding: ItemDeviceListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                onItemClickListener.onItemClicked(deviceList[adapterPosition])
            }
        }
    }
}