Distributed-Disk-Registery (gRPC + TCP)
=======================================

---


# gRPC + Protobuf + TCP Hybrid Distributed Server

Bu proje, birden fazla sunucunun dağıtık bir küme ("family") oluşturduğu, **gRPC + Protobuf** ile kendi aralarında haberleştiği ve **lider üye** üzerinden gelen **SET/GET komutlarıyla** mesajları tolerance değerine göre üyelere dağıtarak sakladığı bir **dağıtık disk kayıt sistemi** örneğidir.

---

##  Özellikler

### ✔ SET/GET Komutları ile Dağıtık Mesaj Saklama

Lider üye TCP üzerinden SET/GET komutları alır:

* `SET <id> <msg>`: Mesajı diske kaydeder ve tolerance sayısı kadar üyeye dağıtır
* `GET <id>`: Önce yerel diskten, yoksa diğer üyelerden mesajı getirir

### ✔ Tolerance Tabanlı Replikasyon

`tolerance.conf` dosyasından okunan değere göre mesajlar birden fazla üyeye kopyalanır:

* Round-robin üye seçimi ile dengeli dağılım
* Mesaj lokasyon takibi (`Map<Integer, List<MemberId>>`)

### ✔ Çoklu IO Modu Desteği

`tolerance.conf` içinde `IO_MODE` ayarı ile:

* **BUFFERED**: BufferedWriter/BufferedReader (varsayılan)
* **UNBUFFERED**: FileOutputStream/FileInputStream
* **ZEROCOPY**: FileChannel ile NIO

### ✔ Otomatik Dağıtık Üye Keşfi

Her yeni Üye:

* 5555’ten başlayarak boş bir port bulur
* Kendinden önce gelen üyelere gRPC katılma (Join) isteği gönderir
* Aile (Family) listesine otomatik dahil olur.

### ✔ Lider Üye (Cluster Gateway)

İlk başlayan Üye (port 5555) otomatik olarak **lider** kabul edilir ve:

* TCP port **6666** üzerinden dış dünyadan text mesajı dinler
* Her mesajı Protobuf formatına dönüştürür
* Tüm diğer üyelere gRPC üzerinden gönderir

### ✔ gRPC + Protobuf İçi Mesajlaşma

Üyeler kendi aralarında sadece **protobuf message** ile haberleşir:

```proto
message StoredMessage {
  int32 id = 1;
  string text = 2;
}

message StoreResult {
  bool success = 1;
  string message = 2;
}

message MessageId {
  int32 id = 1;
}

service StorageService {
  rpc Store(StoredMessage) returns (StoreResult);
  rpc Retrieve(MessageId) returns (StoredMessage);
}
```

### ✔ Aile (Family) Senkronizasyonu

Her üye, düzenli olarak diğer aile üyeleri listesini ekrana basar:

```
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-11-13T21:05:00
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556 [1 msg]
 - 127.0.0.1:5557 [1 msg]
Local messages: 1
Total tracked: 2
======================================
```

### ✔ Periyodik Rapor Sistemi

Her üye 10 saniyede bir:

* Aile üyelerini ve mesaj sayılarını ekrana basar
* Lider, toplam mesaj dağılımını gösterir

### ✔ Üye Düşmesi (Failover)

Health-check mekanizması ile kopan (offline) üyeler aile listesinden çıkarılır.

---

## 📁 Proje Yapısı

```
distributed-disk-register/
│
├── pom.xml
├── tolerance.conf
├── src
│   └── main
│       ├── java/com/example/family/
│       │       ├── NodeMain.java
│       │       ├── NodeRegistry.java
│       │       ├── FamilyServiceImpl.java
│       │       ├── StorageServiceImpl.java
│       │       ├── CommandParser.java
│       │       └── ToleranceConfig.java
│       │
│       └── proto/
│               └── family.proto
```

## 👨🏻‍💻 Kodlama

Yüksek seviyeli dillerde yazılım geliştirme işlemi basit bir editörden ziyade gelişmiş bir IDE (Integrated Development Environment) ile yapılması tavsiye edilmektedir. JVM ailesi dillerinin en çok tercih edilen [IntelliJ IDEA](https://www.jetbrains.com/idea/) aracını edu' lu mail adresinizle öğrenci lisanslı olarak indirip kullanabilirsiniz. Bu projeyi diskinize klonladıktan sonra IDEA' yı açıp, üst menüden _Open_ seçeneği projenin _pom.xml_ dosyasını seçtiğinizde projeniz açılacaktır. 


---

## 🔧 Derleme

Proje dizininde (pom.xml in olduğu):

```bash
mvn clean compile
```

Bu komut:

* `family.proto` → gRPC Java sınıflarını üretir
* Tüm server kodlarını derler

---

## ▶️ Çalıştırma

Her bir terminal yeni bir üye demektir.

### **Üye başlatma**

```bash
mvn exec:java -Dexec.mainClass=com.example.family.NodeMain
```

Çıktı:

```
Node started on 127.0.0.1:5555
Leader listening for text on TCP 127.0.0.1:6666
...
```

![Sistem Başlatma](https://github.com/beyzayagli/distributed-disk-register/blob/main/ddr-calistirma.png)


###  Sonuç

Bu mesaj protobuf mesajına çevrilip round robin ile seçilen üyelere gider ve üyeler mesajları kaydeder.

---

##  Çalışma Prensibi

###  1. Dağıtık Üye Keşfi

Yeni Üye, kendinden önceki portları gRPC ile yoklar:

```
5555 → varsa Join
5556 → varsa Join
...
```

###  2. Lider Üye (Port 5555)

Lider Üye:

* TCP 6666'dan SET/GET komutları alır
* `SET <id> <msg>`: Mesajı diske yazar, tolerance sayısı kadar üyeye gRPC ile dağıtır
* `GET <id>`: Önce yerel diskten, yoksa diğer üyelerden gRPC ile getirir

###  3. Family Senkronizasyonu

Her üye 10 saniyede bir aile listesini ve mesaj dağılımını ekrana basar.

---

## Lisans

MIT — Eğitim ve araştırma amaçlı serbestçe kullanılabilir.

---

##  Katkı

Pull request’e her zaman açığız!
Yeni özellik önerileri için issue açabilirsiniz.
