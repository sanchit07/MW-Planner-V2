import { CurrencyInfo, GoalTypeConfig } from "../types/budget.types";

/* Currency mapping based on countryId Tempraray mapping, ideally this should be based on the actual country selected in the UI and not hardcoded here.
 This is just for demonstration purposes and should be replaced with a more dynamic solution in a real application.*/
export const COUNTRY_CURRENCY_MAP: Record<string, CurrencyInfo> = {
  // North America
  usa: { code: "USD", symbol: "$", label: "USD - US Dollar", isoCode: "US" },
  canada: {
    code: "CAD",
    symbol: "CA$",
    label: "CAD - Canadian Dollar",
    isoCode: "CA",
  },
  mexico: {
    code: "MXN",
    symbol: "MX$",
    label: "MXN - Mexican Peso",
    isoCode: "MX",
  },

  // Central America
  belize: {
    code: "BZD",
    symbol: "BZ$",
    label: "BZD - Belize Dollar",
    isoCode: "BZ",
  },
  "costa-rica": {
    code: "CRC",
    symbol: "₡",
    label: "CRC - Costa Rican Colón",
    isoCode: "CR",
  },
  "el-salvador": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "SV",
  },
  guatemala: {
    code: "GTQ",
    symbol: "Q",
    label: "GTQ - Guatemalan Quetzal",
    isoCode: "GT",
  },
  honduras: {
    code: "HNL",
    symbol: "L",
    label: "HNL - Honduran Lempira",
    isoCode: "HN",
  },
  nicaragua: {
    code: "NIO",
    symbol: "C$",
    label: "NIO - Nicaraguan Córdoba",
    isoCode: "NI",
  },
  panama: {
    code: "PAB",
    symbol: "B/.",
    label: "PAB - Panamanian Balboa",
    isoCode: "PA",
  },

  // Caribbean
  "antigua-and-barbuda": {
    code: "XCD",
    symbol: "EC$",
    label: "XCD - East Caribbean Dollar",
    isoCode: "AG",
  },
  aruba: {
    code: "AWG",
    symbol: "ƒ",
    label: "AWG - Aruban Florin",
    isoCode: "AW",
  },
  bahamas: {
    code: "BSD",
    symbol: "B$",
    label: "BSD - Bahamian Dollar",
    isoCode: "BS",
  },
  barbados: {
    code: "BBD",
    symbol: "Bds$",
    label: "BBD - Barbadian Dollar",
    isoCode: "BB",
  },
  bermuda: {
    code: "BMD",
    symbol: "$",
    label: "BMD - Bermudian Dollar",
    isoCode: "BM",
  },
  "cayman-islands": {
    code: "KYD",
    symbol: "$",
    label: "KYD - Cayman Islands Dollar",
    isoCode: "KY",
  },
  cuba: { code: "CUP", symbol: "₱", label: "CUP - Cuban Peso", isoCode: "CU" },
  curacao: {
    code: "ANG",
    symbol: "ƒ",
    label: "ANG - Netherlands Antillean Guilder",
    isoCode: "CW",
  },
  dominica: {
    code: "XCD",
    symbol: "EC$",
    label: "XCD - East Caribbean Dollar",
    isoCode: "DM",
  },
  "dominican-republic": {
    code: "DOP",
    symbol: "RD$",
    label: "DOP - Dominican Peso",
    isoCode: "DO",
  },
  grenada: {
    code: "XCD",
    symbol: "EC$",
    label: "XCD - East Caribbean Dollar",
    isoCode: "GD",
  },
  guadeloupe: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "GP" },
  haiti: {
    code: "HTG",
    symbol: "G",
    label: "HTG - Haitian Gourde",
    isoCode: "HT",
  },
  jamaica: {
    code: "JMD",
    symbol: "J$",
    label: "JMD - Jamaican Dollar",
    isoCode: "JM",
  },
  martinique: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "MQ" },
  "puerto-rico": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "PR",
  },
  "saint-kitts-and-nevis": {
    code: "XCD",
    symbol: "EC$",
    label: "XCD - East Caribbean Dollar",
    isoCode: "KN",
  },
  "saint-lucia": {
    code: "XCD",
    symbol: "EC$",
    label: "XCD - East Caribbean Dollar",
    isoCode: "LC",
  },
  "saint-vincent-and-the-grenadines": {
    code: "XCD",
    symbol: "EC$",
    label: "XCD - East Caribbean Dollar",
    isoCode: "VC",
  },
  "trinidad-and-tobago": {
    code: "TTD",
    symbol: "TT$",
    label: "TTD - Trinidad and Tobago Dollar",
    isoCode: "TT",
  },
  "turks-and-caicos": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "TC",
  },
  "virgin-islands": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "VI",
  },

  // South America
  argentina: {
    code: "ARS",
    symbol: "AR$",
    label: "ARS - Argentine Peso",
    isoCode: "AR",
  },
  bolivia: {
    code: "BOB",
    symbol: "Bs.",
    label: "BOB - Bolivian Boliviano",
    isoCode: "BO",
  },
  brazil: {
    code: "BRL",
    symbol: "R$",
    label: "BRL - Brazilian Real",
    isoCode: "BR",
  },
  chile: {
    code: "CLP",
    symbol: "CL$",
    label: "CLP - Chilean Peso",
    isoCode: "CL",
  },
  colombia: {
    code: "COP",
    symbol: "CO$",
    label: "COP - Colombian Peso",
    isoCode: "CO",
  },
  ecuador: {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "EC",
  },
  "french-guiana": {
    code: "EUR",
    symbol: "€",
    label: "EUR - Euro",
    isoCode: "GF",
  },
  guyana: {
    code: "GYD",
    symbol: "$",
    label: "GYD - Guyanese Dollar",
    isoCode: "GY",
  },
  paraguay: {
    code: "PYG",
    symbol: "₲",
    label: "PYG - Paraguayan Guaraní",
    isoCode: "PY",
  },
  peru: {
    code: "PEN",
    symbol: "S/",
    label: "PEN - Peruvian Sol",
    isoCode: "PE",
  },
  suriname: {
    code: "SRD",
    symbol: "$",
    label: "SRD - Surinamese Dollar",
    isoCode: "SR",
  },
  uruguay: {
    code: "UYU",
    symbol: "$U",
    label: "UYU - Uruguayan Peso",
    isoCode: "UY",
  },
  venezuela: {
    code: "VES",
    symbol: "Bs.",
    label: "VES - Venezuelan Bolívar",
    isoCode: "VE",
  },

  // Europe - Western
  andorra: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "AD" },
  austria: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "AT" },
  belgium: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "BE" },
  france: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "FR" },
  germany: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "DE" },
  ireland: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "IE" },
  liechtenstein: {
    code: "CHF",
    symbol: "CHF",
    label: "CHF - Swiss Franc",
    isoCode: "LI",
  },
  luxembourg: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "LU" },
  monaco: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "MC" },
  netherlands: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "NL" },
  switzerland: {
    code: "CHF",
    symbol: "CHF",
    label: "CHF - Swiss Franc",
    isoCode: "CH",
  },
  "united-kingdom": {
    code: "GBP",
    symbol: "£",
    label: "GBP - British Pound",
    isoCode: "GB",
  },

  // Europe - Northern
  denmark: {
    code: "DKK",
    symbol: "kr",
    label: "DKK - Danish Krone",
    isoCode: "DK",
  },
  estonia: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "EE" },
  "faroe-islands": {
    code: "DKK",
    symbol: "kr",
    label: "DKK - Danish Krone",
    isoCode: "FO",
  },
  finland: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "FI" },
  iceland: {
    code: "ISK",
    symbol: "kr",
    label: "ISK - Icelandic Króna",
    isoCode: "IS",
  },
  latvia: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "LV" },
  lithuania: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "LT" },
  norway: {
    code: "NOK",
    symbol: "kr",
    label: "NOK - Norwegian Krone",
    isoCode: "NO",
  },
  sweden: {
    code: "SEK",
    symbol: "kr",
    label: "SEK - Swedish Krona",
    isoCode: "SE",
  },

  // Europe - Southern
  albania: {
    code: "ALL",
    symbol: "L",
    label: "ALL - Albanian Lek",
    isoCode: "AL",
  },
  bosnia: {
    code: "BAM",
    symbol: "KM",
    label: "BAM - Bosnia-Herzegovina Convertible Mark",
    isoCode: "BA",
  },
  croatia: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "HR" },
  cyprus: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "CY" },
  gibraltar: {
    code: "GIP",
    symbol: "£",
    label: "GIP - Gibraltar Pound",
    isoCode: "GI",
  },
  greece: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "GR" },
  italy: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "IT" },
  kosovo: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "XK" },
  malta: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "MT" },
  montenegro: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "ME" },
  "north-macedonia": {
    code: "MKD",
    symbol: "ден",
    label: "MKD - Macedonian Denar",
    isoCode: "MK",
  },
  portugal: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "PT" },
  "san-marino": {
    code: "EUR",
    symbol: "€",
    label: "EUR - Euro",
    isoCode: "SM",
  },
  serbia: {
    code: "RSD",
    symbol: "дин",
    label: "RSD - Serbian Dinar",
    isoCode: "RS",
  },
  slovenia: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "SI" },
  spain: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "ES" },
  "vatican-city": {
    code: "EUR",
    symbol: "€",
    label: "EUR - Euro",
    isoCode: "VA",
  },

  // Europe - Eastern
  belarus: {
    code: "BYN",
    symbol: "Br",
    label: "BYN - Belarusian Ruble",
    isoCode: "BY",
  },
  bulgaria: {
    code: "BGN",
    symbol: "лв",
    label: "BGN - Bulgarian Lev",
    isoCode: "BG",
  },
  czechia: {
    code: "CZK",
    symbol: "Kč",
    label: "CZK - Czech Koruna",
    isoCode: "CZ",
  },
  hungary: {
    code: "HUF",
    symbol: "Ft",
    label: "HUF - Hungarian Forint",
    isoCode: "HU",
  },
  moldova: {
    code: "MDL",
    symbol: "L",
    label: "MDL - Moldovan Leu",
    isoCode: "MD",
  },
  poland: {
    code: "PLN",
    symbol: "zł",
    label: "PLN - Polish Złoty",
    isoCode: "PL",
  },
  romania: {
    code: "RON",
    symbol: "lei",
    label: "RON - Romanian Leu",
    isoCode: "RO",
  },
  russia: {
    code: "RUB",
    symbol: "₽",
    label: "RUB - Russian Ruble",
    isoCode: "RU",
  },
  slovakia: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "SK" },
  ukraine: {
    code: "UAH",
    symbol: "₴",
    label: "UAH - Ukrainian Hryvnia",
    isoCode: "UA",
  },

  // Asia - East
  china: {
    code: "CNY",
    symbol: "¥",
    label: "CNY - Chinese Yuan",
    isoCode: "CN",
  },
  "hong-kong": {
    code: "HKD",
    symbol: "HK$",
    label: "HKD - Hong Kong Dollar",
    isoCode: "HK",
  },
  japan: {
    code: "JPY",
    symbol: "¥",
    label: "JPY - Japanese Yen",
    isoCode: "JP",
  },
  macau: {
    code: "MOP",
    symbol: "MOP$",
    label: "MOP - Macanese Pataca",
    isoCode: "MO",
  },
  mongolia: {
    code: "MNT",
    symbol: "₮",
    label: "MNT - Mongolian Tögrög",
    isoCode: "MN",
  },
  "north-korea": {
    code: "KPW",
    symbol: "₩",
    label: "KPW - North Korean Won",
    isoCode: "KP",
  },
  "south-korea": {
    code: "KRW",
    symbol: "₩",
    label: "KRW - South Korean Won",
    isoCode: "KR",
  },
  taiwan: {
    code: "TWD",
    symbol: "NT$",
    label: "TWD - New Taiwan Dollar",
    isoCode: "TW",
  },

  // Asia - Southeast
  brunei: {
    code: "BND",
    symbol: "B$",
    label: "BND - Brunei Dollar",
    isoCode: "BN",
  },
  cambodia: {
    code: "KHR",
    symbol: "៛",
    label: "KHR - Cambodian Riel",
    isoCode: "KH",
  },
  "east-timor": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "TL",
  },
  indonesia: {
    code: "IDR",
    symbol: "Rp",
    label: "IDR - Indonesian Rupiah",
    isoCode: "ID",
  },
  laos: { code: "LAK", symbol: "₭", label: "LAK - Lao Kip", isoCode: "LA" },
  malaysia: {
    code: "MYR",
    symbol: "RM",
    label: "MYR - Malaysian Ringgit",
    isoCode: "MY",
  },
  myanmar: {
    code: "MMK",
    symbol: "K",
    label: "MMK - Myanmar Kyat",
    isoCode: "MM",
  },
  philippines: {
    code: "PHP",
    symbol: "₱",
    label: "PHP - Philippine Peso",
    isoCode: "PH",
  },
  singapore: {
    code: "SGD",
    symbol: "S$",
    label: "SGD - Singapore Dollar",
    isoCode: "SG",
  },
  thailand: {
    code: "THB",
    symbol: "฿",
    label: "THB - Thai Baht",
    isoCode: "TH",
  },
  vietnam: {
    code: "VND",
    symbol: "₫",
    label: "VND - Vietnamese Dong",
    isoCode: "VN",
  },

  // Asia - South
  afghanistan: {
    code: "AFN",
    symbol: "؋",
    label: "AFN - Afghan Afghani",
    isoCode: "AF",
  },
  bangladesh: {
    code: "BDT",
    symbol: "৳",
    label: "BDT - Bangladeshi Taka",
    isoCode: "BD",
  },
  bhutan: {
    code: "BTN",
    symbol: "Nu.",
    label: "BTN - Bhutanese Ngultrum",
    isoCode: "BT",
  },
  india: {
    code: "INR",
    symbol: "₹",
    label: "INR - Indian Rupee",
    isoCode: "IN",
  },
  maldives: {
    code: "MVR",
    symbol: "Rf",
    label: "MVR - Maldivian Rufiyaa",
    isoCode: "MV",
  },
  nepal: {
    code: "NPR",
    symbol: "Rs",
    label: "NPR - Nepalese Rupee",
    isoCode: "NP",
  },
  pakistan: {
    code: "PKR",
    symbol: "₨",
    label: "PKR - Pakistani Rupee",
    isoCode: "PK",
  },
  "sri-lanka": {
    code: "LKR",
    symbol: "Rs",
    label: "LKR - Sri Lankan Rupee",
    isoCode: "LK",
  },

  // Asia - Central
  kazakhstan: {
    code: "KZT",
    symbol: "₸",
    label: "KZT - Kazakhstani Tenge",
    isoCode: "KZ",
  },
  kyrgyzstan: {
    code: "KGS",
    symbol: "с",
    label: "KGS - Kyrgyzstani Som",
    isoCode: "KG",
  },
  tajikistan: {
    code: "TJS",
    symbol: "ЅМ",
    label: "TJS - Tajikistani Somoni",
    isoCode: "TJ",
  },
  turkmenistan: {
    code: "TMT",
    symbol: "m",
    label: "TMT - Turkmenistan Manat",
    isoCode: "TM",
  },
  uzbekistan: {
    code: "UZS",
    symbol: "so'm",
    label: "UZS - Uzbekistani Som",
    isoCode: "UZ",
  },

  // Middle East
  armenia: {
    code: "AMD",
    symbol: "֏",
    label: "AMD - Armenian Dram",
    isoCode: "AM",
  },
  azerbaijan: {
    code: "AZN",
    symbol: "₼",
    label: "AZN - Azerbaijani Manat",
    isoCode: "AZ",
  },
  bahrain: {
    code: "BHD",
    symbol: ".د.ب",
    label: "BHD - Bahraini Dinar",
    isoCode: "BH",
  },
  georgia: {
    code: "GEL",
    symbol: "₾",
    label: "GEL - Georgian Lari",
    isoCode: "GE",
  },
  iran: {
    code: "IRR",
    symbol: "﷼",
    label: "IRR - Iranian Rial",
    isoCode: "IR",
  },
  iraq: {
    code: "IQD",
    symbol: "ع.د",
    label: "IQD - Iraqi Dinar",
    isoCode: "IQ",
  },
  israel: {
    code: "ILS",
    symbol: "₪",
    label: "ILS - Israeli New Shekel",
    isoCode: "IL",
  },
  jordan: {
    code: "JOD",
    symbol: "د.ا",
    label: "JOD - Jordanian Dinar",
    isoCode: "JO",
  },
  kuwait: {
    code: "KWD",
    symbol: "د.ك",
    label: "KWD - Kuwaiti Dinar",
    isoCode: "KW",
  },
  lebanon: {
    code: "LBP",
    symbol: "ل.ل",
    label: "LBP - Lebanese Pound",
    isoCode: "LB",
  },
  oman: { code: "OMR", symbol: "﷼", label: "OMR - Omani Rial", isoCode: "OM" },
  palestine: {
    code: "ILS",
    symbol: "₪",
    label: "ILS - Israeli New Shekel",
    isoCode: "PS",
  },
  qatar: {
    code: "QAR",
    symbol: "﷼",
    label: "QAR - Qatari Riyal",
    isoCode: "QA",
  },
  "saudi-arabia": {
    code: "SAR",
    symbol: "﷼",
    label: "SAR - Saudi Riyal",
    isoCode: "SA",
  },
  syria: {
    code: "SYP",
    symbol: "£",
    label: "SYP - Syrian Pound",
    isoCode: "SY",
  },
  turkey: {
    code: "TRY",
    symbol: "₺",
    label: "TRY - Turkish Lira",
    isoCode: "TR",
  },
  "united-arab-emirates": {
    code: "AED",
    symbol: "د.إ",
    label: "AED - UAE Dirham",
    isoCode: "AE",
  },
  yemen: {
    code: "YER",
    symbol: "﷼",
    label: "YER - Yemeni Rial",
    isoCode: "YE",
  },

  // Africa - North
  algeria: {
    code: "DZD",
    symbol: "د.ج",
    label: "DZD - Algerian Dinar",
    isoCode: "DZ",
  },
  egypt: {
    code: "EGP",
    symbol: "£",
    label: "EGP - Egyptian Pound",
    isoCode: "EG",
  },
  libya: {
    code: "LYD",
    symbol: "ل.د",
    label: "LYD - Libyan Dinar",
    isoCode: "LY",
  },
  mauritania: {
    code: "MRU",
    symbol: "UM",
    label: "MRU - Mauritanian Ouguiya",
    isoCode: "MR",
  },
  morocco: {
    code: "MAD",
    symbol: "د.م.",
    label: "MAD - Moroccan Dirham",
    isoCode: "MA",
  },
  sudan: {
    code: "SDG",
    symbol: "ج.س.",
    label: "SDG - Sudanese Pound",
    isoCode: "SD",
  },
  tunisia: {
    code: "TND",
    symbol: "د.ت",
    label: "TND - Tunisian Dinar",
    isoCode: "TN",
  },
  "western-sahara": {
    code: "MAD",
    symbol: "د.م.",
    label: "MAD - Moroccan Dirham",
    isoCode: "EH",
  },

  // Africa - West
  benin: {
    code: "XOF",
    symbol: "Fr",
    label: "XOF - West African CFA Franc",
    isoCode: "BJ",
  },
  "burkina-faso": {
    code: "XOF",
    symbol: "Fr",
    label: "XOF - West African CFA Franc",
    isoCode: "BF",
  },
  "cape-verde": {
    code: "CVE",
    symbol: "$",
    label: "CVE - Cape Verdean Escudo",
    isoCode: "CV",
  },
  "ivory-coast": {
    code: "XOF",
    symbol: "Fr",
    label: "XOF - West African CFA Franc",
    isoCode: "CI",
  },
  gambia: {
    code: "GMD",
    symbol: "D",
    label: "GMD - Gambian Dalasi",
    isoCode: "GM",
  },
  ghana: {
    code: "GHS",
    symbol: "₵",
    label: "GHS - Ghanaian Cedi",
    isoCode: "GH",
  },
  guinea: {
    code: "GNF",
    symbol: "Fr",
    label: "GNF - Guinean Franc",
    isoCode: "GN",
  },
  "guinea-bissau": {
    code: "XOF",
    symbol: "Fr",
    label: "XOF - West African CFA Franc",
    isoCode: "GW",
  },
  liberia: {
    code: "LRD",
    symbol: "$",
    label: "LRD - Liberian Dollar",
    isoCode: "LR",
  },
  mali: {
    code: "XOF",
    symbol: "Fr",
    label: "XOF - West African CFA Franc",
    isoCode: "ML",
  },
  niger: {
    code: "XOF",
    symbol: "Fr",
    label: "XOF - West African CFA Franc",
    isoCode: "NE",
  },
  nigeria: {
    code: "NGN",
    symbol: "₦",
    label: "NGN - Nigerian Naira",
    isoCode: "NG",
  },
  senegal: {
    code: "XOF",
    symbol: "Fr",
    label: "XOF - West African CFA Franc",
    isoCode: "SN",
  },
  "sierra-leone": {
    code: "SLL",
    symbol: "Le",
    label: "SLL - Sierra Leonean Leone",
    isoCode: "SL",
  },
  togo: {
    code: "XOF",
    symbol: "Fr",
    label: "XOF - West African CFA Franc",
    isoCode: "TG",
  },

  // Africa - Central
  cameroon: {
    code: "XAF",
    symbol: "Fr",
    label: "XAF - Central African CFA Franc",
    isoCode: "CM",
  },
  "central-african-republic": {
    code: "XAF",
    symbol: "Fr",
    label: "XAF - Central African CFA Franc",
    isoCode: "CF",
  },
  chad: {
    code: "XAF",
    symbol: "Fr",
    label: "XAF - Central African CFA Franc",
    isoCode: "TD",
  },
  congo: {
    code: "XAF",
    symbol: "Fr",
    label: "XAF - Central African CFA Franc",
    isoCode: "CG",
  },
  "dr-congo": {
    code: "CDF",
    symbol: "Fr",
    label: "CDF - Congolese Franc",
    isoCode: "CD",
  },
  "equatorial-guinea": {
    code: "XAF",
    symbol: "Fr",
    label: "XAF - Central African CFA Franc",
    isoCode: "GQ",
  },
  gabon: {
    code: "XAF",
    symbol: "Fr",
    label: "XAF - Central African CFA Franc",
    isoCode: "GA",
  },
  "sao-tome-and-principe": {
    code: "STN",
    symbol: "Db",
    label: "STN - São Tomé and Príncipe Dobra",
    isoCode: "ST",
  },

  // Africa - East
  burundi: {
    code: "BIF",
    symbol: "Fr",
    label: "BIF - Burundian Franc",
    isoCode: "BI",
  },
  comoros: {
    code: "KMF",
    symbol: "Fr",
    label: "KMF - Comorian Franc",
    isoCode: "KM",
  },
  djibouti: {
    code: "DJF",
    symbol: "Fr",
    label: "DJF - Djiboutian Franc",
    isoCode: "DJ",
  },
  eritrea: {
    code: "ERN",
    symbol: "Nfk",
    label: "ERN - Eritrean Nakfa",
    isoCode: "ER",
  },
  ethiopia: {
    code: "ETB",
    symbol: "Br",
    label: "ETB - Ethiopian Birr",
    isoCode: "ET",
  },
  kenya: {
    code: "KES",
    symbol: "KSh",
    label: "KES - Kenyan Shilling",
    isoCode: "KE",
  },
  madagascar: {
    code: "MGA",
    symbol: "Ar",
    label: "MGA - Malagasy Ariary",
    isoCode: "MG",
  },
  mauritius: {
    code: "MUR",
    symbol: "₨",
    label: "MUR - Mauritian Rupee",
    isoCode: "MU",
  },
  mayotte: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "YT" },
  mozambique: {
    code: "MZN",
    symbol: "MT",
    label: "MZN - Mozambican Metical",
    isoCode: "MZ",
  },
  reunion: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "RE" },
  rwanda: {
    code: "RWF",
    symbol: "Fr",
    label: "RWF - Rwandan Franc",
    isoCode: "RW",
  },
  seychelles: {
    code: "SCR",
    symbol: "₨",
    label: "SCR - Seychellois Rupee",
    isoCode: "SC",
  },
  somalia: {
    code: "SOS",
    symbol: "Sh",
    label: "SOS - Somali Shilling",
    isoCode: "SO",
  },
  "south-sudan": {
    code: "SSP",
    symbol: "£",
    label: "SSP - South Sudanese Pound",
    isoCode: "SS",
  },
  tanzania: {
    code: "TZS",
    symbol: "TSh",
    label: "TZS - Tanzanian Shilling",
    isoCode: "TZ",
  },
  uganda: {
    code: "UGX",
    symbol: "USh",
    label: "UGX - Ugandan Shilling",
    isoCode: "UG",
  },
  zambia: {
    code: "ZMW",
    symbol: "ZK",
    label: "ZMW - Zambian Kwacha",
    isoCode: "ZM",
  },

  // Africa - Southern
  angola: {
    code: "AOA",
    symbol: "Kz",
    label: "AOA - Angolan Kwanza",
    isoCode: "AO",
  },
  botswana: {
    code: "BWP",
    symbol: "P",
    label: "BWP - Botswana Pula",
    isoCode: "BW",
  },
  eswatini: {
    code: "SZL",
    symbol: "L",
    label: "SZL - Swazi Lilangeni",
    isoCode: "SZ",
  },
  lesotho: {
    code: "LSL",
    symbol: "L",
    label: "LSL - Lesotho Loti",
    isoCode: "LS",
  },
  malawi: {
    code: "MWK",
    symbol: "MK",
    label: "MWK - Malawian Kwacha",
    isoCode: "MW",
  },
  namibia: {
    code: "NAD",
    symbol: "$",
    label: "NAD - Namibian Dollar",
    isoCode: "NA",
  },
  "south-africa": {
    code: "ZAR",
    symbol: "R",
    label: "ZAR - South African Rand",
    isoCode: "ZA",
  },
  zimbabwe: {
    code: "ZWL",
    symbol: "Z$",
    label: "ZWL - Zimbabwean Dollar",
    isoCode: "ZW",
  },

  // Oceania
  australia: {
    code: "AUD",
    symbol: "A$",
    label: "AUD - Australian Dollar",
    isoCode: "AU",
  },
  "cook-islands": {
    code: "NZD",
    symbol: "NZ$",
    label: "NZD - New Zealand Dollar",
    isoCode: "CK",
  },
  fiji: {
    code: "FJD",
    symbol: "FJ$",
    label: "FJD - Fijian Dollar",
    isoCode: "FJ",
  },
  "french-polynesia": {
    code: "XPF",
    symbol: "Fr",
    label: "XPF - CFP Franc",
    isoCode: "PF",
  },
  kiribati: {
    code: "AUD",
    symbol: "A$",
    label: "AUD - Australian Dollar",
    isoCode: "KI",
  },
  "marshall-islands": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "MH",
  },
  micronesia: {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "FM",
  },
  nauru: {
    code: "AUD",
    symbol: "A$",
    label: "AUD - Australian Dollar",
    isoCode: "NR",
  },
  "new-caledonia": {
    code: "XPF",
    symbol: "Fr",
    label: "XPF - CFP Franc",
    isoCode: "NC",
  },
  "new-zealand": {
    code: "NZD",
    symbol: "NZ$",
    label: "NZD - New Zealand Dollar",
    isoCode: "NZ",
  },
  niue: {
    code: "NZD",
    symbol: "NZ$",
    label: "NZD - New Zealand Dollar",
    isoCode: "NU",
  },
  palau: { code: "USD", symbol: "$", label: "USD - US Dollar", isoCode: "PW" },
  "papua-new-guinea": {
    code: "PGK",
    symbol: "K",
    label: "PGK - Papua New Guinean Kina",
    isoCode: "PG",
  },
  samoa: {
    code: "WST",
    symbol: "T",
    label: "WST - Samoan Tālā",
    isoCode: "WS",
  },
  "american-samoa": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "AS",
  },
  "solomon-islands": {
    code: "SBD",
    symbol: "$",
    label: "SBD - Solomon Islands Dollar",
    isoCode: "SB",
  },
  tonga: {
    code: "TOP",
    symbol: "T$",
    label: "TOP - Tongan Paʻanga",
    isoCode: "TO",
  },
  tuvalu: {
    code: "AUD",
    symbol: "A$",
    label: "AUD - Australian Dollar",
    isoCode: "TV",
  },
  vanuatu: {
    code: "VUV",
    symbol: "Vt",
    label: "VUV - Vanuatu Vatu",
    isoCode: "VU",
  },
  wallis: {
    code: "XPF",
    symbol: "Fr",
    label: "XPF - CFP Franc",
    isoCode: "WF",
  },
  anguilla: {
    code: "XCD",
    symbol: "$",
    label: "XCD - East Caribbean Dollar",
    isoCode: "AI",
  },

  // ── Territories, dependencies & alternate-name variants ─────────────────────

  // North America & Caribbean
  "united-states": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "US",
  },
  "us-virgin-islands": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "VI",
  },
  "british-virgin-islands": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "VG",
  },
  "turks-and-caicos-islands": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "TC",
  },
  "saint-martin": {
    code: "EUR",
    symbol: "€",
    label: "EUR - Euro",
    isoCode: "MF",
  },
  "sint-maarten": {
    code: "ANG",
    symbol: "ƒ",
    label: "ANG - Netherlands Antillean Guilder",
    isoCode: "SX",
  },
  "saint-barthelemy": {
    code: "EUR",
    symbol: "€",
    label: "EUR - Euro",
    isoCode: "BL",
  },
  "saint-pierre-and-miquelon": {
    code: "EUR",
    symbol: "€",
    label: "EUR - Euro",
    isoCode: "PM",
  },
  "northern-mariana-islands": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "MP",
  },
  guam: { code: "USD", symbol: "$", label: "USD - US Dollar", isoCode: "GU" },
  "bonaire-saint-eustatius-and-saba": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "BQ",
  },
  "netherlands-antilles": {
    code: "ANG",
    symbol: "ƒ",
    label: "ANG - Netherlands Antillean Guilder",
    isoCode: "AN",
  },
  greenland: {
    code: "DKK",
    symbol: "kr",
    label: "DKK - Danish Krone",
    isoCode: "GL",
  },
  montserrat: {
    code: "XCD",
    symbol: "EC$",
    label: "XCD - East Caribbean Dollar",
    isoCode: "MS",
  },

  // South America (territories)
  "falkland-islands": {
    code: "FKP",
    symbol: "£",
    label: "FKP - Falkland Islands Pound",
    isoCode: "FK",
  },
  "south-georgia-and-the-south-sandwich-islands": {
    code: "GBP",
    symbol: "£",
    label: "GBP - British Pound",
    isoCode: "GS",
  },

  // Europe — territories, Crown dependencies & alternate names
  "the-netherlands": {
    code: "EUR",
    symbol: "€",
    label: "EUR - Euro",
    isoCode: "NL",
  },
  "aland-islands": {
    code: "EUR",
    symbol: "€",
    label: "EUR - Euro",
    isoCode: "AX",
  },
  "svalbard-and-jan-mayen": {
    code: "NOK",
    symbol: "kr",
    label: "NOK - Norwegian Krone",
    isoCode: "SJ",
  },
  guernsey: {
    code: "GBP",
    symbol: "£",
    label: "GBP - British Pound",
    isoCode: "GG",
  },
  jersey: {
    code: "GBP",
    symbol: "£",
    label: "GBP - British Pound",
    isoCode: "JE",
  },
  "isle-of-man": {
    code: "GBP",
    symbol: "£",
    label: "GBP - British Pound",
    isoCode: "IM",
  },
  vatican: { code: "EUR", symbol: "€", label: "EUR - Euro", isoCode: "VA" },
  "serbia-and-montenegro": {
    code: "EUR",
    symbol: "€",
    label: "EUR - Euro",
    isoCode: "CS",
  },
  "bosnia-and-herzegovina": {
    code: "BAM",
    symbol: "KM",
    label: "BAM - Bosnia-Herzegovina Convertible Mark",
    isoCode: "BA",
  },

  // Asia — territories & alternate names
  "timor-leste": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "TL",
  },
  macao: {
    code: "MOP",
    symbol: "MOP$",
    label: "MOP - Macanese Pataca",
    isoCode: "MO",
  },
  "palestinian-territory": {
    code: "ILS",
    symbol: "₪",
    label: "ILS - Israeli New Shekel",
    isoCode: "PS",
  },
  "british-indian-ocean-territory": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "IO",
  },

  // Africa — alternate names & territories
  "cabo-verde": {
    code: "CVE",
    symbol: "$",
    label: "CVE - Cape Verdean Escudo",
    isoCode: "CV",
  },
  "republic-of-the-congo": {
    code: "XAF",
    symbol: "Fr",
    label: "XAF - Central African CFA Franc",
    isoCode: "CG",
  },
  "democratic-republic-of-the-congo": {
    code: "CDF",
    symbol: "Fr",
    label: "CDF - Congolese Franc",
    isoCode: "CD",
  },
  "saint-helena": {
    code: "SHP",
    symbol: "£",
    label: "SHP - Saint Helena Pound",
    isoCode: "SH",
  },
  "french-southern-territories": {
    code: "EUR",
    symbol: "€",
    label: "EUR - Euro",
    isoCode: "TF",
  },

  // Oceania — territories & remote islands
  "wallis-and-futuna": {
    code: "XPF",
    symbol: "Fr",
    label: "XPF - CFP Franc",
    isoCode: "WF",
  },
  "heard-island-and-mcdonald-islands": {
    code: "AUD",
    symbol: "A$",
    label: "AUD - Australian Dollar",
    isoCode: "HM",
  },
  "united-states-minor-outlying-islands": {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "UM",
  },
  tokelau: {
    code: "NZD",
    symbol: "NZ$",
    label: "NZD - New Zealand Dollar",
    isoCode: "TK",
  },
  "cocos-islands": {
    code: "AUD",
    symbol: "A$",
    label: "AUD - Australian Dollar",
    isoCode: "CC",
  },
  "norfolk-island": {
    code: "AUD",
    symbol: "A$",
    label: "AUD - Australian Dollar",
    isoCode: "NF",
  },
  "christmas-island": {
    code: "AUD",
    symbol: "A$",
    label: "AUD - Australian Dollar",
    isoCode: "CX",
  },
  pitcairn: {
    code: "NZD",
    symbol: "NZ$",
    label: "NZD - New Zealand Dollar",
    isoCode: "PN",
  },
  "bouvet-island": {
    code: "NOK",
    symbol: "kr",
    label: "NOK - Norwegian Krone",
    isoCode: "BV",
  },

  // Special / uninhabited
  antarctica: {
    code: "USD",
    symbol: "$",
    label: "USD - US Dollar",
    isoCode: "AQ",
  },
};

