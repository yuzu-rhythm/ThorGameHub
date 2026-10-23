# 公開用コピーの検証記録

2026-10-23、元環境を残した公開専用コピーで実施しました。過去の実機・旧APKの検証結果を今回の結果へ転用していません。

## 環境とコマンド

- macOS arm64 / JDK 17
- Android SDK Platform 37.0 / Build Tools 36.0.0
- Gradle 9.5.0 / AGP 9.3.0

```bash
# 自分のJDK 17とSDKのパスを設定したうえで実行
THOR_RUN_CONNECTED_TESTS=0 bash scripts/verify.sh
bash -n scripts/verify.sh
```

公開用スクリプトを修正した後にもホスト検証を実行しました。Connected指定時に終了コード2で停止するガードも確認しています。

## 結果

| 項目 | 結果 |
| --- | --- |
| JVMテスト | **55 passed / 0 failed / 0 errors / 0 skipped** |
| 内訳 | core:model 15、core:data 12、app 28 |
| Room Androidテスト | コンパイル成功、実行は未実施 |
| app Androidテスト | コンパイル成功、実行は未実施 |
| Standard Lint | 成功 |
| Standard debug APK | ビルド成功 |
| ホスト検証 | 169タスク、72実行・87キャッシュ・10 up-to-date、BUILD SUCCESSFUL |
| シェル構文 | `bash -n` 成功 |
| Connectedガード | 指定時に終了コード2、ADB未実行 |
| Wrapper | 公式JARチェックサム一致 |

今回のAPK SHA-256（APK本体は公開対象外）:

`818d969839014f3d245381887791f7f0552f19e6336057a08508e9299f345f22`

最後のホスト検証は4秒で完了しました。初回はクリーンな公開用コピーから1分10秒で完了しています。

## 新規エミュレータの画面確認

今回専用のAPI 35 / arm64エミュレータをwork領域へ作成しました。qemuであることを確認した対象だけへAPKをインストールし、HomeActivityを起動しました。1280×800、density 160の単画面表示で、`Orion Trail`等の実装内の架空タイトルをUI階層と照合し、Home画面を撮影・目視確認しました。

端末のGoogleアカウント、実ROM、BIOS、エミュレータアプリ、個人のセーブデータは使用していません。デフォルトHOME設定は変更していません。操作したのは今回作成したエミュレータのみです。

## 未実施

- Connected Androidテストの実行（コンパイルのみ）
- Thor実機の上下パネル、タッチ・物理入力、蓋、音声、発熱、長時間運用
- 外部アプリのタイトル別起動と画面配置
- 実ゲーム保存データのsnapshot / restore
- リリース署名、AAB、Play配布、製品品質の認定

## 作業中の補足

最初の検証起動はコマンドの作業ディレクトリが異なったため、スクリプトが見つからず終了コード127になりました。公開用プロジェクトのディレクトリから再実行し、ビルドを確認しました。アプリのソースロジックは変更していません。

APK Signature Schemeの検証（`apksigner verify`）と `zipalign -c -P 16 -v 4` も成功しました。Android debug署名であり、製品版署名ではありません。
