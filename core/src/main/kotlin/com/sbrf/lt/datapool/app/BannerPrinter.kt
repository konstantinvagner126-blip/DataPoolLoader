package com.sbrf.lt.datapool.app

object BannerPrinter {
    fun printBanner() {
        val banner = javaClass.classLoader.getResourceAsStream("banner.txt")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return
        println(banner)
    }
}
