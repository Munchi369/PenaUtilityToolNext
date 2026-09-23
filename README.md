# PenaUtilityToolNext

「ペナントシミュレーション3」のセーブデータを安全に閲覧・編集・分析するための非公式デスクトップツールです。

現在は、セーブデータの選択と接続確認、現役選手の一覧・比較、選手詳細、個人成績、能力履歴、タイトル・表彰・特別記録の閲覧に対応しています。

## 開発環境

- Java 21
- Kotlin/JVM
- Compose Multiplatform for Desktop
- Apache Derby 10.14.2.0

## ビルドとテスト

Windowsでは、リポジトリのルートで次を実行します。

```powershell
.\gradlew.bat check
.\gradlew.bat build
```

一部のDBテストはテストデータがある場合だけ実行されます。テストデータなしでもビルドできますが、DBを使うテストはスキップされます。

DBテストも実行する場合は、GitHubの[Releases](https://github.com/Munchi369/PenaUtilityToolNext/releases)から`PenaUtilityToolNext-test-data-year05.zip`をダウンロードし、リポジトリのルートへ展開してください。配置後に次のファイルが存在すれば準備完了です。

```text
test-data/reference/year-05/penanto3-year05-end/service.properties
```

その後、もう一度`.\gradlew.bat check`を実行します。ゲーム本体、通常利用のセーブデータ、開発用の内部資料は、この公開リポジトリに含まれません。

## ライセンス

このリポジトリで公開するコードは [MIT License](LICENSE) で提供します。ゲーム本体およびゲームに含まれるデータの権利を許諾するものではありません。
