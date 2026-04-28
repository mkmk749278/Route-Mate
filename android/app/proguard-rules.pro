# Keep kotlinx.serialization companions/serializers.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class app.routemate.**$$serializer { *; }
-keepclassmembers class app.routemate.** {
    *** Companion;
}
-keepclasseswithmembers class app.routemate.** {
    kotlinx.serialization.KSerializer serializer(...);
}
