import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Load signing properties from keystore.properties at project root.
// File is gitignored — see .gitignore. Never commit it.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.pgpony.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.pgpony.android"
        minSdk = 26
        targetSdk = 35
        // A16 — Bump for v1.0 Play Store launch.
        //
        // versionCode jumps from 7 (last shipped: A1, v1.5.0) to 100. The
        // big jump leaves room to slot in future hotfix builds at 101/102/…
        // without colliding with anything historical, and matches the
        // parity plan's "Bump versionCode to 100" directive verbatim.
        //
        // versionName follows the iOS lineage: 1.15.0 corresponds to
        // iOS PGPony v4.1 plus the full v5.0 feature set (A2–A15 Android
        // ports). Subsequent Android-only fixes can use 1.15.1, 1.15.2,
        // etc., while the next major iOS-parity drop would bump to 1.16.0.
        // v2.0.0 — hardware security key release. Adds full OpenPGP
        // smartcard / NFC support (decrypt, sign, encrypt-and-sign,
        // expiration editing, signature verification) validated across
        // Token2 and YubiKey, plus card-key support in the decrypt picker
        // and Exchange, discoverable PIN change, and clear-tab actions.
        // v2.1.1 — onboarding key generation can now produce an RFC 9580
        // v6 key (Ed25519 v4 / v6 chooser on the first-key form), matching
        // the iOS onboarding v6 option.
        // v2.1.2 — Decrypt tab "Decrypt With" searchable key picker
        // (hardware keys pinned, most-used default, usage tracking via Room
        // migration 3->4) and a prominent "Paste from Clipboard" button on
        // the Import, Encrypt, and Decrypt tabs.
        // v3.0.0 — public store release. Folds in on-card key generation,
        // admin-PIN lifecycle, the decrypt integrity-verification security
        // fix, Password Store read-only support, the open-source crypto core,
        // and the Phase E polish (version display, More from NorseHorse,
        // Encrypt recipient dropdown). versionCode jumps to 200 to leave the
        // 1xx band for the 2.x dev line.
        // v3.1.0 — iOS 7.1.x parity release. PGP/MIME multipart both
        // directions (structured decrypt with attachments, Bundle compose
        // with .eml/.asc output), multi-file share-in, four-mode Encrypt
        // row with the Encrypt-with toggle in Text mode, card PIN cache
        // with live countdown, offline-primary hardware-key fixes
        // (link matching, subkey issuer signing, tolerant ring lookups),
        // GnuPG AEAD "tag 20" message acceptance, Send as Email with
        // format choice, sign-by-default, and hardened document reads.
        // versionCode moves to the 3xx band for the 3.1 line.
        //
        // v4.0.0 — "The OpenKeychain Succession". Post-quantum composite
        // encryption (ML-KEM-768 + X25519): IETF draft algorithm 35 (v6) and
        // LibrePGP / GnuPG 2.5.x algorithm 8 (v5) — keygen, encrypt, decrypt,
        // passphrase-protected keys, with gpg/Sequoia-interop-aligned wire
        // formats. Also the OpenPGP API provider service (PGPony as the crypto
        // engine for Thunderbird for Android / K-9 Mail / Password Store),
        // default-signer selection, Orbot/Tor SOCKS, and encrypted keyring
        // backup/restore. versionCode jumps to the 4xx band for the 4.0 line.
        versionCode = 400
        versionName = "4.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Apply signing config only if keystore.properties exists.
            // For local debug release builds without keys, fall back to debug signing.
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            // Disable AGP VCS info embedding so the release APK is byte-identical
            // regardless of which working tree it is built from. Without this, the
            // APK contains META-INF/version-control-info.textproto with a git commit
            // reference, which breaks F-Droid reproducible builds when the developer
            // build tree and the F-Droid clone resolve VCS state differently.
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // ── Distribution flavors ────────────────────────────────────────────
    // play  → Google Play. Keeps the Play In-App Review dependency and the
    //         play-flavor RateAppHelper. Build / upload with playRelease.
    // foss  → F-Droid / IzzyOnDroid / direct APK. No Google Play deps. Uses
    //         the foss-flavor RateAppHelper. Build with fossRelease.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
        }
        create("foss") {
            dimension = "distribution"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
        // 4.0.0 Succession Phase 1 — AIDL codegen for the vendored
        // OpenPGP API contract (IOpenPgpService2.aidl under
        // src/main/aidl/org/openintents/openpgp/). AGP 8 defaults
        // aidl to false; without this the generated Stub class never
        // exists and the provider service cannot compile.
        aidl = true
    }

    // FD3: drop the Google-signed dependency-metadata blob from build
    // outputs. It is opaque (only Google can read it), so F-Droid and
    // IzzyOnDroid prefer it gone. Removing it keeps the FOSS APK fully
    // transparent and has no effect on app behavior.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            // 4.0.0 Phase 0: BC 1.85 jars each ship META-INF/LICENSE.md (and
            // NOTICE.md), so bcprov + bcpg + transitive bcutil collide at
            // mergeJavaResource. Exclude them — BC attribution ships in-app
            // via the Phase 9b LicensesScreen (full MIT permission text), so
            // dropping the duplicate jar copies loses nothing legally.
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/NOTICE.md"
        }
    }
}
dependencies {
    // ── Bouncy Castle — OpenPGP ──────────────────────────────────────────
    //
    // V6-0 (RFC 9580 plan): bumped 1.78.1 → 1.84.
    //   - The v6 signature scheme and Argon2 PBE/S2K both landed in 1.79;
    //     1.78.1 predates them, so the crypto layer's existing comments
    //     claiming SEIPDv2 + Argon2id were unbacked on the old jar.
    //   - The native v6 key-pair generator API (Ed25519/X25519/Ed448/X448)
    //     matured by 1.84 — needed for v6 key generation in V6-3.
    //   - 1.84 closes CVE-2026-3505 (unbounded PGP AEAD chunk size →
    //     pre-auth resource exhaustion), which sits on the AEAD path this
    //     v6 work expands.
    // bcprov and bcpg are version-locked together. ProGuard already keeps
    // org.bouncycastle.** wholesale, so no keep-rule change is required.
    //
    // 4.0.0 Phase 0 (dependency housekeeping): bumped 1.84 → 1.85.
    //   - Lands before any 4.0.0 crypto work so every later phase is built
    //     and regression-tested against the final dependency set.
    //   - 1.85 (Maven Central, July 12 2026) OpenPGP-relevant fixes: SKESK
    //     encoding for direct-S2K-encrypted messages; custom signature
    //     creation time no longer ignored on message signatures; continued
    //     AEAD chunk-size hardening on the path CVE-2026-3505 opened.
    //   - PQC survey (plan §6 Q10, answered): bcpg 1.85 has NO OpenPGP PQC
    //     composite support — PublicKeyAlgorithmTags still ends at Ed448
    //     (28); neither LibrePGP algorithm 8 (GnuPG "ECC and Kyber") nor
    //     RFC 9980 algorithm 35 exists in the packet layer. Phase 2b
    //     therefore takes the own-composite-layer path: composite key/PKESK
    //     parsing built on bcprov's ML-KEM (FIPS 203, in bcprov since 1.79)
    //     + X25519 primitives, with iOS 8.0.0 Phase F as the reference
    //     implementation for both wire formats.
    //   - Regression focus: CardPublicKeyDataDecryptorFactory 3-arg
    //     recoverSessionData routing (the 1.84 APDU-level quirk), Argon2/
    //     AEAD v6 import, S2K paths, GnuPG interop suite. See
    //     PHASE_4.0.0-P0_NOTES.md.
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    implementation("org.bouncycastle:bcpg-jdk18on:1.85")
    // ── Jetpack Compose ─────────────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // ── Room (SwiftData equivalent) ─────────────────────────────────────
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // ── DataStore (Preferences) ─────────────────────────────────────────
    //
    // Backs the customizable PGP armor "Comment:" header setting (toggle
    // + custom string). First DataStore-backed pref in the app; the rest
    // of Settings still uses SharedPreferences. See ArmorCommentSettings.
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // ── WorkManager — 4.0.0 Phase 5 background keyserver refresh ─────────
    //     androidx, F-Droid-safe (no Google Play dependency). Drives the
    //     periodic KeyRefreshWorker with Doze-friendly network/battery
    //     constraints — no foreground service.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // ── Ktor (HTTP client for key server) ───────────────────────────────
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    // Note: kotlinx-serialization-json comes in transitively via ktor-
    // serialization-kotlinx-json above. The serialization GRADLE plugin
    // is applied in the plugins{} block to enable @Serializable code
    // generation; no separate runtime dep is needed.
    // ── Google Play Billing — REMOVED for v1.0.0 (monetization restricted ─
    //     until Nov 2026). Re-add when ready:
    //     implementation("com.android.billingclient:billing-ktx:7.1.1")
    // ── ZXing (QR codes) ────────────────────────────────────────────────
    implementation("com.google.zxing:core:3.5.3")
    // ── CameraX (QR scanner) ────────────────────────────────────────────
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    // ── Biometric ───────────────────────────────────────────────────────
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    // ── AndroidX Core ───────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.15.0")
    // ── AndroidX AppCompat — per-app language preferences API ───────────
    //
    // Added in A14 Picker phase. AppCompat is the canonical owner of
    // per-app locale state on Android. On API 33+ it delegates to the
    // platform LocaleManager; on API 26–32 it persists via its own
    // AppLocalesMetadataHolderService (declared in AndroidManifest.xml).
    //
    // We don't actually use any AppCompat themes, AppCompatActivity-only
    // widgets, or the AppCompat resource overlay — we use only the
    // AppCompatDelegate.setApplicationLocales() static method. But
    // pulling in the library is necessary for the Activity recreation
    // on locale change to fire correctly on older API levels.
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // ── In-App Review (Play Store rating prompt) — PLAY FLAVOR ONLY ─────
    //     Scoped to the play source set so the foss flavor (F-Droid /
    //     IzzyOnDroid / direct APK) carries no Google Play dependency. The
    //     foss flavor supplies its own RateAppHelper in src/foss that opens
    //     the public listing instead of the Play in-app review sheet.
    "playImplementation"("com.google.android.play:review-ktx:2.0.2")
    // ── Chrome Custom Tabs (open web links cleanly) ─────────────────────
    implementation("androidx.browser:browser:1.8.0")
    // ── Drag-to-reorder for the keyring (manual sort mode) ──────────────
    implementation("sh.calvin.reorderable:reorderable:2.4.0")
    // ── Testing ─────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    // 4.0.0 Succession Phase 1 — ServiceTestRule for the provider
    // handshake instrumented test (binds PGPonyOpenPgpService in-process).
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// Forward selected -D properties to the forked unit-test JVM. Gradle does NOT
// propagate command-line system properties to the test JVM by default, so the
// gated interop harnesses (-DrunInterop=true) and their passphrases
// (-DiosSecPass=...) would otherwise never see them. Only these keys are
// forwarded; changing one invalidates the test task so it re-runs.
tasks.withType<Test>().configureEach {
    listOf("runInterop", "iosSecPass").forEach { k ->
        System.getProperty(k)?.let { systemProperty(k, it) }
    }
}
