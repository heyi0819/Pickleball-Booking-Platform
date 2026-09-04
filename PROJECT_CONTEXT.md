# PROJECT_CONTEXT.md

# Pickleball Booking Platform — Codex Handoff Context

> Last synchronized project status: 2026-09-04
>
> Current phase: **S11.2 — Zero-Cost Pilot Identity / Runtime Readiness**
>
> Current gate: **S11.1 is SEALED. PR #58 merged at authoritative main commit `c2266df5cd13c72f1a76bc971535e12fc898a43b`; post-merge main CI Run `33839609912` is GREEN, and the S11.2 evidence checkpoint is SEALED. Maintainer acceptance confirms the zero-cost provider boundary, LIFF/Admin authentication, authenticated `/api/v1/me`, logout/re-login, student privilege denial, and Render Free cold-start behavior. S11.2A records that approved coach availability is a student demand-selection input, not an automatically published bookable offering; its trace/closure PR is pending. Backup procedure readiness is recorded; full restore proof is deferred to S11.4. Frontend monitoring coverage is a P1 improvement, not a P0 seal blocker. The active runtime remains Cloudflare Pages Free → Render Free → Neon Free; S11.1C is DEFERRED.**
>
> Next task: **Merge the focused S11.2A availability/student-visibility trace after its CI Gate. Then assess S11.2 closure only from merged, green evidence. Do not start S11.3, provision GCP, upgrade any provider plan, purchase a domain, add paid monitoring, create production LINE resources, or modify pilot runtime configuration without separate authorization.**
>
> Codex model / reasoning policy is canonical in repository-root `AGENTS.md`: routine approved implementation defaults to **GPT-5.6 Terra / Low**; Codex must explicitly warn the maintainer before tasks that materially benefit from higher reasoning; major project planning and architecture decisions return to ChatGPT using **GPT-5.6 with the highest available reasoning effort**. Quality and verification take priority over execution speed.
>
> Important: **This header is the current implementation snapshot. The detailed roadmap and historical baseline sections below are retained as design/history context and must not be used to restart a sealed Slice.**

---

## 1. 專案目的

Pickleball Booking Platform 是一套以「匹克球課程需求、教練媒合、公開招生與課程營運」為核心的平台。

MVP 的主要目標不是做即時球場庫存訂位，也不是先做線上金流，而是協助：

- 學員提出課程需求、指定或不指定教練、查看課程與付款狀態。
- 教練建立可授課時段、接受或拒絕邀請、查看授課與結算。
- 委員會審核教練、審核需求、媒合課程、公開開班、安排場地、確認價格、處理取消/改期/退款、記錄收款與完成教練結算。
- 平台管理員管理使用者、角色、組織、Audit、Outbox 與跨組織異常處理。
- 透過 LINE LIFF 提供行動入口，並以 LINE 通知作為主要通知整合方向。

### 1.1 核心產品定位

平台是「課程媒合與營運系統」，不是單純 CRUD 後台。

兩條主要成班路徑：

1. **Demand / Supply Matching**
   - 學員提出 Lesson Request
   - 教練提出 Availability
   - 委員會審核
   - 建立 Course Match
   - 確認後建立正式 Course / Course Session

2. **Open Enrollment**
   - 委員會建立 Course Offering
   - 確認價格
   - 公開招生
   - 學員報名 / 取消
   - 關閉招生
   - 人數判定
   - 確認成班後建立正式 Course / Course Session

兩條路徑成班後共用：

- Course Operations
- Enrollment / Cancellation
- Reschedule
- Venue Arrangement
- Receivable / Payment / Refund
- Settlement / Payout
- Notification / Outbox
- Audit

---

## 2. 功能範圍

## 2.1 MVP / P0 功能

### Identity / Member
- LINE LIFF Login。
- LINE credential 由 Backend 驗證。
- Backend 發行短效 Platform JWT。
- 使用者基本資料。
- 多角色。
- Organization scope。
- 個人課程、需求、應收、公開課程報名查詢。

### Coach Management
- 教練申請。
- 委員會人工審核教練資格。
- 教練 Profile。
- 教練可授課時段 Availability Proposal。
- Availability DRAFT / SUBMITTED / APPROVED / REJECTED / MATCHED 等狀態流程。
- 學員選擇已核准 Availability 後，第一筆成功有效需求建立 Availability Claim。
- 教練接受 / 拒絕媒合邀請。
- 教練取消授課必須走取消申請，不可直接取消正式 Session。

### Lesson Demand
- 學員建立 Lesson Request。
- 單次課程。
- 固定週期課程。
- 私人課 / 團體課。
- 指定教練 / 不指定教練。
- 可由教練公開 Availability 發起需求。
- Lesson Request 審核。
- 取消需求。

### Matching
- 委員會建立 Course Match。
- 邀請教練。
- 教練回覆。
- 時段、教練、學員、場地與人數 readiness 檢查。
- Pricing Preview。
- Pricing Confirmation。
- Match Confirmation。
- 確認後建立正式 Course / Session / Enrollment / Reservation / Receivable / Outbox / Audit。
- Match 決策不得被無歷程覆寫。

### Open Enrollment / Course Offering
- 委員會建立公開招生班別 DRAFT。
- 設定教練、堂次、場地、最低/最高人數、招生期間、billing mode。
- Pricing Preview。
- Pricing Confirmation。
- Publication：DRAFT → OPEN。
- 學員查看可報名課程。
- 學員報名。
- 學員成班前取消報名。
- 名額上限控制。
- Closure：OPEN → CLOSED。
- Confirmation：CLOSED → CONFIRMED。
- 低於最低人數不得成班。
- 高於最高人數阻止直接成班並交由委員會協調。
- 成班後轉為正式 Course downstream model。
- **不得為 Open Enrollment 建立假的 LessonRequest / CourseMatch。**

### Course / Session Operations
- Course 與 Course Session 分離。
- 固定週期課程每一堂 Session 獨立。
- 每一堂可獨立改期。
- 每一堂可獨立取消。
- 每一堂可更換場地。
- 每一堂可更換教練。
- Attendance。
- Contact transfer。
- 學員取消自己的 Enrollment。
- 學員取消不需委員會核准、不強制填理由，但要建立取消歷史。
- 教練取消需要委員會核准。
- STUDENT / COACH 可對未開始且自己參與的 Session 提出改期。
- COMMITTEE 可審核改期。
- COMMITTEE 可直接執行改期，但仍必須建立完整 change history / audit。
- Recurring Course 改期只改指定 Session，不連動其他 Session。

### Venue
MVP 的 Venue 是「場地協調與紀錄」，不是平台掌握的即時 Court Inventory。

