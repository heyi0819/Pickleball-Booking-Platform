# UX-P5F — Finance Read Model（PR A）

## 範圍與狀態

UX-P1～UX-P4 已封版；本變更只交付 UX-P5F 的唯讀後端、OpenAPI、generated client 與 handwritten adapter。
不更動付款／退款命令、idempotency、audit、outbox、角色模型、CORS、LIFF bootstrap 或既有 UI。
UX-P5 整體仍為 IN PROGRESS；Admin finance journey 與非財務操作改善屬 PR B，不能因 PR A 完成就宣告 UX-P5 SEALED。

## API 與組織邊界

新增 GET `/api/v1/admin/receivables`、`/payments`、`/refunds` 及各自的 `/{id}` detail。
所有端點均要求 bearer token 與明確的 `organizationId` query parameter。

- COMMITTEE：透過既有 `IdentityService.isAuthorizedForOrganization` 驗證 active user、active role 與 organization scope。
- PLATFORM_ADMIN：仍使用同一 server authorization policy，必須指定組織，不提供跨組織聚合或無 scope fallback。
- 未登入 401；無權存取所選組織 403；缺少／格式不合法 scope 400。
- 授權後查不到組織，或該組織內查不到 detail，均 404；foreign ID 與不存在 ID 的 detail 錯誤相同。
- 不新增身分或組織信任模型。平台管理員對既有組織的權限保持既有 global policy。
- `page >= 0`，`1 <= size <= 100`，預設 page 0 / size 20；offset 使用 long 避免溢位。
- 三種列表皆可依 status、memberId 篩選；應收另有 courseId、付款另有 receivableId、退款另有 paymentId。
- status 精確使用 persistence enum，不重新命名、大小寫轉換或靜默忽略錯誤 filter。
- 排序固定時間 DESC、id DESC，時間分別為 createdAt / paidAt / requestedAt。
- 所有參數都 bind；組織條件在 SQL 查詢內執行，不先載入全域資料再過濾。

## Read projection 與金額權威

DTO 僅包含操作需要的會員顯示名稱、組織、課程編號、帳務編號／關聯、金額、狀態與時間。
不回傳 email、LINE provider identity、付款備註、外部 reference、操作者資訊、idempotency key、failure reason 或完整 persistence object。
退款 reason 是既有操作原因，供審核查看。

應收金額直接讀取 receivables 的 total / adjusted / paid / refunded / balance，不重建帳務計算。
金額使用十進位字串，不轉浮點数。日期為 UTC ISO 時間；UI 顯示 Asia/Taipei 屬 PR B。

付款 `refundableAmount` 以既有 RefundStore request context 定義：
payment amount 減去 PENDING_APPROVAL、APPROVED、COMPLETED 退款保留額。
退款 detail 的同名欄位排除目前 request，對齊既有 review context。
這些欄位是同一讀取快照的參考值，**不是可執行動作或最終可退款金額的授權**；
request / review / execution 仍各自執行既有 lifecycle、locking、status 與 balance 檢查。
例如 VOIDED payment 不因顯示剩餘額而變得可退款。

付款與退款透過 allocations 批次取得 receivable / course references，不任意挑選第一筆，也不逐筆 N+1。
每一 read use case 使用 read-only REPEATABLE_READ transaction，使 count、page、references 與保留額使用一致快照。
GET 不 lock 帳務資料、不修正 ledger、不寫 audit/outbox/idempotency。

## Persistence 與 query review

只使用既有 organizations、users、courses、receivables、receivable_items、payments、payment_allocations、refunds。
無 schema 變更、無新 table、無 Flyway V14、無 dependency 或 runtime 設定變更。

已對照 V7 / V10 既有索引：

- receivables：organization/payer/status、course/status、PK。
- payments：organization/status/paid_at、payer/paid_at、PK。
- refunds：organization/status/requested_at、payment/status、PK。
- allocations：payment/item unique、receivable_item index。

回傳頁數受限；付款關聯以頁內 payment IDs 批次查詢。count 及無 status filter 的排序仍會依資料量增加成本；
目前沒有 production 大量資料的 query-plan / latency evidence，不宣稱已完成負載測試。
若後續實測出現瓶頸，先取得實際 SQL、EXPLAIN 與資料量，再回到 migration decision gate；不得自行新增 migration。

## 驗證與本機限制

新增 `AdminFinanceReadHttpIT`，使用 PostgreSQL 18 Testcontainers 與真實 HTTP/JWT：

- 全部六端點的 401、403、404、committee scope、platform explicit scope。
- revoked role 使用已簽發 token 仍回 403；suspended user 由既有 token filter 回 401 / AUTH_INVALID_TOKEN。
- filters、page bounds、大 offset、排序 tie-break、跨組織 relationship filter。
- exact decimal、nullable timestamps、readable references、status semantics。
- 既有退款保留狀態與排除當前 request 的計算。
- 所有 GET 前後 ledger、audit、outbox、idempotency snapshot 無變動。

新增三個前端 adapter tests：scope/filter/header、detail 與日期／金額、所有 read operation 安全錯誤。
本機已執行 frontend typecheck、lint、26 tests、雙 frontend build、api:check。
正式 backend / migration / container / smoke 結果以 PR 最終 HEAD 的 GitHub Actions evidence 為準，不以 local PASS 代稱 CI PASS。

本機 Maven 已完成一次有界診斷：USERPROFILE 正常，但 Java user.home 為 C:\\，Wrapper 解析至 C:\\.m2。
process-local user.home / cache / repository override 後可寫入 workspace cache，
後續 Maven distribution 下載受 TLS PKIX certificate-chain validation 阻擋（一般及受核准執行皆同）。
未修改 ACL、machine environment、TLS 驗證、全域 Maven、wrapper 或 CI；未提交 cache。
因此本機 backend compile/tests 未執行，保留全部測試交 CI。

OpenAPI Generator 的既有 3.1 beta / schema-name 警告仍存在；使用 repository 鎖定版本，不以改工具鏈消除。
本機 NO_COLOR / FORCE_COLOR 警告不影響測試，未變更設定。

本機 Playwright 在 page.goto 等待 load 時逾時；相同測試於受核准執行環境再跑一次為 6 PASS / 8 timeout。
未改動或跳過測試；CI run 33591515572 的 Frontend checks 與原有 14 條 Playwright 全部 PASS。
該首輪 CI backend 180 tests 中唯一失敗為新測試誤將 suspended user 預期為 403（實際 401）。
已依 IdentitySecurityIT 與既有 authentication filter 校正 assertion 並追加 error code / 無 data 驗證，未變更 production security。
修正後所有 required jobs 需在最終 HEAD 重新完整通過，最終 evidence 記錄於 PR body。

## Rollout / rollback / 下一步

此 PR additive 且未部署；部署與 merge 需 maintainer 明確授權。
沒有 migration 或資料回復步驟。若需 rollback，在尚未導入 PR B 時 revert PR A 即可；
PR B 使用新端點後需先回復 UI/client consumer 再撤回 read API，既有 command 與資料保持不變。
PR A merge 後須確認 main 五項 CI GREEN；再以 merged contract 交付 PR B 的 list → detail → confirmation → command journey。
UX-P5 全部 closure PASS 前不得開始 UX-P6；本文件不宣告 UX-P5 SEALED。
