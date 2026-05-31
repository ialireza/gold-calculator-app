package com.ialireza.calculategold

import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("api/tv/price")
    suspend fun getPrices(@Query("type") type: String): PriceResponse
}