支援：
- 學員提供場地。
- 教練提供場地。
- 委員會安排場地。
- 場地名稱 / 資訊快照。
- 場地費。
- 墊付款與返還。
- 異動歷史。
- 場地費變更後不可直接改已確認價格快照。

### Pricing
- Pricing Rule。
- Pricing Preview。
- Immutable Price Snapshot。
- 場地費、教練、人數等可影響價格。
- 已確認 Snapshot 不因新 Pricing Rule 回溯改價。
- 需要改價時建立新版本，舊版本 SUPERSEDED。

### Receivable / Payment
- 成班與付款完全分離。
- 正式 Course 可在 UNPAID 狀態成立。
- 人工收款。
- CASH。
- BANK_TRANSFER。
- 分次付款。
- Receivable 與 Payment 分離。
- Payment Allocation。
- 已完成付款不可直接覆寫金額；錯誤採 VOID / 新增正確 Payment。

### Refund
- 必須已有實際付款才可退款。
- FULL / PARTIAL refund。
- CASH / BANK_TRANSFER。
- 退款不得覆蓋原 Payment。
- 所有退款都必須由 COMMITTEE 核准。
- 只有 COMMITTEE 可執行退款。
- 核准與執行是兩個獨立動作。
- approved_by / approved_at 與 processed_by / refunded_at 分開保存。
- 核准人與執行人可為同一人，但紀錄不可合併。
- Refund amount 不得大於 refundable amount。

### Settlement / Payout
- 每堂 Session 結算。
- 課程應收與教練應付分離。
- 場地費與其他核准調整納入結算。
- 多教練可平均或由委員會調整。
- Settlement 與 Payout 分離。
- 個別發放。
- 批次發放。
- 防止重複發放。
- 歷史金額 immutable，錯誤透過 adjustment 修正。

### Notification / Audit
- Transactional Outbox。
- 業務交易與 Outbox row 同一 DB transaction。
- Worker 非同步發送 LINE。
- Retry。
- Dedupe。
- Notification log。
- 高風險操作寫 Audit Log。
- Audit 包含 actor、time、reason、before / after snapshot 或必要 context。

### Admin
- 使用者管理。
- 多角色指派。
- Organization scope。
- Audit 查詢。
- Outbox 查詢與 retry。
- 高風險營運操作。
- 委員會與平台管理員權限分離。

---

## 2.2 已列入產品但不屬於 MVP / P0 的功能

以下目前為 Future Extension / Phase 11，不應阻塞 MVP：

- 優惠券。
- 點數。
- 會員等級。
- 活動管理。
- 比賽。
- Open Play / 聚打。
- 深度數據分析 / BI Dashboard。
- 完整 Court Inventory。
- 即時球場空位。
- 自動球場預約。
- 線上金流 Provider。
- Payment callback。
- 自動付款逾時釋放。
- 自動匯款給教練。
- 教練證照檔案。
- Payment Proof Attachment。
- Receipt / Invoice。
- 多品牌 / SaaS Billing。
- Native Mobile App。
- Redis / Kafka / Kubernetes / Microservices。

---

## 3. 目前進度

| Phase | 狀態 | 說明 |
|---|---|---|
| 00 Project Definition | Done | 已完成 |
| 01 PRD | Done | MVP 需求基準已核准 |
| 02 Business Rules | Done | Design Baseline |
| 03 System Architecture | Done | 模組化單體架構基線 |
| 04 Database Design | Done | Review Gate PASS；Open Enrollment Backfill PASS |
| 05 Domain Model | Done | Review Gate PASS；Open Enrollment Backfill PASS |
| 06 API Specification | Done | Review Gate PASS；Open Enrollment Backfill PASS |
| 07 UI/UX Flow | Done | Review Gate PASS |
| 08 Test Strategy | Done | Review Gate PASS |
| 09 Deployment / DevOps | Done | Review Gate PASS |
| 10 Development Master Plan | Approved | Review Gate PASS |
| **10.1 Repository Bootstrap** | **NEXT** | **尚未實作，現在從此處開始** |
| 11 Future Extensions | Not Started | Coupon / Event / Analytics / Court Inventory 等 |

### 3.1 非常重要的進度判斷

「01～10 Approved Baseline」代表設計、規格、開發計畫已核准。

**它不代表 Backend、Frontend、DB migration 或 CI 已經寫完。**

目前正確的 Codex 起點：

```text
10.1 Repository Bootstrap
→ 建立實際 Git Repository
→ Backend Spring Boot skeleton
→ Maven Wrapper
→ Frontend npm workspaces
→ apps/liff
→ apps/admin
→ packages/*
→ PostgreSQL 18 local stack
→ Flyway baseline
→ Testcontainers
→ GitHub Actions CI skeleton
→ 確認 clone/build/test/local startup 可重現
```

---

## 4. 使用技術

## 4.1 Architecture
- Monorepo。
- Backend：Modular Monolith。
- Package-by-feature。
- Tactical DDD，但不過度設計。
- Vertical Slice delivery。
- Single PostgreSQL transactional source of truth。
- Transactional Outbox。
- REST JSON API。
- OpenAPI-first shared contract。
- Generated TypeScript API Client + handwritten adapter。

## 4.2 Backend
- Java 21。
- Spring Boot 4.1.x。
- Spring Web MVC。
- Spring Security。
- Spring Validation。
- Spring Data JPA / Hibernate。
- Flyway。
- PostgreSQL JDBC Driver。
- Spring Boot Actuator。
- OAuth2 Resource Server / JWT。
- JUnit 5。
- Spring Boot Test。
- Testcontainers PostgreSQL。
- ArchUnit。
- WireMock 或等價 HTTP stub。
- Maven Wrapper。

### Backend package baseline

```text
com.pickleball.booking
├─ shared/
│  ├─ error/
│  ├─ security/
│  ├─ persistence/
│  ├─ time/
│  └─ web/
├─ identity/
├─ organization/
├─ coach/
├─ lessonrequest/
├─ matching/
├─ offering/
├─ course/
├─ scheduling/
├─ venue/
├─ pricing/
├─ finance/
├─ settlement/
├─ notification/
└─ audit/
```

每個 feature 原則：

```text
<module>/
├─ api/
├─ application/
├─ domain/
└─ infrastructure/
```

依賴規則：
- `api` 不放業務規則。
- `application` 管理 use case orchestration、transaction、authorization scope、跨 Aggregate 協調。
- `domain` 管 invariant、state transition、domain service、value object。
- `infrastructure` 管 JPA、external adapter、worker。
- Domain 不依賴 Spring MVC / LINE SDK / Frontend model。
- 模組間不可任意跨模組直接操作對方 JPA Repository。
- 使用 ArchUnit 守 package dependency。

