package com.ialireza.calculategold

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.ialireza.calculategold.databinding.ActivityPriceListBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PriceListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPriceListBinding
    private lateinit var adapter: PriceAdapter
    private var currentType = "IR"
    private val handler = Handler(Looper.getMainLooper())
    private val priceFormatter = DecimalFormat("#,###.##", DecimalFormatSymbols(Locale.US))

    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchPrices()
            handler.postDelayed(this, 10 * 60 * 1000)
        }
    }

    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://admin.tablotala.app/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPriceListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.updatePadding(top = insets.top)
            view.updatePadding(bottom = insets.bottom)
            windowInsets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        setupRecyclerView()
        setupTabs()
        setupRefresh()

        fetchPrices()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(refreshRunnable, 10 * 60 * 1000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun setupRecyclerView() {
        adapter = PriceAdapter { item ->
            shareItem(item)
        }
        binding.rvPrices.layoutManager = LinearLayoutManager(this)
        binding.rvPrices.adapter = adapter

        // مخفی کردن دکمه هنگام اسکرول به پایین و نمایش هنگام اسکرول به بالا
        binding.rvPrices.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 10 && binding.fabShareAll.isExtended) {
                    binding.fabShareAll.shrink()
                } else if (dy < -10 && !binding.fabShareAll.isExtended) {
                    binding.fabShareAll.extend()
                }
                
                if (dy > 0) {
                    binding.fabShareAll.hide()
                } else if (dy < 0) {
                    binding.fabShareAll.show()
                }
            }
        })
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentType = if (tab?.position == 0) "IR" else "FR"
                fetchPrices()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setOnRefreshListener { fetchPrices() }

        binding.fabShareAll.setOnClickListener {
            shareCurrentMarket()
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> { fetchPrices(); true }
                R.id.action_share_all -> { shareCurrentMarket(); true }
                R.id.action_share_everything -> { shareEverything(); true }
                else -> false
            }
        }
    }

    private fun fetchPrices() {
        binding.progressBar.visibility = if (binding.swipeRefresh.isRefreshing) View.GONE else View.VISIBLE
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = apiService.getPrices(currentType)
                binding.swipeRefresh.isRefreshing = false
                binding.progressBar.visibility = View.GONE
                
                response.data?.let { items ->
                    val filtered = if (currentType == "IR") {
                        items.filterIndexed { index, item -> index > 0 && !item.title.isNullOrBlank() }
                    } else {
                        items.filter { !it.title.isNullOrBlank() }
                    }
                    adapter.updateData(filtered)
                    updateLastRefreshTime()
                    binding.fabShareAll.show()
                    binding.fabShareAll.extend()
                } ?: run { binding.tvError.visibility = View.VISIBLE }
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                binding.progressBar.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
            }
        }
    }

    private fun updateLastRefreshTime() {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        binding.toolbar.subtitle = getString(R.string.last_update, currentTime.toPersianDigits())
    }

    private fun formatPrice(price: String?): String {
        if (price == null) return ""
        return try {
            val cleanPrice = price.replace(",", "").replace(Regex("[^0-9.]"), "").toDouble()
            priceFormatter.format(cleanPrice)
        } catch (e: Exception) { price }
    }

    private fun String.toPersianDigits(): String {
        var result = this
        val persianDigits = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
        for (i in 0..9) { result = result.replace(i.toString(), persianDigits[i]) }
        return result
    }

    private fun shareItem(item: PriceItem) {
        val formattedPrice = formatPrice(item.price).toPersianDigits()
        val header = if (currentType == "IR") getString(R.string.share_header_ir) else getString(R.string.share_header_global)
        val unit = getUnit(item.title ?: "")
        val text = "$header\n  ${item.title}: 👈🏻 $formattedPrice$unit\n\n${getString(R.string.share_footer)}"
        startShareIntent(text)
    }

    private fun getUnit(title: String): String {
        return when {
            title.contains("انس") -> " دلار"
            currentType == "IR" -> " تومان"
            else -> " دلار"
        }
    }

    private fun shareCurrentMarket() {
        val items = adapter.getItems()
        if (items.isEmpty()) return
        val sb = StringBuilder()
        sb.append(if (currentType == "IR") getString(R.string.share_header_ir) else getString(R.string.share_header_global)).append("\n")
        items.forEach { item ->
            sb.append("  ${item.title}: 👈🏻 ${formatPrice(item.price).toPersianDigits()}${getUnit(item.title ?: "")}\n")
        }
        appendFooterAndShare(sb)
    }

    private fun shareEverything() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val irDeferred = async { apiService.getPrices("IR") }
                val frDeferred = async { apiService.getPrices("FR") }
                val irData = irDeferred.await().data?.filterIndexed { index, item -> index > 0 && !item.title.isNullOrBlank() }
                val frData = frDeferred.await().data?.filter { !it.title.isNullOrBlank() }
                
                val sb = StringBuilder()
                sb.append(getString(R.string.share_header_ir)).append("\n")
                irData?.forEach { sb.append("  ${it.title}: 👈🏻 ${formatPrice(it.price).toPersianDigits()} تومان\n") }
                sb.append("\n").append(getString(R.string.share_header_global)).append("\n")
                frData?.forEach { sb.append("  ${it.title}: 👈🏻 ${formatPrice(it.price).toPersianDigits()}${if (it.title?.contains("انس") == true) " دلار" else " دلار"}\n") }
                
                binding.progressBar.visibility = View.GONE
                appendFooterAndShare(sb)
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun appendFooterAndShare(sb: StringBuilder) {
        val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("fa", "IR"))
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("fa", "IR"))
        val fullDate = "${dateFormat.format(Date()).toPersianDigits()} ساعت ${timeFormat.format(Date()).toPersianDigits()}"
        sb.append("\n").append(getString(R.string.share_date_label, fullDate))
        sb.append("\n\n").append(getString(R.string.share_footer))
        startShareIntent(sb.toString())
    }

    private fun startShareIntent(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_text)))
    }
}
