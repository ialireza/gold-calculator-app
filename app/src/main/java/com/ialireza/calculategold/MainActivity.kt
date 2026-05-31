package com.ialireza.calculategold

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.security.ProviderInstaller
import com.ialireza.calculategold.databinding.ActivityMainBinding
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
    }
    private val decimalFormat = DecimalFormat("#,###.##", symbols)
    private val priceFormat = DecimalFormat("#,###", symbols)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // به‌روزرسانی امنیتی برای اندرویدهای قدیمی جهت حل مشکل SSL در لیست قیمت‌ها
        upgradeSecurityProvider()
        
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.etPrice.setText("")
        binding.etFee.setText("")
        binding.etWeight.setText("")

        setupFeeTypeSpinner()
        setupLiveCalculation()

        binding.btnCalculate.setOnClickListener {
            calculate(showFocus = true)
        }

        binding.btnShare.setOnClickListener {
            shareResult()
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_prices -> {
                    startActivity(Intent(this, PriceListActivity::class.java))
                    true
                }
                R.id.action_guide -> {
                    showGuideDialog()
                    true
                }
                R.id.action_about -> {
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun upgradeSecurityProvider() {
        ProviderInstaller.installIfNeededAsync(this, object : ProviderInstaller.ProviderInstallListener {
            override fun onProviderInstalled() {
                // امنیت شبکه با موفقیت ارتقا یافت
            }
            override fun onProviderInstallFailed(errorCode: Int, recoveryIntent: Intent?) {
                // ارتقای امنیت با خطا مواجه شد
            }
        })
    }

    private fun setupFeeTypeSpinner() {
        val items = arrayOf(getString(R.string.percent), getString(R.string.toman))
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
        (binding.spinnerFeeType as? AutoCompleteTextView)?.let {
            it.setAdapter(adapter)
            it.setOnItemClickListener { _, _, _, _ -> calculate() }
        }
    }

    private fun setupLiveCalculation() {
        val genericWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                calculate()
            }
        }

        binding.etFee.addTextChangedListener(genericWatcher)
        binding.etVat.addTextChangedListener(genericWatcher)
        binding.etProfit.addTextChangedListener(genericWatcher)
        binding.etWeight.addTextChangedListener(genericWatcher)

        binding.etPrice.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = normalizeDigits(s.toString())
                if (input != current) {
                    binding.etPrice.removeTextChangedListener(this)

                    val cleanString = input.replace(",", "")
                    if (cleanString.isNotEmpty()) {
                        try {
                            if (cleanString.length <= 15) {
                                val parsed = cleanString.toLong()
                                val formatted = priceFormat.format(parsed)
                                
                                val oldSelection = binding.etPrice.selectionStart
                                current = formatted
                                binding.etPrice.setText(formatted)
                                
                                val newSelection = calculateCursorPosition(input, formatted, oldSelection)
                                binding.etPrice.setSelection(newSelection.coerceIn(0, formatted.length))
                            }
                        } catch (e: Exception) {}
                    } else {
                        current = ""
                    }

                    binding.etPrice.addTextChangedListener(this)
                    calculate()
                }
            }
        })
    }

    private fun normalizeDigits(input: String): String {
        return input.replace("۰", "0").replace("۱", "1").replace("۲", "2")
            .replace("۳", "3").replace("۴", "4").replace("۵", "5")
            .replace("۶", "6").replace("۷", "7").replace("۸", "8")
            .replace("۹", "9").replace("٠", "0").replace("١", "1")
            .replace("٢", "2").replace("٣", "3").replace("٤", "4")
            .replace("٥", "5").replace("٦", "6").replace("٧", "7")
            .replace("٨", "8").replace("٩", "9")
    }

    private fun calculateCursorPosition(oldStr: String, newStr: String, oldPos: Int): Int {
        var digitCountBefore = 0
        for (i in 0 until oldPos.coerceAtMost(oldStr.length)) {
            if (oldStr[i].isDigit()) digitCountBefore++
        }
        var newPos = 0
        var digitCountAfter = 0
        while (newPos < newStr.length && digitCountAfter < digitCountBefore) {
            if (newStr[newPos].isDigit()) digitCountAfter++
            newPos++
        }
        return newPos
    }

    private fun calculate(showFocus: Boolean = false) {
        try {
            val priceStr = normalizeDigits(binding.etPrice.text.toString().replace(",", ""))
            val weightStr = normalizeDigits(binding.etWeight.text.toString())
            
            if (priceStr.isEmpty() || weightStr.isEmpty()) {
                binding.cardResult.visibility = View.GONE
                return
            }

            val price = priceStr.toDoubleOrNull() ?: return
            val weight = weightStr.toDoubleOrNull() ?: 0.0
            val feeInput = normalizeDigits(binding.etFee.text.toString()).toDoubleOrNull() ?: 0.0
            val feeType = binding.spinnerFeeType.text.toString()
            val vatPercent = normalizeDigits(binding.etVat.text.toString()).toDoubleOrNull() ?: 0.0
            val profitPercent = normalizeDigits(binding.etProfit.text.toString()).toDoubleOrNull() ?: 0.0

            if (weight <= 0) {
                binding.cardResult.visibility = View.GONE
                return
            }

            var constructionFeePerGram = feeInput
            if (feeType == getString(R.string.percent)) {
                constructionFeePerGram = (price * feeInput) / 100
            }

            val profitPerGram = ((price + constructionFeePerGram) * profitPercent) / 100
            val taxPerGram = ((constructionFeePerGram + profitPerGram) * vatPercent) / 100

            val totalPerGram = price + constructionFeePerGram + profitPerGram + taxPerGram
            val result = totalPerGram * weight

            binding.tvFeeResult.text = getString(R.string.toman_format, decimalFormat.format(constructionFeePerGram * weight))
            binding.tvProfitResult.text = getString(R.string.toman_format, decimalFormat.format(profitPerGram * weight))
            binding.tvTaxResult.text = getString(R.string.toman_format, decimalFormat.format(taxPerGram * weight))
            binding.tvTotalResult.text = getString(R.string.toman_format, decimalFormat.format(result))

            binding.cardResult.visibility = View.VISIBLE
            if (showFocus) {
                binding.cardResult.parent.requestChildFocus(binding.cardResult, binding.cardResult)
            }

        } catch (e: Exception) {
            binding.cardResult.visibility = View.GONE
        }
    }

    private fun shareResult() {
        val shareText = """
            📊 فاکتور محاسبه طلا
            
            💰 نرخ طلا: ${binding.etPrice.text} تومان
            ⚖️ وزن: ${binding.etWeight.text} گرم
            🛠 اجرت ساخت: ${binding.tvFeeResult.text}
            📈 سود فروشنده: ${binding.tvProfitResult.text}
            🧾 مالیات: ${binding.tvTaxResult.text}
            
            💵 مبلغ قابل پرداخت:
            ${binding.tvTotalResult.text}
            
            ✨ محاسبه شده توسط اپلیکیشن ${getString(R.string.app_name)}
            🌐 https://fornext.ir
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فاکتور"))
    }

    private fun showGuideDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.calculation_guide)
            .setMessage(R.string.guide_text)
            .setPositiveButton("متوجه شدم", null)
            .show()
    }

    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null, false)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnGithub).setOnClickListener {
            openUrl("https://github.com/ialireza/gold-calculator")
        }
        dialogView.findViewById<View>(R.id.btnWebsite).setOnClickListener {
            openUrl("https://fornext.ir")
        }

        dialog.show()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {}
    }
}
