package com.ialireza.calculategold

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ialireza.calculategold.databinding.ItemPriceBinding
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class PriceAdapter(
    private var items: List<PriceItem> = emptyList(),
    private val onShareClick: (PriceItem) -> Unit
) : RecyclerView.Adapter<PriceAdapter.ViewHolder>() {

    private val formatter = DecimalFormat("#,###.##", DecimalFormatSymbols(Locale.US))

    class ViewHolder(val binding: ItemPriceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPriceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvTitle.text = item.title
        
        // سه رقم سه رقم جدا کردن قیمت و تبدیل به فارسی
        holder.binding.tvPrice.text = formatPrice(item.price).toPersianDigits()
        
        if (!item.change.isNullOrEmpty()) {
            holder.binding.tvChange.visibility = View.VISIBLE
            holder.binding.tvChange.text = item.change.toPersianDigits()
            
            val color = when {
                item.status == "up" || item.change.contains("+") -> android.R.color.holo_green_dark
                item.status == "down" || item.change.contains("-") -> android.R.color.holo_red_dark
                else -> android.R.color.darker_gray
            }
            holder.binding.tvChange.setTextColor(ContextCompat.getColor(holder.itemView.context, color))
        } else {
            holder.binding.tvChange.visibility = View.GONE
        }

        holder.binding.btnShareItem.setOnClickListener { onShareClick(item) }
    }

    private fun formatPrice(price: String?): String {
        if (price == null) return ""
        return try {
            val cleanPrice = price.replace(",", "").replace(Regex("[^0-9.]"), "").toDouble()
            formatter.format(cleanPrice)
        } catch (e: Exception) {
            price
        }
    }

    private fun String.toPersianDigits(): String {
        var result = this
        val persianDigits = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
        for (i in 0..9) {
            result = result.replace(i.toString(), persianDigits[i])
        }
        return result
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<PriceItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItems(): List<PriceItem> = items
}