## 4.3 Database
- PostgreSQL 18。
- Local / Testcontainers / Staging / Production 同 Major Version。
- Flyway 是唯一正式 Schema 變更入口。
- Production 禁止 `hibernate.ddl-auto=update`。
- 必要 extension 由 Flyway 建立，例如 `btree_gist`。
- forward-only migration。
- expand-and-contract / forward-fix。
- 時間：`TIMESTAMPTZ`，資料庫用 UTC。
- UI：Asia/Taipei。
- 金額：`NUMERIC(12,2)`。
- 幣別：MVP TWD。
- PK：核心交易表使用 UUID。
- Audit Log 可用 bigint identity。
- 區間使用 `[start, end)`。

## 4.4 Frontend
- Node.js 24 LTS。
- React 19.2.x。
- TypeScript。
- Vite 8.1.x。
- npm workspaces。
- React Router。
- Server state 優先 TanStack Query 類型方案。
- Schema-based form validation。
- Vitest。
- React Testing Library。
- MSW。
- Playwright。

## 4.5 Infrastructure / DevOps
- Docker / Compose local stack。
- GitHub。
- GitHub Actions。
- GCP-first deployment direction。
- Cloud Run / Cloud SQL 為已核准部署基線方向。
- Secret 不進 repo。
- CI 必須至少涵蓋 backend build/test、frontend install/typecheck/lint/test/build、Flyway clean DB migration test。

---

## 5. Repository 目標結構

```text
pickleball-booking-platform/
├─ backend/
│  ├─ pom.xml
│  ├─ mvnw
│  ├─ mvnw.cmd
│  └─ src/
│     ├─ main/
│     │  ├─ java/com/pickleball/booking/
│     │  └─ resources/
│     │     ├─ application.yml
│     │     └─ db/migration/
│     └─ test/
├─ frontend/
│  ├─ package.json
│  ├─ package-lock.json
│  ├─ apps/
│  │  ├─ liff/
│  │  └─ admin/
│  └─ packages/
│     ├─ ui/
│     ├─ api-client/
│     ├─ shared/
│     └─ config/
├─ infra/
│  └─ terraform/
├─ docs/
│  ├─ adr/
│  └─ development/
├─ .github/
│  └─ workflows/
├─ compose.yaml
├─ .editorconfig
├─ .gitignore
├─ .env.example
├─ PROJECT_CONTEXT.md
└─ README.md
```

---

## 6. Domain Model

主要 Domain / Aggregate：

| Domain | Aggregate / 主要物件 |
|---|---|
| Identity / Organization | User, Organization, RoleAssignment, ExternalIdentity |
| Coach Supply | CoachProfile, CoachApplication, AvailabilityProposal |
| Lesson Demand | LessonRequest, AvailabilityClaim |
| Matching | CourseMatch |
| Open Enrollment | CourseOffering, CourseOfferingRegistration |
| Course | Course, CourseSession |
| Enrollment | CourseMembership, SessionEnrollment |
| Scheduling | ScheduleReservation |
| Venue | VenueArrangement, VenueAdvance |
| Pricing | PricingRule, PriceSnapshot |
| Finance | Receivable, Payment, Refund |
| Settlement | SessionSettlement, CoachSettlement, PayoutBatch |
| Notification | Notification, OutboxEvent |
| Audit | AuditLog |

### 6.1 重要 Domain 邊界

- Coach Availability 與 Lesson Request 是不同 Aggregate。
- Course Match 是獨立 Aggregate，不是 Course 的 status。
- Course 是整體方案；Course Session 是單堂。
- Payment 不決定 Course 是否成立。
- Refund 不修改 Payment。
- Settlement 不等於 Payout。
- Venue Arrangement 不等於 Court Booking。
- Schedule Reservation 保護「人」的時段，不代表 Court Inventory。
- Open Enrollment 不建立假的 LessonRequest / CourseMatch。
- DB Constraint 是最後一道一致性防線。

---

## 7. 資料庫結構

> 此區是 Codex 接手所需的資料模型摘要。完整 column-level migration 在實作時應依 04 Database Design 基線落成 Flyway。  
> Slice 0 先建立 migration framework；不要在 Repository Bootstrap 階段一次硬寫所有業務 migration。

## 7.1 共通欄位 / 型別原則

常見欄位：

```text
id                  uuid PK
organization_id     uuid FK
created_at          timestamptz
updated_at          timestamptz
version             bigint          // optimistic locking where needed
status              varchar(...)
amount              numeric(12,2)
currency            char(3)         // TWD
start_at/end_at     timestamptz
time_range          tstzrange       // where overlap constraint is needed
```

命名：
- Table：plural `snake_case`
- Column：`snake_case`
- PK：`id`
- FK：`{entity}_id`
- Index：`idx_{table}_{columns}`
- Unique：`uk_{table}_{columns}`
- Check：`ck_{table}_{rule}`
- Exclusion：`excl_{table}_{rule}`

## 7.2 Identity / Organization

### `organizations`
- PK: `id uuid`
- 核心：name/status。
- MVP seed 一筆 Organization。
- 一般後台不可刪除。

### `users`
- PK: `id uuid`
- 典型欄位：display_name、phone、email、status、timestamps。
- Email 不是主要登入唯一鍵。

### `user_external_identities`
- PK: `id uuid`
- FK: `user_id -> users.id`
- 典型欄位：provider、provider_subject。
- UNIQUE `(provider, provider_subject)`。
- 不保存 LINE access token / ID token。

### `user_role_assignments`
- PK: `id uuid`
- FK: user / organization。
- 同一 User 可多角色。
- 角色不是 users 單一欄位。

## 7.3 Coach Supply

### `coach_profiles`
- PK: `id uuid`
- FK: `organization_id`, `user_id`
- UNIQUE `(organization_id, user_id)`
- Index `(organization_id, approval_status)`
- 只有 APPROVED Coach 可正式媒合。

### `coach_applications`
- PK: `id uuid`
- FK: `coach_profile_id`
- 保存 submitted/review 歷史。
- Append-only reapplication。
- APPROVED/REJECTED 時必須有 reviewed_by / reviewed_at / review_note。

### `coach_availability_proposals`
- PK: `id uuid`
- FK: coach / organization。
- 狀態提案，不是正式 Reservation。
- 已被 Match / Claim 引用後不可硬刪。

### `coach_availability_claims`
- PK: `id uuid`
- FK: availability proposal / lesson request。
- 第一筆有效 Lesson Request 對已核准 Availability 的暫時占用。
- 不是 payment hold。
- 不是正式 Course Reservation。

## 7.4 Lesson Demand / Matching

