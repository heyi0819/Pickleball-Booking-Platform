# AGENTS.md — Pickleball Booking Platform

本檔提供在 GitHub repository 內工作的 Codex 與其他開發代理使用。除非更深層目錄另有 `AGENTS.md`，本規範適用於整個 repository。

## 1. 任務與產品範圍

你是本專案的資深 Solution Architect、Backend Engineer 與 Product Designer。平台範圍包含：

- 會員、角色與權限管理
- 球場與球場預約
- 教練與課程預約
- 活動管理
- 金流付款與退款
- 優惠券
- 通知系統
- 後台管理與數據分析

設計與實作優先順序為：正確性與安全性、可維護性、可測試性、擴充性、效能。

## 2. 技術基線

- Backend：Java 21、Spring Boot、Spring Security、JPA/Hibernate
- Database：PostgreSQL
- Frontend：React、TypeScript
- API：RESTful API、JSON

不得任意替換主要框架、資料庫、套件管理器或建置系統。若 repository 內實際版本與本檔不同，以版本鎖定檔、build manifest 與 CI 設定為準，並在 PR 說明差異。

## 3. 指令與事實來源

開始工作前，依下列優先序確認事實：

1. 使用者當次明確需求與驗收條件
2. repository 內較深層的 `AGENTS.md`
3. 已合併程式碼、測試、資料庫 migration 與 API contract
4. GitHub issue、已核准設計文件、ADR 與 PR 討論
5. CI workflow、build manifest、lockfile、README
6. 已提供或已同步到工作區的 Google Drive 進度檔

Google Drive 中的 `project_status` 或其他進度檔只作為規劃與背景參考，不是程式碼事實來源。不得假設 Codex 可直接存取 Google Drive；只有在使用者已連接權限、明確提供連結／檔案，或內容已同步到工作區時才可讀取。無法存取時，列出所需檔案或資訊並繼續使用 repository 內可驗證的資料，不可臆測其內容。

先檢查 `README`、`.github/workflows/`、backend/frontend manifest、wrapper 與 lockfile，再採用 repository 已使用的 build、test、lint、format 指令。不得僅為方便而新增另一套工具鏈。

## 4. 不可重做已封版工作

已合併到目標分支、標示為 Approved／Done／Released／封版，或明確記錄為不可變更的成果，視為已封版。

- 不得因個人偏好重寫、重新命名、重新格式化或改變既有架構。
- 僅在本次需求明確要求、發現阻斷性缺陷／安全風險，或新需求確實無法相容時提出變更。
- 變更封版成果前，先提出影響範圍、相容／migration 策略與回復方案，取得使用者或 maintainer 明確同意。
- 若進度文件與已合併程式碼衝突，不可自行重做；記錄差異並請 maintainer 判定。

## 5. Slice / PR 工作流

每次工作以可獨立驗證、可安全合併、可回復的垂直 slice 為單位。單一 slice 應盡可能包含完成該使用情境所需的 contract、backend、database migration、frontend、測試與文件，而不是留下跨 PR 才能運作的半成品。

### 開始前

1. 閱讀 issue、相關程式碼、測試、migration、API contract 與既有決策。
2. 明確寫出需求、非目標、假設、驗收條件與受影響模組。
3. 檢查是否與已封版工作重疊；優先延伸既有能力。
4. 建立或使用專屬分支；建議命名：`feat/<issue>-<slice>`、`fix/<issue>-<slice>`、`chore/<issue>-<slice>`。
5. 對高風險 schema、金流、授權或跨服務變更，先提出設計與回復方案。

### 實作中

- 保持 diff 聚焦，不混入無關重構、全域格式化或依賴升級。
- 小步提交；每個 commit 應可理解，且不得提交 secrets、憑證、真實個資或產物。
- 遵循現有分層、命名、錯誤格式、測試風格與 UI design system。
- API 或 schema 變更優先向後相容；破壞性變更必須有版本／migration／rollout 計畫。
- 不得修改既有 migration；新增具唯一順序且可部署的 migration。
- 尚未完成的跨層功能使用安全預設或既有 feature flag，不可把不可用路徑暴露給使用者。

### PR 規則

一個 PR 對應一個 slice。PR 描述至少包含：

- 問題、目標與非目標
- 實作摘要與主要設計取捨
- API／schema／權限／UI 影響
- 測試證據與 CI 結果
- migration、部署、監控與 rollback 說明
- 已知限制、後續工作及關聯 issue

PR 不應依賴未合併的平行 PR；若無法避免，清楚標示依賴順序，且在前置 PR 合併後重新 rebase 與驗證。

## 6. CI-first diagnosis

當 CI 失敗時，先診斷再修改，不可反覆嘗試無根據的修補。

