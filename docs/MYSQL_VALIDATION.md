# I2：MySQL 可重現驗證與免費執行方式

查核日期：2026-11-13。範圍是測試環境，沒有連接正式資料庫、改動既有 migration 或新增 Maven 依賴。

## 執行方式

需要 Java 21 JDK、Maven 3.9、Docker daemon、Bash、OpenSSL。從儲存庫根目錄執行：

```sh
mvn -B -ntp -f backend/pom.xml verify  # H2
scripts/test-mysql.sh                 # 真實 MySQL：空資料庫與同資料庫重跑
```

腳本將官方 `mysql:8.4` 固定至本次驗證的 image digest；啟動只綁定 loopback 的隨機 TCP port，建立隨機測試密碼與專用帳號。資料放在一次性 tmpfs，結束只刪除該腳本建立的容器，不接收外部資料庫位址，也不清除既有資料庫。若執行被強制中止而 trap 未執行，僅清理名稱為該次 `ipola-mysql-test-...` 的容器；不可清理其他容器或 volume。

第一次啟動由 Flyway 在空 schema 套用 V1；第二次保留同一 schema 重新啟動測試應用並驗證 checksum 與 version，確認不需要 clean/reset。各次會員 email 隨機化，資料分頁以該次建立的 animal/member 篩選，避免重跑時污染預期。應用程式仍使用原 JDBC 與原 V1，不以 H2 的 MySQL 相容模式冒充 MySQL。

環境有代理時，Maven 須使用該環境支援的 settings 與 CA。可用 `MVN_BIN` 指定 Maven 執行檔，用 Maven 原生 `MAVEN_ARGS` 指定 settings；不停用 TLS 驗證。例如本次雲端已有的工具：

```sh
export JAVA_HOME=/workspace/.tools/jdk21
export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
export MVN_BIN=/workspace/.tools/apache-maven-3.9.11/bin/mvn
export MAVEN_ARGS='-s /workspace/.tools/maven-settings.xml'
scripts/test-mysql.sh
```

settings 應由執行環境提供，不提交代理位址或憑證。MySQL 測試容器只在本機通訊，沒有修改 SSL 或遠端資料庫的認證政策。

## 驗收內容

`ApiIntegrationTest` 的四個測試在 H2 與 MySQL 共用：

1. 註冊、重複帳號、錯誤密碼、登入、本人資料、未授權寫入、建立動物、目擊／餵養、訪客位置與文字遮罩、登入精確位置、會員歷史、登出撤銷。
2. 未辨識動物、非法座標／類型、不存在動物。
3. 由 JDBC metadata 斷言實際資料庫產品為 MySQL（H2 預設為 H2），並確認 Flyway V1 成功紀錄恰為一筆。
4. 同時間三筆紀錄依 ID 降冪、limit/offset 的不重複分頁、越界空頁、非法分頁、動物與會員兩種入口、訪客／登入者精度差異。

這些是原功能的驗證，不代表已批准或實作 I1/I4 新的內容可見性與本人修改／刪除政策。未包含負載、跨版本升級、備份還原或帳號復原測試。

## 證據與 CI

本機輸出：`backend/target/mysql-evidence/`，包含 image digest、MySQL 版本、fresh/repeat Maven log 與各次 Surefire XML／文字報告。該目錄在既有忽略的 target 內，不將測試密碼或本機資料庫提交到 Git。

`.github/workflows/backend-mysql.yml` 提供 H2/MySQL 兩個 job matrix，MySQL 執行相同腳本；即使失敗也嘗試保存 Surefire 與 MySQL evidence artifact。Action 採固定 commit，權限只讀 contents。此 workflow 僅是後端驗證，不宣稱完成整個專案的 lint、安全掃描或 coverage gate。

實際驗證結果見本文件下方。本次 GitHub Issue API 存取遭 Forbidden，且未建立遠端 CI run；**workflow 已備妥不等於 GitHub CI 通過**。推送至符合觸發條件的分支後，需保留 run URL、commit SHA、H2/MySQL job 結果與 artifact，才算完成使用者要求的遠端 CI 證據。

## 免費方式建議

- 開發與 staging 驗證優先使用本機 Docker 的 MySQL Community；資料庫軟體不需訂閱，電腦／主機與電力仍有成本。不必為 I2 先採購託管資料庫。
- CI 使用現有 GitHub Actions runner 配額執行一次性 MySQL 容器。私人儲存庫的免費分鐘／artifact 空間依帳號方案而異，超額可能計費；先檢查帳號 Billing、避免啟用付費超額，artifact 保留 14 天。公開儲存庫與私人儲存庫規則不同，不能為免費 CI 擅自改公開。
- 免費託管服務常有休眠、容量、連線、備份或信用卡限制；I2 不依賴任何雲端 MySQL 免費方案。正式資料及照片不建議以「永久免費」作可靠性承諾。

官方來源：

- [MySQL Community](https://dev.mysql.com/downloads/mysql/)
- [官方 MySQL Docker image](https://hub.docker.com/_/mysql)
- [GitHub Actions 計費](https://docs.github.com/en/billing/managing-billing-for-your-products/managing-billing-for-github-actions/about-billing-for-github-actions)
- [GitHub Actions artifacts](https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts)

## 本次執行結果

2026-11-13 Asia/Taipei，Java 21.0.12.1、Maven 3.9.11、官方 MySQL 8.4.13（utf8mb4）。

| 檢查 | 實際結果 | 本機報告 |
| --- | --- | --- |
| H2 `mvn verify` | 4 tests，0 failure／error／skip，BUILD SUCCESS | `backend/target/h2-evidence/` |
| MySQL 空 schema | 4 tests，0 failure／error／skip，V1 migration 成功，BUILD SUCCESS | `backend/target/mysql-evidence/fresh-reports/` |
| 同 MySQL 重跑 | 4 tests，0 failure／error／skip，V1 checksum 驗證通過、schema up to date，BUILD SUCCESS | `backend/target/mysql-evidence/repeat-reports/` |
| 腳本語法／差異空白 | `bash -n scripts/test-mysql.sh`、`git diff --check` 通過 | 本次命令 |
| GitHub Actions | **未執行**；無 run URL／遠端 artifact | 等待可用遠端操作與 workflow 觸發 |

腳本 exit 0，該次一次性 MySQL 容器已由 EXIT trap 刪除。三個 Actions commit 均以官方 Git `ls-remote` 核對 tag。下列 SHA-256 用於比對本次本機證據，不替代遠端 CI run，也不包含密碼或完整執行 log：

| 檔案 | SHA-256 |
| --- | --- |
| `backend/src/main/resources/db/migration/V1__initial_schema.sql` | `e352bb2ee83e9bf82afa75c0b1240d77aaf406b723639eaf33051f66b7baf85a` |
| `backend/src/test/java/tw/ipola/ApiIntegrationTest.java` | `d9c4994db801f85eda17122cab9a692974bfdcd341f62d55a724779cf046faa3` |
| `backend/target/h2-evidence/TEST-tw.ipola.ApiIntegrationTest.xml` | `c2ab9a4ae106020ded06bd0c2f56c5995a91e5514eed546a9b56051a02a1ee94` |
| `backend/target/mysql-evidence/fresh-reports/TEST-tw.ipola.ApiIntegrationTest.xml` | `7236149c27f01ba6c416c519ddd0b8ede0d2773c78a63a3456fdaf81748f8835` |
| `backend/target/mysql-evidence/repeat-reports/TEST-tw.ipola.ApiIntegrationTest.xml` | `a13fd8f816c9f49761d6fdcb8cdbcde0a0a444aa761f2969771482db0d3d7e1f` |