### `lesson_requests`
- PK: `id uuid`
- FK: organization / requester。
- Index `(organization_id, status, created_at)`
- Index `(requester_user_id, status, created_at desc)`

### `lesson_request_session_preferences`
- PK: `id uuid`
- FK: `lesson_request_id`
- 支援 recurring 多堂 preferred time。
- `start_at < end_at`
- 建立時 Application 檢查 start > server now。

### `course_matches`
- PK: `id uuid`
- FK: organization / lesson_request。
- Index `(organization_id, status, created_at)`
- Index `(lesson_request_id, status)`
- CONFIRMED 後不可無歷程覆寫。

### `course_match_sessions`
- PK: `id uuid`
- FK: `course_match_id`
- 確認前保存一至多堂排程。
- `start_at < end_at`
- Index `(start_at, end_at)`

### `course_match_session_coaches`
- PK: `id uuid`
- FK: match session / coach。
- 支援多教練與每堂不同教練。

> Match invitation 可依 06 API / 04 schema 基線建立對應持久化模型；實作前確認 04 migration chapter 的正式 table name。

## 7.5 Formal Course / Enrollment / Scheduling

### `courses`
- PK: `id uuid`
- FK: organization。
- `source_match_id` nullable。
- `source_offering_id` nullable。
- Check：不可同時具有 source_match_id 與 source_offering_id。
- 兩者皆 null 時保留 Committee Direct Course 能力。
- PRIVATE max participant <= 4。
- GROUP min/max 必須合法。
- Index `(organization_id, status, created_at desc)`。

### `course_contact_assignments`
- Append-only。
- Partial UNIQUE `(course_id) WHERE effective_to IS NULL`。
- Contact transfer：關閉舊 row + 新增 row + Audit 同一 transaction。

### `course_memberships`
- Course 層級的已註冊實際參與者。
- 未註冊參與者不建立假 User。
- Index `(user_id, status)`。

### `course_sessions`
- 每一堂獨立 Session。
- recurring 每堂可獨立異動。
- 時間使用 `[start, end)`。

### `enrollments`
- Session 粒度。
- FK: session / user / membership。
- Index `(user_id, status, course_session_id)`
- Index `(course_session_id, status)`
- Student 取消自己的 enrollment 不直接取消 Session。

### `member_cancellation_records`
- 一次學員取消一筆。
- UNIQUE `(enrollment_id)`
- Index `(member_id, cancelled_at desc)`
- 取消次數使用 COUNT，不另存 counter。

### `session_coach_assignments`
- Session 教練配置。
- Partial UNIQUE：每 Session 最多一名 active primary coach。
- 教練取消不得直接 Session → CANCELLED。

### `course_approvals`
- 保存 Course Activation / Approval 的正式決策歷史。

### `coach_cancellation_requests`
- 教練取消申請。
- 委員會核准後才真正取消 Session。

### `session_change_requests`
- 改期 / 更換場地 / 更換教練 / 其他重要 Session 異動。
- APPROVED / REJECTED 保存 decided_by / decided_at / decision_reason。
- 重要欄位不可直接無歷程覆寫。

### `schedule_reservations`
- 保護已註冊學員與教練的重疊時間。
- PostgreSQL `tstzrange` + GiST Exclusion Constraint。
- 需要 `btree_gist`。
- Open Enrollment 成班前可指向 `course_offering_session_id`。
- 正式成班後轉接 `course_session_id`。
- 不代表 Court Inventory。

## 7.6 Venue / Pricing

### `venues`
- 場地資料。
- MVP 不建立同 Venue 同時間不可重疊的硬性 Court Inventory constraint。

### `session_venue_arrangements`
- PK: `id uuid`
- FK: session / venue。
- 每次異動新增快照。
- 每 Session 最多一筆 CONFIRMED。
- `cost_amount >= 0`

### `venue_advances`
- 場地墊付。
- `advanced_amount > 0`
- `returned_amount between 0 and advanced_amount`
- Index `(venue_arrangement_id, status)`

### `pricing_rules`
- PK: `id uuid`
- FK: organization / optional coach。
- 可使用少量 JSONB 保存 rule conditions / trace。
- Index `(organization_id, status, priority)`
- Index `(organization_id, coach_profile_id, status)`

### `session_price_snapshots`
- Confirmed 後 immutable。
- 新版本確認前舊版轉 SUPERSEDED。
- 不允許同時存在兩個有效 confirmed snapshot。

### `session_price_snapshot_items`
- 價格拆解 line items。
- 保存教練費、場地費、調整等計算來源。

## 7.7 Receivable / Payment / Refund

### `receivables`
- 應收 Aggregate。
- 成班可先產生 Receivable，但不要求先 Payment。

### `receivable_items`
- 可按 Session / Enrollment 追蹤。
- FULL_COURSE 也應能分攤到各 Session。
- amount/paid/refunded >= 0。

### `receivable_adjustments`
- Append-only。
- 禁止直接覆寫應收總額不留歷史。

### `payments`
- 每次實際付款一筆。
- FK: payer / organization 等。
- `amount numeric(12,2)`
- CASH / BANK_TRANSFER。
- Index `(payer_user_id, paid_at desc)`
- 已完成付款不得直接修改 amount。

### `payment_allocations`
- Payment ↔ Receivable Item 多對多配置。
- allocations sum <= payment.amount。
- 跨 row 合計使用 transaction + row lock。

### `refunds`
- 獨立於 Payment。
- PENDING_APPROVAL → APPROVED / REJECTED → COMPLETED / FAILED。
- 支援 FULL / PARTIAL。
- 累計退款 <= refundable amount。
- approval / execution 分開保存。

## 7.8 Settlement / Payout

### `session_settlements`
- 每堂目前一個 Settlement Aggregate。
- UNIQUE `course_session_id`。
- `distributable = gross_receivable - venue_cost + other_adjustment`。
- 確認後不可直接覆寫。

### `settlement_adjustments`
- Append-only 修正。

### `coach_settlements`
- 教練應付。
- `payable >= 0`
- `paid between 0 and payable`
- 多教練 payable 合計需等於 distributable。
- 未收足可處於 WAITING_RECEIPT。

### `payout_batches`
- 個別 / 批次發放的 batch。
- Index `(organization_id, status, payout_date)`。
- total_amount >= 0。

### `payout_batch_items`
- Batch 與 coach settlement 關聯。
- 防止重複 payout。

## 7.9 Notification / Audit / Idempotency

### `notification_targets`
- 固定 LINE 群組等通知目標。
- UNIQUE `(organization_id, channel, target_code)`。
- LINE groupId 不寫一般 application log。

### `notifications`
- 發送狀態、payload、target、retry context。

