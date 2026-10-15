# 舊版儲存庫初步盤點

盤點日期：2026-10-14

來源：https://github.com/imomi329/ipo_old-_20180512

檢查範圍：master 分支的檔案樹、README.md、index.html。未執行網站，也未檢查其他分支與歷史版本。

## 已觀察到的內容

- README 僅有 ipo 與 web 的簡短描述。
- 首頁 title 為 IPoLa，導覽品牌為 IPo。
- 導覽有 Home、尋覓、浪浪、商城、分享。
- 尋覓連結指向 _s/sHomepage.jsp；浪浪指向 _h/HHomeServlet。
- 會員選單包含登入、註冊、帳號與登出連結；有暱稱顯示區域。
- 使用 Bootstrap CSS／JavaScript，並引用 jQuery。
- 包含圖片資源與貓咪影片。

## 目前無法確認的部分

- master 檔案樹未見 Java、JSP、SQL 或 XML 檔案。
- 首頁所指向的會員、尋覓與浪浪路徑未出現在該檔案樹。
- 無法由現有內容確認地圖、動物辨識、餵養紀錄、資料模型或後端功能是否完成。
- index.html 引用 css/main.css，但該檔案未出現在檔案樹。

## 對重構的意義

這份儲存庫可作為舊版名稱、導覽與視覺素材的參考，目前不足以還原 Java／MySQL 系統。首頁入口不代表對應功能已實作。新系統範圍以使用者此次確認的需求為準，商城、分享與尋覓尚未納入第一版。
