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

實際驗證結果見本文件下方。已推送符合觸發條件的 `feat/ipola-i1-i5-handoff`，測試提交為 `cbe3537b8f1dae7e38dd15dbb6f423e3def8d137`。GitHub Issue 及 Actions runs API 均回覆 Forbidden，無法確認遠端是否啟動或完成；**workflow 已推送不等於 GitHub CI 通過**。需保留 run URL、commit SHA、H2/MySQL job 結果與 artifact，才算完成使用者要求的遠端 CI 證據。

## 免費方式建議

- 開發與 staging 驗證優先使用本機 Docker 的 MySQL Community；資料庫軟體不需訂閱，電腦／主機與電力仍有成本。不必為 I2 先採購託管資料庫。
- CI 使用現有 GitHub Actions runner 配額執行一次性 MySQL 容器。私人儲存庫的免費分鐘／artifact 空間依帳號方案而異，超額可能計費；先檢查帳號 Billing、避免啟用付費超額，artifact 保留 14 天。公開儲存庫與私人儲存庫規則不同，不能為免費 CI 擅自改公開。
- 免費託管服務常有休眠、容量、連線、備份或信用卡限制；I2 不依賴任何雲端 MySQL 免費方案。正式資料及照片不建議以「永久免費」作可靠性承諾。

官方來源：

- [MySQL Community](https://dev.mysql.com/downloads/mysql/)
- [官方 MySQL Docker image](https://hub.docker.com/_/mysql)
- [GitHub Actions 計費](https://docs.github.com/en/billing/managing-billing-for-your-products/managing-billing-for-github-actions/about-billing-for-github-actions)
- [GitHub Actions artifacts](https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts)

## 首批規格階段執行結果（保留歷史）

2026-11-13 Asia/Taipei，Java 21.0.12.1、Maven 3.9.11、官方 MySQL 8.4.13（utf8mb4）。

| 檢查 | 實際結果 | 本機報告 |
| --- | --- | --- |
| H2 `mvn verify` | 4 tests，0 failure／error／skip，BUILD SUCCESS | `backend/target/h2-evidence/` |
| MySQL 空 schema | 4 tests，0 failure／error／skip，V1 migration 成功，BUILD SUCCESS | `backend/target/mysql-evidence/fresh-reports/` |
| 同 MySQL 重跑 | 4 tests，0 failure／error／skip，V1 checksum 驗證通過、schema up to date，BUILD SUCCESS | `backend/target/mysql-evidence/repeat-reports/` |
| 腳本語法／差異空白 | `bash -n scripts/test-mysql.sh`、`git diff --check` 通過 | 本次命令 |
| GitHub Actions | **未取得遠端執行結果**；分支已推送，runs API 回覆 Forbidden | 尚無可驗證的 run URL／遠端 artifact |

腳本 exit 0，該次一次性 MySQL 容器已由 EXIT trap 刪除。三個 Actions commit 均以官方 Git `ls-remote` 核對 tag。下列 SHA-256 用於比對本次本機證據，不替代遠端 CI run，也不包含密碼或完整執行 log：

| 檔案 | SHA-256 |
| --- | --- |
| `backend/src/main/resources/db/migration/V1__initial_schema.sql` | `e352bb2ee83e9bf82afa75c0b1240d77aaf406b723639eaf33051f66b7baf85a` |
| `backend/src/test/java/tw/ipola/ApiIntegrationTest.java` | `d9c4994db801f85eda17122cab9a692974bfdcd341f62d55a724779cf046faa3` |
| `backend/target/h2-evidence/TEST-tw.ipola.ApiIntegrationTest.xml` | `c2ab9a4ae106020ded06bd0c2f56c5995a91e5514eed546a9b56051a02a1ee94` |
| `backend/target/mysql-evidence/fresh-reports/TEST-tw.ipola.ApiIntegrationTest.xml` | `7236149c27f01ba6c416c519ddd0b8ede0d2773c78a63a3456fdaf81748f8835` |
| `backend/target/mysql-evidence/repeat-reports/TEST-tw.ipola.ApiIntegrationTest.xml` | `a13fd8f816c9f49761d6fdcb8cdbcde0a0a444aa761f2969771482db0d3d7e1f` |

## 核准後最終實作驗證

使用者核准邀請制、本人內容／修正與前端依賴後，測試擴充為 9 項（ApiIntegrationTest 8 項、InvitationIntegrationTest 1 項）。最終 H2、MySQL 8.4.13 空庫及同庫重啟三輪均為 **9 tests，0 failure／error／skip，BUILD SUCCESS**。MySQL 首輪套用 V1＋V2，第二輪驗證相同 schema 已最新。最終照片處理的 multipart threshold、ImageIO 輸入／輸出記憶體 cache 已納入三輪相同來源驗證。

新增驗證：本人／其他登入者所有列表的內容遮罩、本人 PUT／DELETE 與版本衝突、跨會員拒絕、私有照片端點、PNG／JPEG 重新編碼及 metadata 清除、EXIF orientation6、1600px 縮圖、超尺寸／超像素／錯格式拒絕、邀請碼缺少／錯誤／正確／重放。正負端點格網也已回歸。這些不包含備份還原、限流或帳號復原。

| 當輪報告 | SHA-256 |
| --- | --- |
| H2 `h2-approved-evidence/TEST-tw.ipola.ApiIntegrationTest.xml` | `d885b56f3a0433bda14f4f84a958bb62c6068a141656a6e44b526e0e899fac36` |
| H2 `h2-approved-evidence/TEST-tw.ipola.InvitationIntegrationTest.xml` | `e07137210948812642b256a30f30f5260e6042478d579a12d9ca09cb550bb528` |
| MySQL `fresh-reports/TEST-tw.ipola.ApiIntegrationTest.xml` | `c8d8ae0365f05ac38ac73d466abdd67d0c4ae162f5c142fa56c99057454fe746` |
| MySQL `fresh-reports/TEST-tw.ipola.InvitationIntegrationTest.xml` | `1b3ad31f75703bc2dd1c52c4482f81f79bf5aed5a274567376886e76819b7b0c` |
| MySQL `repeat-reports/TEST-tw.ipola.ApiIntegrationTest.xml` | `288333cad7c5561ebb2dd6d2a2f8da0756e9c60c708737cf26bfdfdf6a161ea3` |
| MySQL `repeat-reports/TEST-tw.ipola.InvitationIntegrationTest.xml` | `11a9cddf68e9f731b13486e5569ff1803e433ad11f0580cd9f26341c3ebc14c4` |

H2 目錄位於 `backend/target/h2-approved-evidence/`；MySQL 目錄位於 `backend/target/mysql-evidence/`。新報告取代先前 4 項階段結果，舊 hash 僅作歷史，不應與目前輸出比對。

前端 TypeScript strict＋Vite build、完整 npm audit（0 vulnerabilities）已通過。Node 24 原生 CDP＋系統 Chromium 的手機 smoke 在隔離 H2、真實 HTTP API 通過 16 項檢查，含 360／390／1280px、登入、兩種紀錄、定位拒絕與手動輸入、本人內容／照片／修正刪除、分頁、登出與實際撤銷 token。命令 `node scripts/mobile-smoke.mjs`，結果 `artifacts/mobile-smoke.json` 與合成資料截圖；不是 MySQL 手機 E2E，也不是外部 staging。

CI workflow 已加入 Node 安裝、前端 frozen install／build／audit、瀏覽器 smoke 與 artifacts。遠端 API 存取仍受限，必須另取得 run 結果後才可標遠端 CI 完成。最新啟動與限制見 [本機 staging](LOCAL_STAGING.md)。
