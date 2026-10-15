# IPoLa 後端 v0.1

依目前已確認需求重新建立的 Java REST API。舊儲存庫沒有可用後端程式或 SQL，這次不包含舊資料遷移。

## 技術與執行

Java 21、Spring Boot 4.1.1、Spring Security、JDBC 參數化 SQL、Flyway。使用單一應用程式，先完成核心流程。

從 backend 目錄執行：

```sh
mvn test
mvn spring-boot:run
```

預設只監聽 127.0.0.1:8080；使用 H2 檔案資料庫，資料保存在 backend/data（需從 backend 目錄啟動）。整合測試使用獨立記憶體資料庫，不修改日常資料。

MySQL 模式需預先建立 UTF-8 資料庫及專用帳號，提供環境變數：

- `DB_URL`：例如 `jdbc:mysql://127.0.0.1:3306/ipola?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`
- `DB_USERNAME`
- `DB_PASSWORD`

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Flyway 自動套用 `src/main/resources/db/migration`。已執行的遷移不得修改，後續變更新增 V2、V3 等檔案。正式部署需 HTTPS、資料庫備份與環境設定；密碼不得提交到儲存庫。

## API

基底路徑 `/api`。JSON 時間使用含時區的 ISO 8601 格式；建議 UTC，例如 `2026-10-14T10:00:00Z`。

| 方法 | 路徑 | 權限／用途 |
|---|---|---|
| POST | /auth/register | 公開；註冊 |
| POST | /auth/login | 公開；登入並取得 24 小時有效的 token |
| POST | /auth/logout | 登入；撤銷目前 token |
| GET | /auth/me | 登入；目前會員 |
| GET | /animals | 公開；動物列表 |
| GET | /animals/{id} | 公開；動物資訊 |
| POST | /animals | 登入；建立動物 |
| GET | /observations | 公開；紀錄列表，可用 animalId 篩選 |
| POST | /observations | 登入；新增目擊／餵養紀錄 |
| GET | /members/{id}/observations | 公開；該會員的紀錄 |

列表接受 `limit`（1–100，預設 50）與 `offset`（非負整數，預設 0）；回應為 `{items, limit, offset}`。紀錄依事件時間降冪排序，同時間依 ID 降冪。

註冊：

```json
{"email":"demo@example.com","nickname":"橘貓照顧者","password":"a-long-example-password"}
```

登入：

```json
{"email":"demo@example.com","password":"a-long-example-password"}
```

登入回應包含 `accessToken`、`expiresAt` 與會員 `id`、`nickname`。後續請求帶 `Authorization: Bearer TOKEN`。密碼以 BCrypt 雜湊保存，token 只保存 SHA-256 雜湊。密碼至少 10 個字元且不得超過 72 UTF-8 bytes。

建立動物：

```json
{"species":"CAT","name":"公園橘貓","description":"白色尾尖"}
```

種類接受 `CAT` 或 `DOG`。新增紀錄：

```json
{"animalId":1,"kind":"FEEDING","observedAt":"2026-10-14T10:00:00Z","latitude":25.033123,"longitude":121.565432,"notes":"餵了一份罐頭","photoUrl":"https://example.com/cat.jpg"}
```

`kind` 接受 `SIGHTING` 或 `FEEDING`；`animalId` 可省略／null，代表尚未辨識。`photoUrl` 可省略／null，目前只保存 HTTPS 連結，不提供照片上傳。`notes` 必填，可為空字串。事件時間不可在未來，座標須有效；指定不存在的動物回傳 404。

常見狀態：建立成功 201、登出成功 204、輸入錯誤 400、未登入／無效 token 401、找不到動物 404、資料衝突（如重複 email）409。

## 位置規則與暫定決策

訪客的座標由伺服器轉成 0.01 度格網中心（在台灣約一公里量級），`locationPrecision=APPROXIMATE`；登入者取得原座標，`locationPrecision=EXACT`。所有紀錄列表共用相同處理。

公開紀錄的 `notes` 與 `photoUrl` 暫回傳 null，避免文字地址或照片中繼資訊洩漏詳細位置。動物名稱、外觀說明與會員暱稱仍公開，應避免輸入精確地址。概略精度、公開照片方式與動物關聯規則均為可調整的技術提案，尚非使用者最終業務決策。

系統只記錄歷史出現點，尚未計算活動範圍，不做自動動物辨識。

## 驗證與目前邊界

HTTP 整合測試涵蓋註冊、重複帳號、錯誤密碼、登入／登出、存取權限、兩種紀錄、未辨識紀錄、公開座標遮罩、登入座標、照顧者歷史、分頁與無效輸入。

目前測試資料庫為 H2；MySQL 設定與遷移已提供，但尚未在真正 MySQL 實例驗證。

第一版尚未包含：修改／刪除紀錄、動物合併、地圖視窗與距離查詢、照片上傳、管理員審核、密碼重設、登入頻率限制、正式部署。這些需依後續優先順序開發。
