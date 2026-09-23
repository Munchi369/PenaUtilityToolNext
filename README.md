# PenaUtilityToolNext

「ペナントシミュレーション3」のセーブデータを安全に閲覧・編集・分析するための非公式デスクトップツールです。

本プロジェクトは、[PenaUtilityTool](https://github.com/Munchi369/PenaUtilityTool)と並行して開発・提供している新しいツールです。

<img width="712" height="444" alt="image" src="https://github.com/user-attachments/assets/b9a2944c-8a10-4f3e-ae61-6f133dba06b1" />

## ダウンロード

Windows向けアプリの最新版は、GitHubの[Releases](https://github.com/Munchi369/PenaUtilityToolNext/releases/latest)からダウンロードできます。

`PenaUtilityToolNext-<バージョン>-windows-x64.zip`を展開し、フォルダ内の`PenaUtilityToolNext` フォルダを、`penanto3` フォルダと同じ階層に配置する

```text
ペナントシミュレーションのフォルダ/
├── penanto3/                   ← セーブデータフォルダ
├── ペナントシミュレーション.exe
└── PenaUtilityToolNext/            ← ここに配置
```

ゲーム本体は、[がらくたのペナントシミュレーション](http://garakutapsg.web.fc2.com/)からダウンロードできます。

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

### Windows向けアプリの作成

実行可能なアプリ一式を作成する場合は、リポジトリのルートで次を実行します。

```powershell
.\gradlew.bat createDistributable
```

生成先は次のディレクトリです。

```text
app/build/compose/binaries/main/app/PenaUtilityToolNext/
```

一部のDBテストはテストデータがある場合だけ実行されます。テストデータなしでもビルドできますが、DBを使うテストはスキップされます。

DBテストも実行する場合は、GitHubの[テストデータ](https://github.com/Munchi369/PenaUtilityToolNext/releases/tag/test-db)から`PenaUtilityToolNext-test-data-year05.zip`をダウンロードし、リポジトリのルートへ展開してください。配置後に次のファイルが存在すれば準備完了です。

```text
test-data/reference/year-05/penanto3-year05-end/service.properties
```

その後、もう一度`.\gradlew.bat check`を実行します。ゲーム本体、通常利用のセーブデータ、開発用の内部資料は、この公開リポジトリに含まれません。

## ライセンス

このリポジトリで公開するコードは [GNU General Public License v3.0](LICENSE) で提供します。

Copyright (c) 2026 Munchi369
