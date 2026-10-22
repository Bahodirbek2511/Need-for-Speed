# Need for Speed — Street Rush

**Android uchun yengil arcade street-racing o‘yini.** Landscape rejimi, perspektivali tungi shahar trassasi, raqib mashinalar, nitro, tormoz va sensorli boshqaruv mavjud. Ilova tashqi API, internet yoki Vulkan talab qilmaydi; Android Canvas orqali ishlaydi va ARM32/ARM64 telefonlarga mos Java APK sifatida yig‘iladi.

## APK yuklab olish

Build muvaffaqiyatli tugagach, eng so‘nggi APK shu havolada paydo bo‘ladi:

[**Need-for-Speed-Street-Rush.apk — yuklab olish**](https://github.com/Bahodirbek2511/Need-for-Speed/releases/latest/download/Need-for-Speed-Street-Rush.apk)

Shuningdek, [GitHub Actions buildlari](https://github.com/Bahodirbek2511/Need-for-Speed/actions) bo‘limida APK artifact sifatida beriladi.

## Boshqaruv

- **◀ / ▶** — mashinani chapga va o‘ngga burish
- **GO** — tezlashish
- **BRAKE** — sekinlashish
- **N₂O** — nitro
- **Pause** — o‘yinni vaqtincha to‘xtatish

Uch marta to‘qnashsangiz poyga tugaydi. Trafik orasidan xavfsiz o‘tib, masofa va ochkolarni oshiring.

## Muhim izoh

Bu **mustaqil, muxlislar yaratgan NFS-uslubidagi 2.5D arcade o‘yini**; EA’ning original Need for Speed: Most Wanted (2005/2012) yoki Underground 2 o‘yini emas. EA’ning original kodi, mashina modellari, xaritalari, musiqasi yoki boshqa mualliflik huquqi bilan himoyalangan materiallari kiritilmagan. Bu qaror Redmi A1+ kabi boshlang‘ich Android telefonlarida Vulkan talab qiladigan portga bog‘lanmasdan, o‘rnatilib ishlaydigan APK chiqarish uchun qabul qilindi.

## Build

GitHub Actions main branchga o‘zgarish tushganda Gradle orqali Android APK yig‘adi, APK arxivini va package nomini tekshiradi, so‘ng artifact hamda GitHub Release sifatida chiqaradi.
