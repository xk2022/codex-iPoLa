# IPoLa I1–I5 執行與交接

查核日：2026-11-13。授權來源：使用者本次交辦 I1–I5，由執行總管拆解、分派、整合與驗收。分支 `feat/ipola-i1-i5-handoff`。本文件暫作主要任務草稿；GitHub GraphQL 與 REST Issue 讀取均回覆 Forbidden，未能確認既有 Issue，因此未另建重複任務。

## 目標與範圍

**本次核准更新**：使用者回覆「核准變更」，採用邀請制先測試、TypeScript／Vite／Leaflet／Bootstrap，以及照片／備註僅本人可讀、本人可修改／刪除。精確位置維持有效登入者可讀。下面原草案的待決描述保留作背景，以本段授權與後續實作證據為準；正式部署、付費資源及未核定的營運保存期限不在此次核准內。

第一版完成手機「登入 → 地圖／列表 → 目擊／餵養 → 本人紀錄」，以真實 MySQL 驗證後端，以 staging 驗證發布程序。活動區域推估、自動辨識與動物合併另列下一期。正式部署、付費資源、安全政策變更及新增依賴依專案 AGENTS.md 先核准。

| 工作 | 負責職能 | 驗收與證據 | 相依／門檻 |
| --- | --- | --- | --- |
| I1 隱私與內容規則 | 產品規格 Agent；總管整合 | [規則與權限矩陣](PRODUCT_PRIVACY_AND_CONTENT.md)；本次核准範圍已實作 | 其他登入者保留精確座標，私人內容僅本人 |
| I2 真實 MySQL | 資料庫開發 Agent；總管驗收 | [重現與證據](MYSQL_VALIDATION.md)；migration、核心 API、權限、分頁與 CI 報告 | 以執行紀錄為準，workflow 檔案不等於 CI 成功 |
| I3 手機閉環 | 前端 Agent；總管瀏覽器驗收 | 登入→地圖／列表→兩類新增→本人紀錄已實作 | TypeScript strict build 與真瀏覽器驗證 |
| I4 照片與修正 | 後端 Agent；測試 Agent | 私有照片、本人修改／刪除、版本衝突已實作 | 清除失敗重試與保存期限仍未實作 |
| I5 staging 與發布 | 上線研究 Agent；總管整合 | [上線清單](STAGING_AND_LAUNCH.md) | 開放對象、寄信與儲存環境、staging 先驗；正式發布另核准 |

## 已核准的前端技術

採 TypeScript + Vite + Leaflet + Bootstrap，先用瀏覽器 DOM 與獨立 API Service，不增加前端框架。Vite／TypeScript 為建置依賴；Leaflet 與 Bootstrap 為執行期依賴。以同源反向代理轉送 `/api`，避免為前端修改既有認證與 CORS 政策。地圖必須標出資料精度、來源標示，提供等效列表與手動座標；供應商未選定時不自動傳送真實位置或載入第三方圖磚。

Token 只保留在記憶體，重新整理需重新登入；不把 token 寫入 URL 或持久儲存。登入、動物選擇／未辨識、兩種紀錄新增、本人歷史、分頁、照片、修改／刪除已完成本機驗證。版本固定於前端 lockfile；Vite 已更新同 major 安全修補，完整 npm audit 為 0 vulnerabilities。

## 排程與追蹤

本輪為已授權的 AI 工作與驗收，依 I1／I4、I2、I5 方案平行進行，總管整合。後續 I3 與 I4 程式、staging 隨前置決策完成依序推進；未承諾產品交付日期。

Google 日曆：**未同步**。本次沒有可呼叫的日曆讀寫工具，無法查詢既有事件或建立、讀回事件；未捏造 event_id，未建立外部行事曆替代。恢復工具後先查重再同步。

GitHub Issue：**未同步**。Git 可 fetch 不代表 API 可用；GraphQL 與 REST 都回覆 Forbidden。解鎖後先查既有主要任務，再將本文件驗收條件、狀態及證據回填；產品任務只留產品庫。

已進一步確認未帶憑證的 `api.github.com` CONNECT 也被代理回覆 403，當前 runtime allowlist 尚未包含該網域。已在開發環境草稿新增 `api.github.com`（保留既有 preset），並儲存新前端安裝／啟動指引；runtime 狀態尚未套用。使用者需在環境設定檢閱、儲存並發布開發環境，之後重新驗證實際 API 存取；網路放行本身不保證 GitHub API 權限，不要求另提供 PAT。

版本交付：測試與規格提交 `cbe3537b8f1dae7e38dd15dbb6f423e3def8d137` 已推送至 `origin/feat/ipola-i1-i5-handoff`。未建立 PR、未合併 main。Actions runs API 同樣 Forbidden，僅能確認推送符合 workflow 觸發條件，不能確認 run 是否啟動或成功。可由 GitHub 的該分支 Actions 頁查看；恢復 API 後總管應讀回結果與 artifact。

## 本輪交付狀態

I1／I3／I4：核准範圍已實作並完成本機驗證，詳見 [啟動與限制](LOCAL_STAGING.md)。I5：上線建議與驗收門檻，尚未建立外部 staging／公開服務；復原、限流、備份還原及營運流程未視為完成。

I2：最終版 H2、MySQL 8.4.13（utf8mb4）空庫與同庫重啟三輪各 9 項測試通過，0 失敗／錯誤／跳過；首輪套用 V1＋V2，次輪 schema 已最新。真瀏覽器手機流程在隔離 H2 通過 16 項檢查，包含 360／390／1280px、照片、他人內容隱私、登出與實際撤銷 token、分頁。總管及獨立驗收 Agent 已讀回報告。CI 已擴充前端 build／audit／手機流程並保存 artifacts，但遠端結果仍因 API Forbidden 無法確認，I2 不標遠端 CI 完成。具體指令與證據見 [MySQL 驗證](MYSQL_VALIDATION.md)。

獨立審查已修正 I4／I5 備份保存期的草案衝突，統一為 7 天輪替；刪除清單涵蓋所有可還原備份生命週期。這些仍是待核准的營運政策，不是已執行的備份。

本次核准已落實邀請碼註冊（僅 email 名單無法防冒領）、本人照片／備註與修正刪除。照片限制採已提出的 5 MiB／12MP／1600px 第一版實作，地圖預設不連第三方。尚需指定實際測試者與安全交付邀請碼、外部 staging 主機／持久儲存／HTTPS 與網路保護、營運責任人及保存期限。未啟用付費資源、公開註冊或正式發布。

下一期：活動區域推估、自動辨識／合併；另定需求與驗收，不混入本期關聯功能。
