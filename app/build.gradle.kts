plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zenlauncher.zen"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.zenlauncher.zen"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // OpenCV trae un .so de ~40 MB POR ABI. El dispositivo objetivo es un Nothing
        // Phone (2a), que es arm64-v8a, asi que empaquetar las otras tres multiplicaria
        // por cuatro el peso del launcher para nada. Ver README.
        //
        // Consecuencia: `connectedDebugAndroidTest` necesita un dispositivo arm64; en un
        // emulador x86_64 hay que anadir "x86_64" a esta lista.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    testOptions {
        unitTests {
            // Robolectric necesita los recursos empaquetados para levantar el contexto.
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)

    // Camara del escaner. CameraX y no Camera2 a pelo: el ciclo de vida, la rotacion y
    // el analisis de frames son justo lo que Camera2 obliga a escribir a mano.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // La UNICA dependencia nativa de Zen, y la mas pesada de todas. Detecta la hoja y
    // rectifica la perspectiva. Ver README: se acota con abiFilters a la ABI del
    // dispositivo objetivo para no multiplicar el peso por cuatro.
    implementation(libs.opencv)

    // OCR con el modelo empaquetado: sin red y sin Google Play Services.
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // Robolectric solo donde hace falta Android de verdad (SQLite y DataStore), para
    // que `./gradlew testDebugUnitTest` cubra la persistencia sin necesitar dispositivo.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // Tests de UI de Compose ejecutados sobre Robolectric: cubren pantallas reales sin
    // necesitar un dispositivo conectado.
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
