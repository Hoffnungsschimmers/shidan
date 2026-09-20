import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fanji.mealnote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fanji.mealnote"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // 签名配置必须在 buildTypes 之前声明：Kotlin DSL 的 android {} 块按顺序执行，
    // 若放在后面，buildTypes 里的 signingConfigs.getByName("release") 会因尚未创建而失败。
    signingConfigs {
        create("release") {
            // 注意：在 android {} 块内 `java` 已被 AGP 的扩展遮蔽，
            // 因此文件顶部已 import java.util.Properties。
            val props = Properties()
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                propsFile.inputStream().use { props.load(it) }
            }
            val storePath = props.getProperty("release.storeFile")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = props.getProperty("release.storePassword")
                keyAlias = props.getProperty("release.keyAlias")
                keyPassword = props.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug 包体积与构建速度优先，不开启混淆。
            isMinifyEnabled = false
        }
        release {
            // Release 必须开启代码压缩与资源裁剪：本项目虽小，但开启后能显著减小
            // APK 体积、移除调试信息与未使用资源，并提高反编译成本。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 使用项目根目录下的自签名密钥库，使 release 包可直接安装。
            // 凭据从 local.properties 读取（该文件已在 .gitignore 中）；
            // 未配置时该 signingConfig 的 storeFile 为 null，AGP 会产出未签名包，
            // 这样 CI 只做编译验证时不会因缺少密钥而失败。
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // 依赖升级必须先在独立分支验证构建与测试，因此版本提示不构成发布阻塞项。
        // 详见 README「开发约定」与 PROJECT_PLAN.md 第 9.4 节。
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
        // mipmap-anydpi-v26 是自适应图标的推荐目录写法。虽然 minSdk 已是 26、
        // 该限定符在技术上冗余，但去掉后 AAPT 无法解析 mipmap/ic_launcher，故保留。
        disable += "ObsoleteSdkInt"
        // 出现真正的错误（而非提示）时立即失败，避免问题被淹没在警告中。
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
    }
}

kotlin {
    jvmToolchain(17)
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}


