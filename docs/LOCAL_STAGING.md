# 已核准第一版：本機 staging 與驗收

本文件對應使用者「核准變更」後的實作。只在 loopback 執行，不代表已建立可供外部測試者使用的 staging 或正式服務。

## 工具與啟動

需要 Java 21 JDK、Maven 3.9、Node.js 24、npm；真實 MySQL 測試另需 Docker。環境有代理時保留既有代理與 CA 信任設定。

在目前雲端機器，JDK／Maven 位於 `/workspace/.tools`；Maven 使用既有 settings 與 CA。若預設家目錄不可寫，使用 `npm --cache=/workspace/.cache/npm ...`，不要停用套件完整性檢查。

```sh
cd backend
mvn -B -ntp verify
# 在安全的環境設定提供 IPOLA_INVITATION_CODES，未提供時拒絕新註冊。
java -jar target/ipola-backend-0.1.0-SNAPSHOT.jar
```

另開終端：

```sh
cd frontend
npm ci
npm run build
npm run dev
```

Vite 僅監聽 loopback，將 `/api` 代理至後端 8080；可用 `IPOLA_API_TARGET` 更換本機後端位址。`npm run preview` 只預覽靜態產物，未提供 API proxy，不能直接視為整合部署。正式／外部 staging 需獨立的同源 HTTPS 靜態檔與 API 反向代理、私有儲存、資料庫與網路保護；本次沒有安裝或發布這些外部服務。

## 邀請與帳號

`IPOLA_INVITATION_CODES` 格式為逗號分隔的 `email:code` 配對。每個測試者使用不同的高熵 base64url code（至少 32 字元），由營運者透過受信任管道交付。不可將真實邀請碼放進 Git、網址、日誌或聊天。僅 email 名單無法防止冒領，因此註冊同時要求對應 invitationCode；未設定、缺碼或錯碼均拒絕。email 的唯一約束使成功啟用後無法再次建立同 email 帳號。

前端提供「受邀測試者建立帳號」，填寫信箱、暱稱、密碼與邀請碼。沒有自動寄邀請信、信箱驗證或密碼復原。公開註冊開關 `ipola.registration.open=true` 僅供隔離合成資料測試使用，不得放進外部 staging 的啟動設定。

## 可見性與本人操作

- 訪客：紀錄種類、時間、暱稱、動物關聯及 0.01 度格網中心；無備註或照片。
- 其他有效登入者：同上並可讀原座標；仍無他人的備註、照片 URL 或照片存在性。
- 本人：可讀自己的備註與私有照片，可修改、刪除紀錄或取代／移除照片。
- 地圖預設只有紀錄點，不連第三方圖磚；使用者明確選用 OSM 才發送第三方圖磚請求。圖磚顯示不計算活動範圍，不自動辨識／合併動物。

所有動態文字以文字節點呈現。Token 只在記憶體；重新整理需重登，登出及收到失效 token 回應時清除私人畫面與照片 Blob URL；過期中的非同步回應不能重畫舊私人資料。

## 修改、照片與清除

列表包含 `version`。`PUT /api/observations/{id}` 傳入 `{version,animalId,kind,observedAt,latitude,longitude,notes}`；`DELETE /api/observations/{id}?version=N` 刪除。本人版本過期回 409，其他會員操作回 404，訪客回 401。

照片採 `POST /api/observations/{id}/photo?version=N`，multipart 欄位 `file`；`GET` 同一路徑需本人 token，`DELETE` 同一路徑需 version。修改或照片變更後重新載入取得新版，避免以舊版本操作。

私有儲存預設 `/workspace/ipola-private/photos`，可用 Spring property `ipola.photos.directory` 指定 checkout 外的持久目錄。檔案 600、目錄 700，不由靜態伺服器公開。新圖限 JPEG／PNG、5 MiB、12 百萬像素；先查尺寸再解碼，拒絕動畫 PNG，以 EXIF orientation 旋正，最長邊縮至 1600px，重新編成不帶原始中繼資訊的 JPEG。Servlet multipart 與影像 input/output cache 均設為記憶體，避免合法原照被預設 cache 寫進系統暫存。去中繼資訊不會移除照片像素中的人臉、門牌或車牌。

舊 `photoUrl` 不自動下載、嵌入或當作私有照片回傳；原資料未批次刪除。本人可重新上傳或移除舊關聯。照片回應採 `private, no-store` 與 `nosniff`。

刪除紀錄時，資料庫交易直接刪除敏感紀錄、保存不含內容的最小操作／刪除 ledger，提交後立即刪除照片。取代新圖失敗不移除原圖，交易 rollback 清除新圖。若提交後實體照片清除失敗，已無 API 可讀，但會記錄無私人路徑的錯誤，**仍需營運者清理孤兒檔**；沒有自動重試佇列或 24 小時清除保證。尚無稽核到期清除、備份輪替／還原程序的實作；7 天備份等數字仍是待定營運提案。

## 重現驗收

```sh
# 在 repo root
scripts/test-mysql.sh       # 真實 MySQL 空庫及同庫重啟
npm --prefix frontend ci
npm --prefix frontend run build
node scripts/mobile-smoke.mjs
```

瀏覽器測試需要 Node 24 與 Chromium（預設 `/usr/bin/chromium`；可設 `CHROME_BIN`）。它會啟動自己管理的後端 18080 與 Vite 15173，使用隔離記憶體 H2、合成帳號與圖片，結束清理子程序及私有暫存。不要與同埠服務一起執行。測試不連外部圖磚、不是 MySQL 手機 E2E，也不是公開部署驗收。

腳本會先 bind 檢查兩埠可用，並等候自己啟動的程序回報 ready；同埠已被占用即拒絕建立測試帳號。已實測占用埠時非零退出且不連接既有服務，避免污染現有資料。每次執行重設結果狀態與截圖，報告含起訖時間；失敗不能沿用舊的 passed 結果。

本機結果寫入 `artifacts/mobile-smoke.json` 與手機截圖；該目錄已忽略，截圖僅含合成資料。CI 保存結果 artifacts，只有取得實際 run／artifact 才能宣稱遠端通過。

## 外部 staging／正式發布仍需完成

指定測試者與安全交付邀請碼、選定持久儲存與資料庫、同源 HTTPS 及網路保護、實際備份還原與刪除重播、營運與濫用處理負責人。帳號復原、限流、警示及公開發布依 [上線清單](STAGING_AND_LAUNCH.md) 推進；本輪未將原建議視為已完成營運能力，也未安排未確認的發布日期。
