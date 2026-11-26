package com.rega.navigation.ui

enum class RegaLanguage(val tag: String, val label: String, val rtl: Boolean) {
    FA("fa", "فارسی", true), EN("en", "English", false), AR("ar", "العربية", true),
    TR("tr", "Türkçe", false), CKB("ckb", "کوردی سۆرانی", true), KMR("kmr", "Kurdî Kurmancî", false)
}

data class Copy(
    val appName: String, val subtitle: String, val searchHint: String, val search: String,
    val myLocation: String, val route: String, val settings: String, val language: String,
    val locating: String, val locationDenied: String, val keyMissing: String,
    val searching: String, val routeReady: String, val routeFailed: String,
    val destination: String, val distance: String, val duration: String,
    val mapAttribution: String, val chooseLanguage: String
)

object RegaStrings {
    fun forLanguage(language: RegaLanguage) = when (language) {
        RegaLanguage.FA -> Copy("REGA", "مسیریاب هوشمند ایران", "نام شهر، خیابان یا مقصد را بنویسید", "جست‌وجو", "موقعیت من", "مسیریابی", "تنظیمات", "زبان برنامه", "در حال دریافت موقعیت…", "دسترسی موقعیت داده نشد", "سرویس نقشه آزاد در دسترس نیست؛ اتصال اینترنت را بررسی کنید", "در حال جست‌وجو…", "مسیر آماده است", "مسیریابی انجام نشد", "مقصد", "فاصله", "زمان تقریبی", "© OpenStreetMap contributors", "انتخاب زبان")
        RegaLanguage.EN -> Copy("REGA", "Smart Navigation of Iran", "Search city, street or destination", "Search", "My location", "Navigate", "Settings", "App language", "Getting location…", "Location permission denied", "Map service unavailable; check your internet connection", "Searching…", "Route is ready", "Could not calculate route", "Destination", "Distance", "ETA", "© OpenStreetMap contributors", "Choose language")
        RegaLanguage.AR -> Copy("REGA", "الملاحة الذكية في إيران", "ابحث عن مدينة أو شارع أو وجهة", "بحث", "موقعي", "ابدأ الملاحة", "الإعدادات", "لغة التطبيق", "جارٍ تحديد الموقع…", "لم يتم منح إذن الموقع", "خدمة الخريطة غير متاحة؛ تحقق من الاتصال بالإنترنت", "جارٍ البحث…", "المسار جاهز", "تعذر حساب المسار", "الوجهة", "المسافة", "الوقت المتوقع", "© OpenStreetMap contributors", "اختر اللغة")
        RegaLanguage.TR -> Copy("REGA", "İran Akıllı Navigasyon", "Şehir, cadde veya hedef ara", "Ara", "Konumum", "Yol tarifi", "Ayarlar", "Uygulama dili", "Konum alınıyor…", "Konum izni verilmedi", "Harita hizmeti kullanılamıyor; internet bağlantısını kontrol edin", "Aranıyor…", "Rota hazır", "Rota hesaplanamadı", "Hedef", "Mesafe", "Tahmini süre", "© OpenStreetMap contributors", "Dil seç")
        RegaLanguage.CKB -> Copy("REGA", "ڕێنیشاندەری زیرەکی ئێران", "شار یان شەقام یان شوێن بگەڕێ", "گەڕان", "شوێنی من", "ڕێنیشاندان", "ڕێکخستن", "زمانی بەرنامە", "شوێن دیاری دەکرێت…", "ڕێگەپێدانی شوێن نەدرا", "خزمەتگوزاری نەخشە بەردەست نییە؛ پەیوەندی ئینتەرنێت بپشکنە", "گەڕان…", "ڕێگا ئامادەیە", "هەژمارکردنی ڕێگا سەرکەوتوو نەبوو", "مەبەست", "دووری", "کاتی گەیشتن", "© OpenStreetMap contributors", "زمان هەڵبژێرە")
        RegaLanguage.KMR -> Copy("REGA", "Rêvebiriya Biaqil a Îranê", "Bajar, kolan an cihê armancê bigere", "Lêgerîn", "Cihê min", "Rê nîşan bide", "Mîheng", "Zimanê sepanê", "Cih tê dîtin…", "Destûra cihê nehat dayîn", "Xizmeta nexşeyê tune ye; girêdana înternetê kontrol bike", "Tê lêgerîn…", "Rê amade ye", "Rê nehat hesabkirin", "Armanc", "Dûrahî", "Dema texmînkirî", "© OpenStreetMap contributors", "Ziman hilbijêre")
    }
}
