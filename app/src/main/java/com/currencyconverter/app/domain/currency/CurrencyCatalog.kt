package com.currencyconverter.app.domain.currency

import com.currencyconverter.app.domain.model.Currency
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every currency the app can display, roughly ordered from the largest to the smallest economy
 * (this is also the order shown in the currency picker). Covers the G7, all BRICS+ members,
 * every ASEAN member, and the rest of the world's ~100 largest economies. To support a new
 * currency add ONE line: ISO code, ISO country code for the flag, optional symbol and natural
 * fraction digits. The provider (see [com.currencyconverter.app.data.remote.ExchangeRateApi])
 * must quote it, otherwise the app shows "no rate available" for that pair.
 */
@Singleton
class CurrencyCatalog @Inject constructor() {

    val all: List<Currency> = ALL

    fun find(code: String): Currency? = all.firstOrNull { it.code == code }

    companion object {
        val ALL: List<Currency> = listOf(
            // G7 / major reserve currencies
            Currency("USD", "US", "$"),
            Currency("EUR", "EU", "€"),
            Currency("CNY", "CN", "CN¥"),
            Currency("JPY", "JP", "¥", fractionDigits = 0),
            Currency("INR", "IN", "₹"),
            Currency("GBP", "GB", "£"),
            Currency("BRL", "BR", "R$"), // BRICS
            Currency("CAD", "CA", "CA$"),
            Currency("RUB", "RU", "₽"), // BRICS
            Currency("MXN", "MX", "Mex$"),
            Currency("KRW", "KR", "₩", fractionDigits = 0),
            Currency("AUD", "AU", "A$"),
            Currency("CHF", "CH"),
            Currency("IDR", "ID", "Rp"), // ASEAN, BRICS
            Currency("TRY", "TR", "₺"),
            Currency("SAR", "SA"),
            Currency("TWD", "TW", "NT$"),
            Currency("PLN", "PL", "zł"),
            Currency("ARS", "AR"),
            Currency("SEK", "SE", "kr"),
            Currency("THB", "TH", "฿"), // ASEAN
            Currency("NGN", "NG", "₦"),
            Currency("ILS", "IL", "₪"),
            Currency("AED", "AE"), // BRICS
            Currency("EGP", "EG", "E£"), // BRICS
            Currency("NOK", "NO", "kr"),
            Currency("PHP", "PH", "₱"), // ASEAN
            Currency("BDT", "BD", "৳"),
            Currency("VND", "VN", "₫", fractionDigits = 0), // ASEAN
            Currency("MYR", "MY", "RM"), // ASEAN
            Currency("SGD", "SG", "S$"), // ASEAN
            Currency("DKK", "DK", "kr."),
            Currency("ZAR", "ZA", "R"), // BRICS
            Currency("HKD", "HK", "HK$"),
            Currency("COP", "CO"),
            Currency("PKR", "PK", "₨"),
            Currency("RON", "RO"),
            Currency("CLP", "CL", fractionDigits = 0),
            Currency("CZK", "CZ", "Kč"),
            Currency("IQD", "IQ", fractionDigits = 3),
            Currency("KZT", "KZ", "₸"),
            Currency("PEN", "PE"),
            Currency("NZD", "NZ", "NZ$"),
            Currency("HUF", "HU"),
            Currency("QAR", "QA"),
            Currency("KWD", "KW", fractionDigits = 3),
            Currency("UAH", "UA", "₴"),
            Currency("MAD", "MA"),
            Currency("ETB", "ET"), // BRICS
            Currency("KES", "KE"),
            Currency("IRR", "IR"), // BRICS
            Currency("DOP", "DO"),
            Currency("GTQ", "GT"),
            Currency("OMR", "OM", fractionDigits = 3),
            Currency("BGN", "BG"),
            Currency("LKR", "LK"),
            Currency("UZS", "UZ"),
            Currency("JOD", "JO", fractionDigits = 3),
            Currency("TZS", "TZ"),
            Currency("BHD", "BH", fractionDigits = 3),
            Currency("RSD", "RS"),
            Currency("GHS", "GH"),
            Currency("AZN", "AZ", "₼"),
            Currency("CRC", "CR"),
            Currency("UYU", "UY"),
            Currency("TND", "TN", fractionDigits = 3),
            Currency("KHR", "KH"), // ASEAN
            Currency("GEL", "GE", "₾"),
            Currency("ZMW", "ZM"),
            Currency("UGX", "UG", fractionDigits = 0),
            Currency("BOB", "BO"),
            Currency("HNL", "HN"),
            Currency("PYG", "PY", fractionDigits = 0),
            Currency("AMD", "AM"),
            Currency("ALL", "AL"),
            Currency("BWP", "BW"),
            Currency("JMD", "JM"),
            Currency("NPR", "NP"),
            Currency("TTD", "TT"),
            Currency("MKD", "MK"),
            Currency("BAM", "BA"),
            Currency("MNT", "MN", "₮"),
            Currency("MDL", "MD"),
            Currency("NAD", "NA"),
            Currency("PGK", "PG"),
            Currency("LAK", "LA"), // ASEAN
            Currency("KGS", "KG"),
            Currency("MUR", "MU"),
            Currency("ISK", "IS", fractionDigits = 0),
            Currency("AFN", "AF"),
            Currency("LYD", "LY"),
            Currency("FJD", "FJ"),
            Currency("SDG", "SD"),
            Currency("TJS", "TJ"),
            Currency("MMK", "MM"), // ASEAN
            Currency("BND", "BN"), // ASEAN
            Currency("TMT", "TM"),
            Currency("AOA", "AO"),
            Currency("MZN", "MZ"),
            Currency("DZD", "DZ"),
        )
    }
}