### `outbox_events`
- 業務事件。
- Index `(status, available_at, created_at)`
- Index `(aggregate_type, aggregate_id, created_at)`
- Partial UNIQUE `dedupe_key` when non-null。

### `audit_logs`
- `id bigint generated always as identity` PK。
- 高風險操作不可變歷史。
- actor / action / target / reason / before / after / timestamp / trace context。

### `api_idempotency_keys`
- 支援重要 POST Command 防重複。
- key scope 至少包含 actor + operation + idempotency key + request hash。
- 相同 key + 相同 request：重播第一次成功結果。
- 相同 key + 不同 request：409 `IDEMPOTENCY_CONFLICT`。

## 7.10 Open Enrollment Tables

### `course_offerings`
- PK: `id uuid`
- FK: organization / coach。
- DRAFT / OPEN / CLOSED / CONFIRMED / CANCELLED。
- `minimum_participants > 0`
- `maximum_participants >= minimum_participants`
- `registration_close_at > registration_open_at`
- MVP lesson type 為 GROUP。
- Index `(organization_id, status, registration_close_at)`
- OPEN listing 可用 partial index。

### `course_offering_sessions`
- PK: `id uuid`
- FK: `course_offering_id`
- `sequence_no smallint`
- `start_at/end_at timestamptz`
- venue snapshot。
- Index `(course_offering_id, sequence_no)`
- Index time columns。

### `course_offering_price_snapshots`
- PK: `id uuid`
- FK: offering。
- DRAFT / CONFIRMED / SUPERSEDED。
- `price_per_participant numeric(12,2)`
- `currency char(3)`
- `rule_trace jsonb`
- billing mode 決定 price per participant 的語意。

### `course_offering_registrations`
- PK: `id uuid`
- FK: offering / user。
- ACTIVE / CANCELLED / CONVERTED。
- Partial UNIQUE：同一 Offering 同一 User 不可有兩筆 ACTIVE。
- Index offering/status。
- Index user/status。
- `registered_count` 不保存 counter，以 ACTIVE registration COUNT 為真實值。

---

## 8. API

## 8.1 共通規範

```text
Base Path: /api/v1
Content-Type: application/json
ID: UUID string
Time: RFC3339; backend UTC; UI Asia/Taipei
Money JSON: decimal string, e.g. "1200.00"
Currency: TWD
```

Authorization：

```http
Authorization: Bearer <platformAccessToken>
```

Trace：

```http
X-Request-Id: <UUID>
```

重要 POST Command：

```http
Idempotency-Key: <client-generated-UUID>
```

### 成功 Response

```json
{
  "data": {},
  "meta": {
    "requestId": "uuid"
  }
}
```

列表：

```json
{
  "data": [],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "requestId": "uuid"
  }
}
```

### Error Response

```json
{
  "error": {
    "code": "SCHEDULE_CONFLICT",
    "message": "指定時段與既有安排衝突",
    "fieldErrors": [],
    "details": {},
    "traceId": "uuid"
  }
}
```

### HTTP Status

- 400：JSON / validation。
- 401：未登入或 token 無效。
- 403：角色 / organization scope 不符。
- 404：資源不存在或依安全策略不可見。
- 409：狀態、時段、併發、idempotency 衝突。
- 422：合法 request，但 business rule 不允許。
- 429：Rate limit。
- 500：未預期錯誤；不得回 stack trace。

### 核心 Error Code

```text
AUTH_INVALID_TOKEN
AUTH_FORBIDDEN
ORG_SCOPE_DENIED
RESOURCE_NOT_FOUND
VALIDATION_FAILED
STATE_TRANSITION_INVALID
BOOKING_TIME_NOT_FUTURE
SCHEDULE_CONFLICT
COACH_NOT_APPROVED
AVAILABILITY_ALREADY_CLAIMED
AVAILABILITY_NOT_MODIFIABLE
PARTICIPANT_BELOW_MIN
PARTICIPANT_ABOVE_MAX
MATCH_NOT_READY
PAYMENT_AMOUNT_INVALID
REFUND_EXCEEDS_REFUNDABLE
REFUND_NOT_APPROVED
REFUND_ALREADY_PROCESSED
SETTLEMENT_NOT_READY
PAYOUT_ALREADY_PROCESSED
IDEMPOTENCY_CONFLICT
CONCURRENT_MODIFICATION
OFFERING_NOT_OPEN
OFFERING_REGISTRATION_CLOSED
OFFERING_CAPACITY_FULL
OFFERING_ALREADY_REGISTERED
OFFERING_NOT_READY
```

## 8.2 Endpoint Catalog

### Auth / Me

```text
POST  /api/v1/auth/line/login
GET   /api/v1/me
PATCH /api/v1/me/profile
GET   /api/v1/me/roles
GET   /api/v1/me/courses
GET   /api/v1/me/lesson-requests
GET   /api/v1/me/receivables
```

### Coach

```text
POST  /api/v1/coach-applications
GET   /api/v1/coach-applications/{applicationId}
POST  /api/v1/coach-applications/{applicationId}/review

GET   /api/v1/coaches
GET   /api/v1/coaches/{coachId}

POST  /api/v1/coach-availability-proposals
GET   /api/v1/coach-availability-proposals/{proposalId}
PATCH /api/v1/coach-availability-proposals/{proposalId}
POST  /api/v1/coach-availability-proposals/{proposalId}/submission
POST  /api/v1/coach-availability-proposals/{proposalId}/review
POST  /api/v1/coach-availability-proposals/{proposalId}/closure
```

### Lesson Request

```text
POST  /api/v1/lesson-requests
GET   /api/v1/lesson-requests/{lessonRequestId}
PATCH /api/v1/lesson-requests/{lessonRequestId}
POST  /api/v1/lesson-requests/{lessonRequestId}/submission
POST  /api/v1/lesson-requests/{lessonRequestId}/review
POST  /api/v1/lesson-requests/{lessonRequestId}/cancellation
```

### Matching

```text
POST  /api/v1/course-matches
GET   /api/v1/course-matches/{courseMatchId}
PATCH /api/v1/course-matches/{courseMatchId}

POST  /api/v1/course-match-invitations/{invitationId}/response
POST  /api/v1/course-matches/{courseMatchId}/pricing-preview
POST  /api/v1/course-matches/{courseMatchId}/pricing-confirmation
POST  /api/v1/course-matches/{courseMatchId}/confirmation
POST  /api/v1/course-matches/{courseMatchId}/cancellation
```

### Course / Session

