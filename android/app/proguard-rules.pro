# Add project specific ProGuard rules here.
# Keep SQLCipher
-keep,includedescriptorclasses class net.zetetic.database.** { *; }
-keep,includedescriptorclasses interface net.zetetic.database.** { *; }

# Keep Room entities
-keep class com.securecontacts.app.data.model.** { *; }

# Keep export/import data classes for Gson serialization
-keep class com.securecontacts.app.util.ExportData { *; }
-keep class com.securecontacts.app.util.ContactExport { *; }
-keep class com.securecontacts.app.util.ImportResult { *; }
-keep class com.securecontacts.app.util.ImportResult$* { *; }

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
