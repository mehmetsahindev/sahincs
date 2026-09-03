# ☁️ CloudStream Türkçe Eklentiler

CloudStream için yazılmış Türkçe içerik eklentileri.

## 💾 Kurulum

1. **[CloudStream](https://github.com/recloudstream/cloudstream/releases)** uygulamasını kurun.
2. Uygulamada `Ayarlar › Eklentiler › Depo ekle` yolunu izleyin.
3. `Depo URL'si` alanına aşağıdaki adresi yapıştırın:

```
https://raw.githubusercontent.com/mehmetsahindev/sahincs/master/repo.json
```

## 📦 Eklentiler

| Eklenti | Tür | Site |
|---|---|---|
| SelcukFlix | Dizi, Film | [selcukflix.co](https://selcukflix.co) |

## 🔨 Derleme

```bash
./gradlew make makePluginsJson
```

Çıktılar: `<Eklenti>/build/<Eklenti>.cs3` ve `build/plugins.json`

`master` dalına yapılan her push'ta **CloudStream Derleyici** iş akışı eklentileri derleyip
`builds` dalına yükler; CloudStream de depoyu oradan okur.

## ➕ Yeni eklenti ekleme

Kök dizinde `build.gradle.kts` içeren her klasör otomatik olarak modül sayılır — ayrıca kayıt gerekmez.

```
YeniEklenti/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    └── kotlin/com/sahincs/
        ├── YeniEklenti.kt          # MainAPI alt sınıfı
        └── YeniEklentiPlugin.kt    # @CloudstreamPlugin kaydı
```

## 📄 Lisans

GPL-3.0
