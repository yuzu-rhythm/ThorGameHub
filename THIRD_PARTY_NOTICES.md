# 第三者ライブラリ・素材の点検

2026-10-23の公開用コピーの確認記録です。AYNとは関係のない独立したプロジェクトです。

## 同梱物

Kotlin / Javaソース、XMLリソース、スキーマJSON、ビルドスクリプト、テスト、Gradle Wrapper、架空ライブラリのエミュレータ画面例を含めます。ROM、BIOS、ファームウェア、ゲーム画像、個人のセーブデータ、エミュレータ本体、Android SDK、依存ライブラリ本体、APKは公開リポジトリへ含めません。アイコンはXMLベクター、カード背景はコードで描画されています。独自コードの全行の出所を保証する監査ではありません。

## Gradle Wrapper

Gradle WrapperのJARはGradle 9.5.0の公式SHA-256と一致しました。

`497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`

Wrapper / 起動スクリプトの著作権表示を維持し、[Gradle配布物のLICENSE](docs/licenses/GRADLE-LICENSE.txt) と [NOTICE](docs/licenses/GRADLE-NOTICE.txt) を同梱しました。配布ZIPの公式チェックサムもwrapper設定へ追加しています。

一次資料: [Gradle公式チェックサム](https://gradle.org/release-checksums/)、[Gradleのセキュリティ方針](https://docs.gradle.org/current/userguide/best_practices_security.html)。

## 直接依存の確認

下記はバージョンカタログにバージョンが明記されたライブラリのPOMを一次配布元で照会した結果です。ComposeのBOM管理される個別モジュールや、推移依存すべてを保証する一覧ではありません。バイナリ配布時は、解決された全依存物のライセンス・通知条件を改めて確認してください。

| ライブラリ | 指定バージョン | POMのライセンス表示 |
| --- | --- | --- |
| [androidx.core:core-ktx](https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/1.18.0/core-ktx-1.18.0.pom) | 1.18.0 | The Apache Software License, Version 2.0 |
| [androidx.activity:activity-compose](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/1.13.0/activity-compose-1.13.0.pom) | 1.13.0 | The Apache Software License, Version 2.0 |
| [androidx.lifecycle:lifecycle-runtime-compose](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-compose/2.11.0/lifecycle-runtime-compose-2.11.0.pom) | 2.11.0 | The Apache Software License, Version 2.0 |
| [androidx.lifecycle:lifecycle-viewmodel-compose](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-compose/2.11.0/lifecycle-viewmodel-compose-2.11.0.pom) | 2.11.0 | The Apache Software License, Version 2.0 |
| [androidx.navigation:navigation-compose](https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/2.9.8/navigation-compose-2.9.8.pom) | 2.9.8 | The Apache Software License, Version 2.0 |
| [androidx.room:room-runtime](https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/2.8.4/room-runtime-2.8.4.pom) | 2.8.4 | The Apache Software License, Version 2.0 |
| [androidx.room:room-ktx](https://dl.google.com/dl/android/maven2/androidx/room/room-ktx/2.8.4/room-ktx-2.8.4.pom) | 2.8.4 | The Apache Software License, Version 2.0 |
| [androidx.room:room-compiler](https://dl.google.com/dl/android/maven2/androidx/room/room-compiler/2.8.4/room-compiler-2.8.4.pom) | 2.8.4 | The Apache Software License, Version 2.0 |
| [androidx.room:room-testing](https://dl.google.com/dl/android/maven2/androidx/room/room-testing/2.8.4/room-testing-2.8.4.pom) | 2.8.4 | The Apache Software License, Version 2.0 |
| [androidx.room:room-paging](https://dl.google.com/dl/android/maven2/androidx/room/room-paging/2.8.4/room-paging-2.8.4.pom) | 2.8.4 | The Apache Software License, Version 2.0 |
| [androidx.datastore:datastore-preferences](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences/1.2.1/datastore-preferences-1.2.1.pom) | 1.2.1 | The Apache Software License, Version 2.0 |
| [androidx.work:work-runtime-ktx](https://dl.google.com/dl/android/maven2/androidx/work/work-runtime-ktx/2.11.2/work-runtime-ktx-2.11.2.pom) | 2.11.2 | The Apache Software License, Version 2.0 |
| [androidx.work:work-testing](https://dl.google.com/dl/android/maven2/androidx/work/work-testing/2.11.2/work-testing-2.11.2.pom) | 2.11.2 | The Apache Software License, Version 2.0 |
| [androidx.documentfile:documentfile](https://dl.google.com/dl/android/maven2/androidx/documentfile/documentfile/1.1.0/documentfile-1.1.0.pom) | 1.1.0 | The Apache Software License, Version 2.0 |
| [androidx.paging:paging-runtime](https://dl.google.com/dl/android/maven2/androidx/paging/paging-runtime/3.5.1/paging-runtime-3.5.1.pom) | 3.5.1 | The Apache Software License, Version 2.0 |
| [androidx.paging:paging-compose](https://dl.google.com/dl/android/maven2/androidx/paging/paging-compose/3.5.1/paging-compose-3.5.1.pom) | 3.5.1 | The Apache Software License, Version 2.0 |
| [androidx.compose:compose-bom](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.08.00/compose-bom-2026.08.00.pom) | 2026.08.00 | The Apache Software License, Version 2.0 |
| [io.coil-kt.coil3:coil-compose](https://repo.maven.apache.org/maven2/io/coil-kt/coil3/coil-compose/3.5.0/coil-compose-3.5.0.pom) | 3.5.0 | The Apache License, Version 2.0 |
| [io.coil-kt.coil3:coil-network-okhttp](https://repo.maven.apache.org/maven2/io/coil-kt/coil3/coil-network-okhttp/3.5.0/coil-network-okhttp-3.5.0.pom) | 3.5.0 | The Apache License, Version 2.0 |
| [com.google.dagger:hilt-android](https://repo.maven.apache.org/maven2/com/google/dagger/hilt-android/2.60.1/hilt-android-2.60.1.pom) | 2.60.1 | Apache 2.0 |
| [com.google.dagger:hilt-android-compiler](https://repo.maven.apache.org/maven2/com/google/dagger/hilt-android-compiler/2.60.1/hilt-android-compiler-2.60.1.pom) | 2.60.1 | Apache 2.0 |
| [org.jetbrains.kotlinx:kotlinx-coroutines-core](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.10.2/kotlinx-coroutines-core-1.10.2.pom) | 1.10.2 | Apache-2.0 |
| [org.jetbrains.kotlinx:kotlinx-coroutines-android](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/1.10.2/kotlinx-coroutines-android-1.10.2.pom) | 1.10.2 | Apache-2.0 |
| [org.jetbrains.kotlinx:kotlinx-coroutines-test](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-test/1.10.2/kotlinx-coroutines-test-1.10.2.pom) | 1.10.2 | Apache-2.0 |
| [junit:junit](https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.pom) | 4.13.2 | Eclipse Public License 1.0 |
| [androidx.test:core](https://dl.google.com/dl/android/maven2/androidx/test/core/1.7.0/core-1.7.0.pom) | 1.7.0 | The Apache Software License, Version 2.0 |
| [androidx.test:runner](https://dl.google.com/dl/android/maven2/androidx/test/runner/1.7.0/runner-1.7.0.pom) | 1.7.0 | The Apache Software License, Version 2.0 |
| [androidx.test.ext:junit](https://dl.google.com/dl/android/maven2/androidx/test/ext/junit/1.3.0/junit-1.3.0.pom) | 1.3.0 | The Apache Software License, Version 2.0 |
| [androidx.test.espresso:espresso-core](https://dl.google.com/dl/android/maven2/androidx/test/espresso/espresso-core/3.7.0/espresso-core-3.7.0.pom) | 3.7.0 | The Apache Software License, Version 2.0 |
| [com.google.truth:truth](https://repo.maven.apache.org/maven2/com/google/truth/truth/1.4.4/truth-1.4.4.pom) | 1.4.4 | POMに表示なし |
| [app.cash.turbine:turbine](https://repo.maven.apache.org/maven2/app/cash/turbine/turbine/1.2.1/turbine-1.2.1.pom) | 1.2.1 | The Apache Software License, Version 2.0 |

Android SDKは開発者が別途、SDK配布元の条件に従って用意します。Android SDK全体をApache License 2.0として扱うものではありません。

このスナップショットでは、オリジナルコード全体へのOSSライセンスを新たに付与していません。一般的な再利用・再配布許諾の方針は作者が選択してください。外部ゲーム・エミュレータ名の記載は、そのコードや素材の同梱・許諾を意味しません。

## ビルド用プラグインのPOM確認

| プラグイン | バージョン | POMの表示 |
| --- | --- | --- |
| [com.android.tools.build:gradle](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.3.0/gradle-9.3.0.pom) | 9.3.0 | The Apache Software License, Version 2.0 |
| [org.jetbrains.kotlin:kotlin-gradle-plugin](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/2.3.21/kotlin-gradle-plugin-2.3.21.pom) | 2.3.21 | Apache-2.0 |
| [com.google.devtools.ksp:symbol-processing-gradle-plugin](https://repo.maven.apache.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/2.3.9/symbol-processing-gradle-plugin-2.3.9.pom) | 2.3.9 | The Apache License, Version 2.0 |
| [com.google.dagger:hilt-android-gradle-plugin](https://repo.maven.apache.org/maven2/com/google/dagger/hilt-android-gradle-plugin/2.60.1/hilt-android-gradle-plugin-2.60.1.pom) | 2.60.1 | Apache 2.0 |
| [androidx.room:room-gradle-plugin](https://dl.google.com/dl/android/maven2/androidx/room/room-gradle-plugin/2.8.4/room-gradle-plugin-2.8.4.pom) | 2.8.4 | The Apache Software License, Version 2.0 |

## 外部起動アダプタの参照先

GameNativeの候補契約は、公開された[Manifest](https://github.com/utkarshdalal/GameNative/blob/master/app/src/main/AndroidManifest.xml)や[IntentLaunchManager](https://github.com/utkarshdalal/GameNative/blob/master/app/src/main/java/app/gamenative/utils/IntentLaunchManager.kt)を参考とした接続形状です。GameNative / RetroArchのコードやバイナリを同梱していません。実タイトル起動・互換性の確認結果を意味するものではありません。