```text
GET   /api/v1/courses
GET   /api/v1/courses/{courseId}
GET   /api/v1/courses/{courseId}/sessions
GET   /api/v1/course-sessions/{sessionId}

PUT   /api/v1/course-sessions/{sessionId}/venue-arrangement

POST  /api/v1/course-sessions/{sessionId}/change-requests
POST  /api/v1/session-change-requests/{requestId}/review
POST  /api/v1/course-sessions/{sessionId}/reschedule

PUT   /api/v1/course-sessions/{sessionId}/attendance
POST  /api/v1/session-enrollments/{enrollmentId}/cancellation

POST  /api/v1/course-sessions/{sessionId}/coach-cancellation-requests
POST  /api/v1/coach-cancellation-requests/{requestId}/review

POST  /api/v1/courses/{courseId}/contact-transfer
```

### Receivable / Payment / Refund

```text
GET   /api/v1/receivables/{receivableId}
GET   /api/v1/receivables/{receivableId}/payments
POST  /api/v1/receivables/{receivableId}/payments

POST  /api/v1/receivables/{receivableId}/refunds
GET   /api/v1/refunds/{refundId}
POST  /api/v1/refunds/{refundId}/review
POST  /api/v1/refunds/{refundId}/execution
```

### Settlement / Payout

```text
GET   /api/v1/course-sessions/{sessionId}/settlement
POST  /api/v1/course-sessions/{sessionId}/settlement-calculation
POST  /api/v1/session-settlements/{settlementId}/confirmation

GET   /api/v1/me/coach-settlements

POST  /api/v1/payout-batches
GET   /api/v1/payout-batches/{batchId}
POST  /api/v1/payout-batches/{batchId}/execution
```

### Admin / Operations

```text
GET    /api/v1/admin/users
POST   /api/v1/admin/users/{userId}/role-assignments
DELETE /api/v1/admin/users/{userId}/role-assignments/{roleAssignmentId}

GET    /api/v1/admin/audit-logs
GET    /api/v1/admin/outbox-events
POST   /api/v1/admin/outbox-events/{eventId}/retry
```

### Open Enrollment

```text
GET   /api/v1/course-offerings
GET   /api/v1/course-offerings/{offeringId}

POST  /api/v1/course-offerings
PATCH /api/v1/course-offerings/{offeringId}

POST  /api/v1/course-offerings/{offeringId}/pricing-preview
POST  /api/v1/course-offerings/{offeringId}/pricing-confirmation
POST  /api/v1/course-offerings/{offeringId}/publication
POST  /api/v1/course-offerings/{offeringId}/closure
POST  /api/v1/course-offerings/{offeringId}/confirmation
POST  /api/v1/course-offerings/{offeringId}/cancellation

GET   /api/v1/course-offerings/{offeringId}/registrations
POST  /api/v1/course-offerings/{offeringId}/registrations
POST  /api/v1/course-offering-registrations/{registrationId}/cancellation
GET   /api/v1/me/course-offering-registrations
```

---

## 9. 權限

角色：

```text
STUDENT
COACH
COMMITTEE
PLATFORM_ADMIN
```

同一 User 可同時擁有多個角色。

授權模型：

```text
RBAC + Organization Scope (ABAC-like resource scope)
```

### STUDENT
可：
- 管理自己的 Lesson Request。
- 查看符合資格的 Open Offering。
- 建立 / 取消自己的 Offering Registration。
- 查看自己的 Course。
- 取消自己的 Enrollment。
- 對自己參與且尚未開始的 Session 提出改期。
- 查看自己的 Receivable / Payment。
- 更新自己的 Profile。

不可：
- 審核自己或他人需求。
- 直接修改 status。
- 操作他人 enrollment。
- 核准退款 / 執行退款。
- 跨 organization 查資料。

### COACH
可：
- 建立 Coach Application。
- 管理自己的 Availability。
- 回覆自己的媒合邀請。
- 查看自己的授課 Session。
- 對自己授課且尚未開始的 Session 提出改期。
- 提出自己的授課取消申請。
- 查看自己的 Settlement / Payout。

不可：
- 直接取消正式 Session。
- 未經核准成為正式 Coach。
- 跨 organization 管理資料。
- 執行委員會財務操作。

### COMMITTEE
同 organization 內可：
- 審核教練。
- 審核 Availability。
- 審核 Lesson Request。
- 建立與確認 Match。
- 建立 / 編輯 DRAFT Offering。
- Offering pricing / publication / closure / confirmation / cancellation。
- 查看公開招生名單。
- 安排場地。
- 審核 change request。
- 直接改期，但必須保留 change request / audit。
- 教練取消審核。
- 記錄收款。
- 建立退款。
- 核准退款。
- 執行退款。
- Settlement。
- Payout。
- 其他高風險營運操作。

### PLATFORM_ADMIN
可：
- User / Role / Organization 系統管理。
- 跨組織異常處理。
- Audit / Outbox operational access。
- 在明確授權下執行跨組織管理操作。

### 重要安全規則
- Frontend route guard 只是 UX，不是安全邊界。
- Controller annotation 只做第一層 role check。
- Application Service 必須再次驗證：
  - organizationId
  - resource owner
  - course membership
  - coach ownership
- 不得信任前端傳入 organizationId 決定 scope。
- Scope 必須由 authenticated principal + resource relation 推導。
- Server-owned status 不允許普通 PATCH。
- 所有高風險 command 使用明確 endpoint。
- Backend 驗證「是否仍在未來」，不信任裝置時間。
- Sensitive token / LINE groupId 不寫一般 log。
- 重要 write 支援 idempotency。
- Finance / approval / reschedule / payout 都需 Audit。

---

## 10. 頁面結構

## 10.1 Frontend Apps

```text
frontend/apps/liff
frontend/apps/admin
frontend/packages/ui
frontend/packages/api-client
frontend/packages/shared
frontend/packages/config
```

### `apps/liff`
使用角色：
- STUDENT
- COACH
- COMMITTEE

特性：
- Mobile-first。
- LINE LIFF bootstrap。
- 短流程。
- Stepper / cards。
- 快速查詢 / 提交 / 審核 / 報名。

### `apps/admin`
使用角色：
- COMMITTEE
- PLATFORM_ADMIN

特性：
- Desktop-first。
- Table + Filter + Detail Drawer + Action Panel。
- 財務。
- 批次營運。
- Audit。
- Outbox。
- 系統管理。

## 10.2 學員端 IA

P0：
- LINE Login。
- Home / 我的。
- 建立課程需求。
- 找教練空堂。
- 公開課程 / 開放報名。
- Offering Detail。
- 我的公開課程報名。
- 我的需求。
- Lesson Request Detail。
- 我的課程。
- Course / Session Detail。
- 改期申請。
- Enrollment 取消。
- 應收與付款。
- 個人資料。

P1：
- 完整通知中心。

