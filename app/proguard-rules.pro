# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# BouncyCastle (Ed25519 crypto)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Solana Mobile SDK
-keep class com.solana.** { *; }
-keep class com.solana.mobilewalletadapter.** { *; }
-dontwarn com.solana.**

# Solana encoding (Base58)
-keep class com.funkatronics.** { *; }
-dontwarn com.funkatronics.**

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# App data classes used with JSON
-keep class com.example.walletconnect.SolanaManager$BoxCreatedEvent { *; }
-keep class com.example.walletconnect.SolanaManager$BoxOpenedEvent { *; }
-keep class com.example.walletconnect.SolanaManager$PendingContract { *; }
-keep class com.example.walletconnect.SolanaManager$ExpiredBox { *; }
-keep class com.example.walletconnect.SolanaManager$TokenMetadata { *; }
-keep class com.example.walletconnect.SolanaManager$TokenInfo { *; }
-keep class com.example.walletconnect.SolanaManager$Instruction { *; }
-keep class com.example.walletconnect.SolanaManager$AccountMeta { *; }
-keep class com.example.walletconnect.utils.BoxMetadataStore$BoxMetadata { *; }
-keep class com.example.walletconnect.utils.TimerContractStore$TimerParams { *; }
-keep class com.example.walletconnect.utils.CheckpointContractStore$CheckpointParams { *; }
-keep class com.example.walletconnect.utils.LocalBookStore$LocalBook { *; }

# Jsoup (used for EPUB parsing)
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Timber (no-op in release, but keep for safety)
-dontwarn timber.log.**

# AndroidX Security (EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }
