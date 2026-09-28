# Baori R8 rules.
# Compose and Kotlin libraries ship consumer rules; nothing extra is needed for them.

# RevenueCat relies on reflection for model deserialization. Keep its public surface.
# See https://www.revenuecat.com/docs/getting-started/installation/android
-keep class com.revenuecat.purchases.** { *; }
-keep interface com.revenuecat.purchases.** { *; }