## 10.3 教練端 IA

P0：
- 我的空堂。
- Availability Create / Edit。
- Availability Submit。
- 待回覆邀請。
- Invitation Detail。
- 我的課程。
- Session Detail。
- 改期申請。
- 取消授課申請。
- 教練資料 / 審核狀態。

P1：
- 我的結算。
- Desktop coach workspace。

## 10.4 委員會 LIFF

P0：
- Dashboard / 待辦。
- 快速審核。
- 快速查詢。
- 必要即時操作。
- Open Enrollment 狀態 / 報名人數快速查看。
- 高風險操作二次確認。

不應把大型表格與複雜批次財務塞進 LIFF。

## 10.5 委員會 Admin

P0：
- Dashboard。
- Coach Review。
- Lesson Request Review。
- Matching Workspace。
- Course Offering / Open Enrollment Management。
- Registration List。
- Course / Session Management。
- Venue Arrangement。
- Reschedule Review。
- Coach Cancellation Review。
- Receivable / Payment。
- Refund Approval / Execution。
- Settlement。
- Payout。
- Operational Notifications。

## 10.6 Platform Admin

- User Management。
- Role Assignment。
- Organization。
- Audit Log。
- Outbox Events。
- Retry / operational recovery。
- Cross-org exception handling。

---

## 11. 已核准的重要決策

Codex 不應自行推翻以下 baseline；若實作發現缺口，使用 forward-fix 並同步更新 source-of-truth 文件。

1. Backend 使用 Java 21。
2. Backend 使用 Spring Boot 4.1.x。
3. Database 使用 PostgreSQL 18。
4. Repository 使用 Monorepo。
5. Backend 使用 Modular Monolith，不採 Microservices。
6. Backend package-by-feature。
7. Frontend 使用 React 19.2.x + TypeScript + Vite 8.1.x。
8. Frontend 使用 npm workspaces。
9. Frontend 有 `apps/liff` 與 `apps/admin`。
10. API 使用 REST JSON，Base Path `/api/v1`。
11. API 使用 Resource-centric + Command Subresource。
12. 不允許普通 PATCH 直接修改 server-owned status。
13. LINE LIFF credential 由 Backend 驗證。
14. Backend 發行短效 JWT；MVP 不導入 refresh token session。
15. RBAC + Organization scope。
16. Organization scope 不能只依前端 organizationId。
17. Course 與 Course Session 分離。
18. Coach Availability 與 Lesson Request 分離。
19. Course Match 是獨立 Aggregate。
20. Open Enrollment 是 MVP / P0 正式功能。
21. Open Enrollment 保留在 Slice 4。
22. Slice 1～3 仍建置共用基礎與另一條成班路徑；Open Enrollment 不是唯一課程流程。
23. Open Enrollment 不建立假的 LessonRequest / CourseMatch。
24. GROUP 低於最低人數不得成班。
25. 高於最高人數不得直接成班，需委員會協調。
26. Payment 不是成班前置條件。
27. 不建立 Payment Hold / payment countdown。
28. 學員開始前可取消自己的 enrollment，不需委員會核准。
29. 學員取消理由不必填，但一定建立取消紀錄。
30. 教練取消必須委員會核准。
31. Refund 支援 partial refund。
32. Refund approval 與 execution 分離。
33. 只有委員會可核准及執行退款。
34. Pricing Rule + immutable Price Snapshot。
35. Schedule Reservation 使用 PostgreSQL range + GiST Exclusion。
36. Venue 在 MVP 不是 Court Inventory。
37. Notification 使用 Transactional Outbox。
38. 高風險操作必須 Audit。
39. Flyway 是 schema 唯一正式入口。
40. Production 禁用 ddl-auto update。
41. Migration forward-only / forward-fix。
42. Vertical Slice，不採 layer-first big bang。
43. API Contract 以 Backend OpenAPI 為來源，產生 TypeScript client。
44. Generated API client 外再包 handwritten adapter。
45. Branch strategy：`main` + short-lived feature branches + PR。
46. 不建立長期 `develop` branch。
47. Test / Security / DB / API / UI 必須跟同一 Slice 一起完成。
48. Outbox / Audit infrastructure 從早期 Slice 就要存在，不等 Slice 8 才補。
49. Future 功能不得阻塞 P0 商業閉環。
50. 如果 implementation 發現 baseline gap，採 forward-fix 回補 04～10，不重寫整套規格。

---

## 12. Development Roadmap

### Slice 0 — Repository Bootstrap / Engineering Foundation
**SEALED**

目標：
- clone 後可 build。
- clone 後可 test。
- clone 後可啟動 local stack。
- CI skeleton 綠燈。
- Flyway 可在 clean PostgreSQL 執行。
- Backend / Frontend skeleton 可部署。

### Slice 1 — Identity / LINE Login / Me / RBAC + Organization Scope
目標：
- LIFF login。
- Platform JWT。
- `/me`。
- Roles。
- Organization context。
- Route guard + backend authorization。

必測：
- invalid LINE credential。
- disabled user。
- multiple roles。
- org scope。
- token expiration。

### Slice 2 — Coach Supply + Lesson Demand
目標：
- Coach Application。
- Availability。
- Lesson Request。
- submit / review。

必測：
- DRAFT → SUBMITTED。
- illegal transition。
- coach approval。
- availability edit rules。
- owner / org security。
- idempotent LIFF submit。

### Slice 3 — Matching → Confirmed Course
目標：
- Match。
- Coach invitation。
- pricing。
- confirmation。
- 建正式 Course / Session / Enrollment / Reservation / Receivable / Outbox / Audit。

必測：
- readiness。
- schedule conflict。
- recurring sessions。
- price snapshot。
- concurrent confirmation。
- idempotency replay。

### Slice 4 — Open Enrollment
目標：
- 委員會開班。
- pricing。
- publication。
- registration / cancellation。
- closure。
- participant count。
- confirmation。

必測：
- 未定價不可發布。
- 非 OPEN 不可報名。
- capacity full。
- below minimum。
- above maximum。
- confirmed downstream model。
- 不建立 fake demand/match。

### Slice 5 — Course Operations
目標：
- Session。
- Venue。
- Student cancellation。
- Coach cancellation request。
- Reschedule。
- Recurring single-session changes。

必測：
- `[start,end)`。
- conflict。
- only selected recurring session changed。
- cancellation history。
- committee direct reschedule still audited。

### Slice 6 — Receivable / Payment / Refund
目標：
- artificial/manual receivable。
- split payment。
- refund creation。
- approval。
- execution。

必測：
- payment independent of course confirmation。
- partial payment。
- partial refund。
- refundable cap。
- duplicate execution。
- approval / execution actor trail。

