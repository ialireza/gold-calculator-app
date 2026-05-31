package com.ialireza.calculategold

import com.google.gson.annotations.SerializedName

data class PriceResponse(
    @SerializedName("data") val data: List<PriceItem>?
)

data class PriceItem(
    @SerializedName("title") val title: String?,
    @SerializedName("price") val price: String?,
    @SerializedName("change") val change: String?,
    @SerializedName("status") val status: String?
)
