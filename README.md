# SHÊRA AI — شێرە AI (OKX Crypto Quant & Multimodal AI Vision Terminal)

ئەپی پڕۆفیشناڵی شیکاری و چاودێری بازاڕی کریپتۆی **OKX SPOT/USDT**، داتای دێریڤەیتیڤ (`Funding Rate`, `Open Interest`, `Liquidations`)، گرافی **TradingView + OKX Native**، و شیکاری وێنەی چارت بە زیرەکی دەستکردی **Gemini (Firebase AI Logic)**.

---

## ١. دروستکردنی APK لە ڕێگەی GitHub Actions (ئۆتۆماتیکی)

پڕۆژەکە فایلی فەرمیی وۆرکفلۆی گیتهەبی تێدایە لە شوێنی:
`.github/workflows/build-apk.yml`

### چۆنیەتی کارپێکردن لەسەر GitHub:
1. لە سەرەوەی ژینگەی **AI Studio** یان لە ڕێگەی فەرمانی Git، پڕۆژەکە بنێرە (Push / Export) بۆ هەژماری **GitHub**ـی خۆت.
2. لەناو ڕیپۆزیتۆرییەکەت لە GitHub بڕۆ بۆ تابی **Actions** لە سەرەوە.
3. وۆرکفلۆی **`Build SHÊRA AI Android APK`** هەم لەگەڵ هەر `Push`ـێک بۆ لقی `main`/`master` بە شێوەی خۆکار دەست پێ دەکات، هەم دەتوانیت دەستبەجێ کرتە لەسەر دوگمەی **`Run workflow`** بکەیت.
4. وۆرکفلۆکە بە شێوەی خۆکار:
   - فایلی `debug.keystore.base64` دەگۆڕێتەوە بۆ `debug.keystore` (بۆ ئەوەی واژۆی ئەپەکە `SHA-1` هاوتا بێت لەگەڵ `google-services.json` و چوونەژوورەوەی گووگڵ کار بکات).
   - تاقیکردنەوەکانی مێشکی بیرکاری و Robolectric جێبەجێ دەکات.
   - فایلی **`SHERA-AI-debug.apk`** دروست دەکات و لە بەشی **Artifacts** لە خوارەوەی پەڕەی Actionـەکە دایدەنێت بۆ دابەزاندن (`SHERA-AI-Android-APK`).

---

## ٢. تایبەتمەندییە سەرەکییەکان (Core Capabilities)

- **پەیوەندیی ڕاستەوخۆ بە بۆرسەی OKX (`REST v5` + `WebSocket v5`)**:
  - چاودێریکردنی هەموو جووتە دراوەکانی `SPOT/USDT` بە شێوەی پەڕەبەندی (Pagination)، کاشکردن، و گەڕانەوەی خۆکار (Exponential Backoff Reconnect).
  - پیشاندانی دۆخی پەیوەندی (`WebSocket Live / REST Polling / Reconnecting / Stale Data Warning`) و کاتی دوایین نوێکردنەوە.
  - بەکارهێنانی ١٠٠٪ داتای گشتی؛ هیچ API Key یان وشەی نهێنیی مامەڵەکردن لە فرۆنت-ئێند داوا ناکرێت.
- **مێشکی شیکاریی ژمارەیی (6-Timeframe Quantitative Engine)**:
  - حیسابکردنی وردی `RSI(14)`، `EMA(20/50/200)`، `MACD(12,26,9)`، `ATR(14)`، قەبارەی مامەڵە (`Volume Ratio`)، و ئاستەکانی پشتگیری و بەرگری لە تایمفریمەکانی (`1m`, `5m`, `15m`, `1H`, `4H`, `1D`).
  - جیاکردنەوەی ڕوونی **`BUY WATCH`**، **`SELL WATCH`**، و **`NEUTRAL`**.
  - جیاکردنەوەی **نمرەی کوالێتی نیشانە لە ١٠٠ (`Signal Quality Score /100`)** لە **ڕێژەی سەرکەوتنی مێژوویی پشتڕاستکراوە بە Backtest**.
- **گرافی دووانە (OKX Native Canvas + TradingView Official Widget)**:
  - گۆڕینی خێرا لە نێوان چارتی ناوخۆیی خێرای OKX (بە توانای پشکنینی هەر مۆمێک بە پەنجەنان) و گرافی فەرمیی TradingView.
- **شیکاری دێریڤەیتیڤ و CoinGlass (`Funding Rate` • `Open Interest` • `Liquidations`)**:
  - وەرگرتنی ڕاستەوخۆی Funding Rate، Open Interest، و لیکویدەیشنەکان لە داتای گشتیی فەرمیی OKX بەبێ دروستکردنی هیچ ژمارەیەکی ساختە.
- **ژووری شیکاری وێنەی چارت و پێشبینی بە AI (Multimodal AI Chart Vision Desk)**:
  - ناردنی وێنەی هەر چارتێک لە مۆبایلەوە (بە بەکارهێنانی Android Photo Picker) یان شیکارکردنی ڕاستەوخۆی چارتی زیندووی OKX بە مۆدێلەکانی **`gemini-3.8-flash`** و **`gemini-4-argon`**.
- **تاقیکردنەوەی مێژوویی (Walk-Forward Backtesting + Out-of-Sample)**:
  - دابەشکردنی داتای مێژوویی بۆ **70% In-Sample** و **30% Out-of-Sample (دەرەوەی نموونە)** بە لێدەرکردنی کرێی بۆرسە (`Fee %`) و خلیسکانی نرخ (`Slippage %`).
- **ئاگادارکردنەوەی ٢٤/٧ و پاشەکەوتی هەوری (Cloud Sync & Alerts)**:
  - پاشەکەوتکردنی لیستی چاودێری (`Watchlist`)، یاساکانی ئاگادارکردنەوە (`Alert Rules`)، و مێژووی نیشانەکان (`Signal History`) لەسەر **Firebase Firestore** بە چوونەژوورەوەی پارێزراوی **Google Sign-In**.

---

## ٣. فەرمانەکانی تاقیکردنەوە و کۆمپایلکردنی ناوخۆیی

1. **پشکنینی یاساکانی ئاسایشی Firestore (JavaScript Tier 1)**:
   ```bash
   FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
   ```
2. **تاقیکردنەوەی یەکەیی و Robolectric (Kotlin Tier 2 & Quant Tests)**:
   ```bash
   gradle :app:testDebugUnitTest --tests "com.example.ExampleUnitTest" --tests "com.example.ExampleRobolectricTest"
   ```
3. **دروستکردنی فایلی APK**:
   ```bash
   gradle :app:assembleDebug
   ```