### Slice 7 — Settlement / Payout
目標：
- coach payable。
- settlement confirmation。
- individual / batch payout。
- anti-duplicate payout。

### Slice 8 — Notification / Outbox / Operational Hardening
目標：
- LINE notifications。
- outbox retry / dedupe。
- admin ops。
- monitoring。
- failure recovery。

注意：
- Outbox / Audit 不是到 Slice 8 才開始做。
- 前面 Slice 需要時就必須落地。

### Slice 9 — MVP Product Acceptance / Release-Candidate Readiness
**IN PROGRESS — explicitly approved 2026-08-30**

目標：
- 將 S1～S8 的代表性 backend、browser 與 production-like evidence 固定為可稽核 Closure Gate。
- 補齊 Slice 3 matching / pricing / course confirmation 的 browser acceptance journey。
- 在 ephemeral production-like runtime 驗證 S1～S8 protected capability 均維持 unauthenticated `401`。
- 不新增產品功能、API、schema、role、migration 或 Future Extension。

驗收、相容性與 recovery 規則以 `docs/reference/slice9-product-acceptance.md` 為準。

---

## 13. Codex 執行規則

Codex 接手後請遵守：

1. **不要先重做 PRD、DB、API 或 UI 設計。**
2. **Slices 0–7 and S8.1–S8.4 已封版；開始任何新產品 Slice 前，先依最新 Git history、PR、main CI 與 Closure Gate 確認 maintainer 已核准範圍與驗收條件。**
3. 不要一次生成整個產品所有 Entity / Controller / UI。
4. 每次只做一個可完整驗收的 Vertical Slice。
5. 每個 Slice 必須同時考慮：
   - DB migration
   - Domain
   - Application
   - API
   - Security
   - UI
   - Unit / Integration / API / E2E Test
   - Idempotency
   - Audit
   - Outbox
   - OpenAPI
   - CI
6. 不要讓 JPA Entity 取代 Domain Model。
7. 不要把商業規則放 Controller。
8. 不要讓前端複製後端 business rule 作為唯一判定。
9. UI action 必須對應 API command。
10. 所有時間判斷以 Backend / Database time 為準。
11. 需要跨 row aggregate sum 時使用 transaction + lock / DB constraint。
12. 任何新增 dependency 先說明用途與替代方案。
13. 任何 schema 變更都新增 Flyway migration。
14. Production 不使用 destructive down migration。
15. 如果規格與實作衝突，先確認 02 / 04 / 05 / 06 的 source-of-truth，再 forward-fix。
16. Codex model / reasoning policy 以 repository-root `AGENTS.md` 為唯一完整規範；本文件只保留摘要，不另行複製完整 escalation matrix。

---

## 14. Source of Truth 優先序

實作時若文件有衝突，按以下原則判斷：

1. **最新已核准 Business Rules**
2. **04 Database Design**
3. **05 Domain Model**
4. **06 API Specification**
5. **07 UI/UX Flow**
6. **08 Test Strategy**
7. **09 Deployment / DevOps**
8. **10 Development**
9. 01 PRD 作產品範圍基準
10. 更早的舊架構 / MVP 草稿只作歷史參考

尤其：
- Open Enrollment Backfill 已正式進入 04 / 05 / 06 baseline。
- 舊的「球場即時預約為核心」方向已被新版設計取代。
- 舊的 Payment Hold、24 小時取消門檻、低於 minimum 可例外自動成班等假設不可復活。

---

## 15. 10.1 Repository Bootstrap Definition of Done

Codex 第一階段完成條件：

- [ ] 建立 `pickleball-booking-platform` Git repository。
- [ ] 建立目標 monorepo 目錄。
- [ ] `backend/` 可透過 Maven Wrapper build/test。
- [ ] Java 21 baseline。
- [ ] Spring Boot 4.1.x baseline。
- [ ] 建立 backend package boundary。
- [ ] 建立 `frontend/` npm workspace。
- [ ] `apps/liff` 可 build。
- [ ] `apps/admin` 可 build。
- [ ] `packages/ui`。
- [ ] `packages/api-client`。
- [ ] `packages/shared`。
- [ ] `packages/config`。
- [ ] Node.js 24 LTS baseline。
- [ ] React 19.2.x。
- [ ] Vite 8.1.x。
- [ ] PostgreSQL 18 Compose service。
- [ ] Flyway baseline migration 可在空 DB 執行。
- [ ] Testcontainers PostgreSQL smoke test。
- [ ] `.env.example`，無 secret。
- [ ] `.editorconfig`。
- [ ] `.gitignore`。
- [ ] Backend health endpoint / Actuator baseline。
- [ ] Backend test skeleton。
- [ ] Frontend test skeleton。
- [ ] GitHub Actions CI skeleton。
- [ ] CI 執行 backend build/test。
- [ ] CI 執行 frontend install/typecheck/lint/test/build。
- [ ] CI 執行 clean DB migration test。
- [ ] README 本機啟動說明正確。
- [ ] clone 後可以重現 build / test / local startup。
- [ ] 完成後再進 Slice 1。

---

## 16. 不要現在做的事

Repository Bootstrap 階段先不要：

- 一次建立所有 40+ tables 的完整 migration。
- 一次建立所有 JPA entities。
- 一次建立所有 APIs。
- 一次建立完整 UI。
- 先做 Coupon / Event / Analytics。
- 先做 Court Inventory。
- 先串線上 payment provider。
- 引入微服務。
- 引入 Kafka。
- 引入 Redis。
- 引入 Kubernetes。
- 自行改 branch strategy。
- 自行改掉 Open Enrollment Slice 4。
- 自行將 Maven Wrapper 改為依賴本機 Maven。
- 把 GitHub CLI 當成 build 的必需品。

---

## 17. 給 Codex 的目前接續指令建議

```text
Read AGENTS.md, PROJECT_CONTEXT.md, README.md, the latest Git history, merged/open PRs, and main CI first.

Slices 0–7 and S8.1–S8.4 are SEALED. Do not redo or broadly refactor them.
Slice 9 MVP Product Acceptance / Release-Candidate Readiness is explicitly approved and in progress.
Implement only the acceptance scope in docs/reference/slice9-product-acceptance.md. Do not add a Future Extension or deploy.

Before starting any product Slice after Slice 9, require the maintainer to explicitly approve:
- problem statement and P0 scope
- non-goals and compatibility boundary
- API / schema / security / UI impact
- migration and forward-fix strategy
- automated test matrix
- production-like validation and Closure Gate

At the end, report files changed, commands and tests executed, GitHub CI Run / HEAD SHA, unresolved prerequisites, risks, and the next legal step.
```