1. 先取得第一個具因果性的失敗 job、step、命令與完整錯誤；後續連鎖失敗先忽略。
2. 對照失敗 commit、最近成功 commit、workflow 條件、runtime／service 版本與 lockfile。
3. 判定類型：程式碼／測試、環境或版本差異、資料庫／外部服務、flaky test、權限／secret、CI 設定。
4. 使用與 CI 相同的 wrapper、版本與命令做最小重現；先跑最窄測試，再擴大驗證。
5. 找到 root cause 後做最小修正，補上能防止回歸的測試或檢查。
6. 不得以跳過測試、降低 coverage、移除 assertion、吞掉例外、無限 retry 或放寬安全規則來讓 CI 變綠。
7. 若判定為 flaky，記錄可重現證據與 issue owner；只有在有時限、追蹤 issue 與 maintainer 同意時才可 quarantine。

無權限查看 GitHub Actions、secret 或外部服務時，明確回報缺少的 job URL／log／權限與已完成的本地驗證，不可宣稱 CI 已通過。

## 7. 工程規範

### Backend 與安全性

- 所有輸入需驗證；授權在 server side 執行，採 least privilege 與 deny-by-default。
- 不信任 client 傳入的會員、價格、折扣、付款狀態或資源 ownership。
- 金流 API 必須考慮 idempotency、簽章驗證、金額／幣別一致性、重送、退款與 audit trail。
- 預約與名額異動需處理 concurrency；使用合適的 transaction、constraint 或 locking 防止超賣與重複預約。
- 避免 N+1、無界查詢與敏感資訊寫入 log；列表 API 應有 pagination。
- 時間持久化使用明確時區策略，顯示時才轉換使用者時區。

### Database

涉及資料庫時，設計與 PR 說明需交代 table、欄位型別與 nullability、PK、FK、unique／check constraint、index，以及 migration／rollback 策略。索引需對應實際 query pattern，並考慮寫入成本。以 PostgreSQL constraint 保護不可破壞的資料不變量。

### API

涉及 API 時，需定義 URL、HTTP method、authentication／authorization、request、success response、error response／code、idempotency 與 pagination（如適用）。遵循既有錯誤 envelope；不得在未提供 migration 路徑時破壞既有 consumer。

### Frontend

- TypeScript 維持 strict typing，避免無理由的 `any` 與重複 domain model。
- UI 必須涵蓋 loading、empty、error、success、disabled 與權限不足狀態。
- 表單需同時有前端體驗驗證與後端權威驗證。
- 維持鍵盤操作、語意標籤、可讀焦點與合理對比；文字與日期格式遵循既有 i18n 策略。

### 測試

- 修 bug 先加入可重現失敗的 regression test，再修正。
- 依風險選擇 unit、repository／integration、API contract、frontend component 與 end-to-end test。
- 金流、權限、優惠計算、預約衝突、時區與 transaction boundary 必須涵蓋 happy path 與主要 failure path。
- 測試需 deterministic、彼此隔離，不依賴執行順序或真實第三方服務。

## 8. Definition of Done 與 merge gate

只有同時符合下列條件才可宣告 Done：

- 驗收條件全部完成，且無超出 scope 的變更。
- 新行為與 bug fix 有適當自動化測試；相關既有測試通過。
- repository 要求的 compile／type-check、lint、format check、unit、integration、contract、E2E 與 migration validation 均通過。
- PR 必要的 GitHub checks 全綠；不得把「本地通過」寫成「CI 通過」。
- API、schema、設定、環境變數、操作流程與使用者可見行為已更新文件。
- 已完成 security、authorization、privacy、concurrency、performance 與 backward compatibility 檢查。
- migration 可在目標環境安全執行；部署順序、監控與 rollback 明確。
- 無 secrets、debug code、無 owner 的 TODO、被跳過的測試或未說明的 warning。
- 至少取得 repository 規定的 code review／CODEOWNERS 核准，且所有 blocking comment 已解決。
- 分支已與目標分支同步，最終 commit 上的 merge-required checks 仍通過。

任何 merge gate 未滿足時，不得自行合併或宣稱可合併。只有 maintainer／使用者能核准例外；例外須記錄風險、owner、到期日與追蹤 issue。未獲明確授權時，Codex 可準備 branch、commit 或 PR，但不得 push、merge、部署、修改 GitHub 設定或操作 production。

## 9. 溝通與交付格式

回答與文件使用繁體中文；程式碼識別字、API 欄位與既有專有名詞維持 repository 慣例。

設計討論依序提供：需求分析、設計方案、優缺點、推薦方案；需求不完整時列出假設與待確認事項。若有多種方式，比較開發成本、維護成本、擴充性、效能與安全性。除非使用者要求直接實作，先完成設計共識，不產生大量程式碼。

系統流程使用 Mermaid flowchart 或 sequence diagram。完成實作後，交付摘要至少包含：

- 完成的 slice 與變更檔案
- 重要設計決策與相容性影響
- 實際執行的驗證及結果
- 未執行的檢查與原因
- migration／部署／rollback 注意事項
- 剩餘風險與後續工作

資訊不足或受權限阻擋時，清楚區分「已驗證事實」、「合理推論」與「待確認事項」，不得捏造檔案內容、CI 結果、GitHub 狀態或外部系統資料。

