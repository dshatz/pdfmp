`pdfium.so` and compiled `pdfmp.so` files are put in this dir by `packageAndroidNatives` so android compilation can put them into the AAR.

The new "Android Multiplatform Library" plugin does not support specifying a custom `jniLibs` directory.

With the old plugin this was possible:

```kotlin
android {
    sourceSets.getByName("main") {
        jniLibs.srcDir(packageAndroidNatives.map { it.destinationDir })
    }
}
```