export const DEFAULT_BUDGET = 10000;

export const DEFAULT_CURRENCY: CurrencyInfo = {
  code: "USD",
  symbol: "$",
  label: "USD - US Dollar",
};

export const GOAL_TYPE_CONFIG: Record<string, GoalTypeConfig> = {
  IMPRESSIONS: {
    label: "Number of Impressions",
    placeholder: "Enter target impressions",
    unit: "Impressions",
    max: undefined,
    min: 1000,
  },
  REACH: {
    label: "Unique Users",
    placeholder: "Enter target reach",
    unit: "Users",
    max: undefined,
    min: 1000,
  },
  SOV: {
    label: "% Share of Voice",
    placeholder: "Enter percentage (0-100)",
    unit: "%",
    max: 100,
    min: 0,
  },
  ADPLAYS: {
    label: "Ad Plays",
    placeholder: "Enter target ad plays",
    unit: "Ad Plays",
    max: 1_000_000,
    min: 100,
  },
  // ATTRIBUTION: {
  //   label: "Attribution Events",
  //   placeholder: "Enter target events",
  //   unit: "Events",
  //   max: undefined,
  //   min: undefined
  // },
  // OTHER: {
  //   label: "Target Value",
  //   placeholder: "Enter target value",
  //   unit: "",
  //   max: undefined,
  //   min: undefined
  // },
};
