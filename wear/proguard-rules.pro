# Regole di R8 per l'app Wear OS.

# La Data Layer arriva da Google Play services: le classi vengono richiamate dal sistema.
-keep class com.google.android.gms.wearable.** { *; }
-dontwarn com.google.android.gms.**
