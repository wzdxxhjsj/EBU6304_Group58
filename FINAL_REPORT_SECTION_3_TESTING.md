# Final Report — Section 3: Testing（真实测试记录）

> **项目：** EBU6304 Group 58 — JavaFX TA Recruitment System  
> **记录日期：** 2026-05-16  
> **测试执行环境：** Windows，项目根目录 `EBU6304_Group58`  
> **说明：** 下文数据来自 **2026-05-16 实际运行** `scripts\test.bat`，非虚构。

---

## 一、测试运行结果

### 运行命令

**推荐（项目标准方式，先编译再测）：**

```bat
cd <项目根目录>
scripts\compile.bat
scripts\test.bat
```

`scripts\test.bat` 内部等价于：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test.ps1
```

`test.ps1` 会：

1. 编译 `test\java\` 下全部测试类到 `out-test\`
2. 使用 JUnit Platform Console Standalone 执行扫描

**等价的 JUnit Console Launcher 命令（需已执行 `scripts\compile.bat`）：**

```bat
java -jar lib\junit-platform-console-standalone-1.10.2.jar execute --class-path "out;out-test;lib\gson-2.10.1.jar" --scan-class-path
```

**依赖：**

- `lib\junit-platform-console-standalone-1.10.2.jar`（JUnit 5.10.2 Console）
- `lib\gson-2.10.1.jar`
- 主代码已编译到 `out\`（`scripts\compile.bat`）

### 测试框架

| 组件 | 版本/说明 |
|------|-----------|
| JUnit Jupiter | 5.x（随 `junit-platform-console-standalone-1.10.2.jar`） |
| 断言 | `org.junit.jupiter.api.Assertions` |
| 临时目录隔离 | `@TempDir`（JUnit Jupiter） |
| 测试发现 | `--scan-class-path`（扫描 `out` + `out-test`） |

### 测试结果摘要（2026-05-16 实际运行）

| 指标 | 数值 |
|------|------|
| **测试总数（tests found）** | **78** |
| **通过（tests successful）** | **78** |
| **失败（tests failed）** | **0** |
| **跳过（tests skipped）** | **0** |
| **中止（tests aborted）** | **0** |
| **测试容器（containers）** | 32（全部 successful） |
| **测试运行时间** | **约 1.12 秒**（控制台：`Test run finished after 1116 ms`） |
| **退出码** | **0**（成功） |

### 失败测试列表

**无。** 本次运行 **0 failed，0 skipped**。

### 结果截图建议（Report Evidence）

| 优先级 | 建议截图内容 | 原因 |
|--------|----------------|------|
| **首选** | 终端运行 `scripts\test.bat` 后 **末尾汇总行**（含 `78 tests successful`、`0 tests failed`、`1116 ms`） | 证明真实执行、可复现、含数量与时间 |
| **备选** | IDE（IntelliJ / VS Code）Test Explorer **全部绿色** 的测试树（展开显示 7 个测试类） | 评委熟悉 IDE，直观 |
| **可选** | JUnit Console 彩色树状输出 **中间一段 + 末尾汇总**（不必截全 78 条） | 展示覆盖的 Nested 结构 |
| **不建议** | 黑底代码截图、单个 `@Test` 方法源码 | 报告要求避免；信息密度低 |
| **不建议** | 仅截 `compile.bat` 成功 | 不能证明测试通过 |

**推荐截图文案（Figure caption 示例）：**

> Figure X: Automated test execution via `scripts\test.bat` (JUnit 5). 78/78 tests passed in ~1.1s.

---

## 二、测试类覆盖范围

> **说明：** `TAServiceRoleBTestSupport` 为测试工具类（写临时 JSON），**不是**测试类，无 `@Test`。

| Test class | Test type | Target class/service | Main scenarios covered |
|------------|-----------|----------------------|-------------------------|
| `AuthServiceLoginTest` | Unit / integration（临时 `users.json`） | `AuthService` | 登录成功；错误密码；角色不匹配；用户不存在；登出；`hasRole` 会话 |
| `TAServiceStatusLabelsTest` | Unit（无磁盘 I/O） | `TAService`（静态方法） | 申请状态 UI 文案；`countsAsAcceptedForTa`（最多 3 个录用计数规则） |
| `TAServiceRoleBIntegrationTest` | Integration（`@TempDir` + JSON） | `TAService` | 提交申请成功/失败；重复申请；最多 4 个申请；无 profile/CV；模块关闭/满员；Dashboard 筛选；申请历史；CV 路径；调剂意愿 |
| `TAServiceRoleCIntegrationTest` | Integration（`@TempDir` + JSON） | `TAService` | 加载/创建 profile；保存 profile 校验（姓名/电话/邮箱/技能）；可无 CV 保存；调剂标志读写 |
| `MOServiceRoleDIntegrationTest` | Integration（`@TempDir` + JSON） | `MOService` | MO 只看自己的模块；申请人列表排序与 profile 字段；接受申请并更新名额/满员 FINISHED；拒绝（调剂开/关）；待审数量 |
| `MO2FunctionTest` | Integration（`@TempDir` + JSON） | `MOService` | 创建模块（校验、ID 冲突）；更新模块（权限、满员自动 FINISHED、名额封顶）；关闭模块（权限、状态） |
| `AdminServiceIntegrationTest` | Integration（`@TempDir` + JSON） | `AdminService` | 调剂成功并更新名额；多种非法调剂；终审拒绝；是否存在 MO 未审的 SUBMITTED |

**测试类数量：** 7 个（78 个 `@Test` 方法）

---

## 三、测试策略总结

### Unit testing（单元测试）

- **`TAServiceStatusLabelsTest`**：纯内存，不读写 `data/`，验证 `displayLabelForStatus` 与 `countsAsAcceptedForTa`。
- **`AuthServiceLoginTest`**：虽写入临时 `users.json`，但只测 `AuthService` 单类逻辑，可视为 **轻量单元/组件测试**。

### Integration testing（集成测试）

- **`TAServiceRoleBIntegrationTest`、`TAServiceRoleCIntegrationTest`、`MOServiceRoleDIntegrationTest`、`MO2FunctionTest`、`AdminServiceIntegrationTest`**：
  - 通过 `System.setProperty("user.dir", tempRoot)` 将 `AppPaths.dataDirectory()` 指向 **临时目录**；
  - 使用 `TAServiceRoleBTestSupport` 写入 `users.json`、`modules.json`、`applications.json`、`profiles.json` 等；
  - 调用真实 **Service + Repository + Gson**，验证持久化与业务规则联动。

### Regression testing（回归测试体现）

- 同一套测试在每次修改 Service 规则后可重复运行（`scripts\test.bat`）；
- 覆盖 TA 申请上限、重复申请、MO 满员、Admin 调剂边界等 **易回归缺陷点**；
- **未**使用独立 CI 流水线或历史 baseline 对比，回归依赖 **本地/手动重复执行全部 78 条**。

### Manual / system testing（需报告补充说明）

| 范围 | 自动化情况 | 建议报告表述 |
|------|------------|----------------|
| JavaFX UI（Login、TA/MO/Admin 界面） | **未**自动化 | 登录、导航、卡片布局、弹窗等 **手工系统测试** |
| AI Recruitment Insight（远程 API） | **未**自动化 | 依赖 `config.properties` 与网络，**手工验证** |
| CV 文件内容解析 / PDF 解析 | **未**测试 | 仅测 CV **路径** 与申请绑定 |
| 多用户并发 / 性能 | **未**测试 | 单机 JSON 文件存储 |

### 测试数据隔离

- 广泛使用 **`@TempDir Path root`** + `useProjectRoot(root)`；
- 每个测试方法在 **独立临时目录** 下生成 `data/*.json`，不污染仓库内 `data/`；
- **注意：** 测试说明中注明不宜 **并行** 运行依赖 `user.dir` 的测试 JVM（见 `TAServiceRoleBIntegrationTest` 类注释）。

### JSON repository / persistence

- **是。** 集成测试通过 `*Repository` 读写 Gson 序列化的 JSON，验证申请状态、模块名额、`vacanciesFilled` 等 **落盘结果**。

---

## 四、测试技术（附真实用例）

| 技术 | 是否使用 | 真实测试例子 |
|------|----------|----------------|
| **Black-box testing** | 是 | `AuthServiceLoginTest.loginFailsWrongPassword`：仅根据 QM ID/密码/角色输入，断言 `Optional.empty()`，不依赖内部实现细节。 |
| **White-box testing** | 是 | `MO2FunctionTest.autoSetsFinishedWhenFullAndStatusOpen`：针对 `updateModule` 满员后状态变为 `FINISHED` 的分支；`TAServiceStatusLabelsTest` 直接测静态辅助方法。 |
| **Equivalence partitioning** | 是 | 登录：有效凭证 / 错误密码 / 错误角色 / 不存在用户（`AuthServiceLoginTest` 四个分区）；申请状态：`SUBMITTED` vs `ACCEPTED` vs `WAITING_FOR_ASSIGNMENT`（`MOServiceRoleDIntegrationTest.rejectApplication`）。 |
| **Boundary testing** | 是 | `TAServiceRoleBIntegrationTest.maxFourApplications`：第 5 个申请被拒绝；`MOServiceRoleDIntegrationTest.rejectsWhenModuleAlreadyFull`：名额已满仍接受；`MO2FunctionTest.fails_whenVacanciesNotPositive`：名额 ≤ 0。 |
| **Error handling testing** | 是 | `TAServiceRoleBIntegrationTest.rejectsWhenNoProfile`、`rejectsWhenNoCv`；`MO2FunctionTest.fails_whenMoUserIdMismatch`；`AdminServiceIntegrationTest.rejectsWhenNotWaitingForAssignment`。 |
| **State transition testing** | 是 | `MOServiceRoleDIntegrationTest.successAcceptsAndUpdatesVacancyAndFinishesWhenFull`：`SUBMITTED → ACCEPTED`，名额满后模块 `FINISHED`；`allowAdjustmentTrueTurnsIntoWaitingForAssignment`：`SUBMITTED → WAITING_FOR_ASSIGNMENT`；`AdminServiceIntegrationTest.successMovesApplicationAndIncrementsVacancy`：`WAITING_FOR_ASSIGNMENT → REASSIGNED`。 |
| **Regression testing** | 部分 | 全量 78 测试一键重跑；无专用回归套件命名，但 Role B/C/D、Admin、MO2 用例防止核心规则回退。 |

---

## 五、测试用例矩阵（Report 推荐 12 条）

| Test ID | Function | Test input / operation | Expected result | Actual result | Status | Related test class |
|---------|----------|------------------------|-----------------|---------------|--------|-------------------|
| AUTH-01 | Login | Valid QM ID + password + matching `Role.TA` in temp `users.json` | `Optional` contains user; session set | Login succeeds | **Pass** | `AuthServiceLoginTest.loginSuccess` |
| AUTH-02 | Login | Correct ID, wrong password | Login rejected | `Optional.empty()` | **Pass** | `AuthServiceLoginTest.loginFailsWrongPassword` |
| AUTH-03 | Login | Correct credentials, wrong selected role | Login rejected | `Optional.empty()` | **Pass** | `AuthServiceLoginTest.loginFailsWrongRole` |
| AUTH-04 | Login | Unknown QM ID | Login rejected | `Optional.empty()` | **Pass** | `AuthServiceLoginTest.loginFailsNonexistentId` |
| TA-01 | Submit application | Valid profile + CV file on disk + OPEN module | `ApplyResult.success`; row in `applications.json` | 1 application saved | **Pass** | `TAServiceRoleBIntegrationTest.successWritesRow` |
| TA-02 | Submit application | Second application to same module | Failure: already applied | Message contains "already applied" | **Pass** | `TAServiceRoleBIntegrationTest.duplicateModuleRejected` |
| TA-03 | Apply prerequisite | No profile / no CV | Failure before submit | Profile/CV error messages | **Pass** | `rejectsWhenNoProfile`, `rejectsWhenNoCv` |
| TA-04 | Application cap | 4 existing applications, apply to 5th module | Failure: max 4 | Message contains "Maximum 4" | **Pass** | `TAServiceRoleBIntegrationTest.maxFourApplications` |
| MO-01 | Accept application | SUBMITTED app, module OPEN with vacancy | ACCEPTED; `vacanciesFilled++`; full → FINISHED | Success + vacancy updated | **Pass** | `MOServiceRoleDIntegrationTest.successAcceptsAndUpdatesVacancyAndFinishesWhenFull` |
| MO-02 | Reject application | `allowAdjustment=true` vs `false` | WAITING_FOR_ASSIGNMENT vs REJECTED | States updated as expected | **Pass** | `allowAdjustmentTrueTurnsIntoWaitingForAssignment`, `allowAdjustmentFalseTurnsIntoRejected` |
| ADM-01 | Reassign | WAITING_FOR_ASSIGNMENT + target OPEN with vacancy | REASSIGNED; target vacancy +1 | Success | **Pass** | `AdminServiceIntegrationTest.successMovesApplicationAndIncrementsVacancy` |
| ADM-02 | Reassign guard | No vacancy / wrong status / TA not allowing adjustment | `ActionResult.failure` with reason | Rejected as expected | **Pass** | `rejectsWhenTargetModuleHasNoVacancy`, `rejectsWhenNotWaitingForAssignment`, `rejectsWhenTaDoesNotAllowAdjustment` |

*（补充：状态文案 — `TAServiceStatusLabelsTest.reassignedMatchesHistoryUi` 断言 UI 显示 "Reassigned"；MO 模块更新 — `MO2FunctionTest.success_updatesEditableFields`。）*

---

## 六、测试结果分析（Report-ready English Summary）

Automated testing for the TA Recruitment System relies on **JUnit 5** and a single entry point, `scripts\test.bat`, which compiles test sources and executes **78** tests in approximately **1.1 seconds**. On **16 May 2026**, all **78** tests **passed** with **zero** failures or skips.

The suite combines **fast unit tests** (e.g. status display labels and acceptance-cap rules) with **integration tests** that exercise services against **isolated temporary JSON data** under `@TempDir`, covering authentication, TA browsing and application submission (including duplicate and four-application limits, profile/CV prerequisites), MO applicant review and module vacancy updates, MO module lifecycle (create/update/close), and Admin reassignment and final rejection workflows. This provides evidence that core recruitment business rules and persistence interactions behave as specified without manual data setup.

**Limitations (stated honestly):** JavaFX screens are **not** covered by automated UI tests and were validated manually. Remote **AI insight** calls, **CV document parsing**, static analysis tools, and concurrent access to JSON files are **out of scope** of the current automated suite. Passwords in demo `users.json` remain plaintext by design for local demonstration.

---

## 七、截图建议（汇总）

1. **必放一张：** PowerShell/CMD 中 `scripts\test.bat` 输出 **最后 15–20 行**（含 `78 tests successful`、`0 tests failed`、`Test run finished after … ms`）。
2. **可放一张：** IDE 中 7 个测试类全部通过的 Test Runner 面板（不必展开每一个方法）。
3. **不要放：** 源码截图、黑底 IDE 代码、仅编译成功的截图。

---

## 附录 A：78 个测试方法清单（按测试类）

### AuthServiceLoginTest（6）

- `loginSuccess`
- `loginFailsWrongPassword`
- `loginFailsWrongRole`
- `loginFailsNonexistentId`
- `logoutClearsSession`
- `hasRoleReturnsFalseAfterLogout`

### TAServiceStatusLabelsTest（9）

- `submitted`, `accepted`, `reassignedMatchesHistoryUi`, `waitingForAssignment`, `nullFallsBackToSubmitted`
- `acceptedCounts`, `reassignedCounts`, `submittedDoesNotCount`, `waitingDoesNotCount`

### TAServiceRoleBIntegrationTest（22）

- **submitApplication:** `successWritesRow`, `rejectsWhenNoProfile`, `rejectsWhenNoCv`, `duplicateModuleRejected`, `maxFourApplications`, `moduleNotFound`, `finishedModuleClosed`, `fullVacanciesClosed`
- **getDashboardData:** `excludesAlreadyAppliedModules`, `keywordFiltersByCodeOrName`, `workloadFilter`, `acceptedAndReassignedCountTowardCap`
- **getWorkloadOptions:** `startsWithAllWorkloadAndSortsByNumericPart`
- **getCvFilePath:** `readsPathFromProfile`
- **isTaWillingToAcceptAdjustment:** `defaultsTrueWhenNoProfile`, `readsStoredProfile`
- **listMyApplications:** `emptyWhenNoRows`, `blankTaUserIdReturnsEmpty`, `mapsModuleLabelsAndStatusText`, `sortedByCreatedAtDescending`

### TAServiceRoleCIntegrationTest（11）

- **loadOrCreateProfile:** `createsDefaultProfileWhenNoneExists`, `returnsExistingProfileWhenPresent`
- **saveProfile validation:** `successWhenAllFieldsValid`, `savesProfileWithoutCvPath`, `rejectsEmptyName`, `rejectsNonDigitPhone`, `rejectsInvalidEmailFormat`, `rejectsWhenNoSkills`
- **isTaWillingToAcceptAdjustment:** `defaultsTrueWhenNoProfile`, `readsStoredFlagFromProfile`

### MOServiceRoleDIntegrationTest（10）

- **getMyModules:** `returnsOnlyOwnModulesAndOpenFirst`
- **getApplicantsForModule:** `submittedFirstThenNewestFirstAndContainsProfileFields`, `fallsBackToUserNameWhenProfileNameBlank`
- **acceptApplication:** `successAcceptsAndUpdatesVacancyAndFinishesWhenFull`, `rejectsWhenApplicationNotSubmitted`, `rejectsWhenModuleAlreadyFull`
- **rejectApplication:** `allowAdjustmentTrueTurnsIntoWaitingForAssignment`, `allowAdjustmentFalseTurnsIntoRejected`
- **countPendingForModule:** `countsOnlySubmitted`

### MO2FunctionTest（15）

- **createModule:** `success_withValidModule`, `fails_whenModuleCodeMissing`, `fails_whenModuleNameMissing`, `fails_whenVacanciesNotPositive`, `generatesUniqueModuleId_whenCodeCollision`
- **updateModule:** `success_updatesEditableFields`, `fails_whenModuleNotFound`, `fails_whenMoUserIdMismatch`, `autoSetsFinishedWhenFullAndStatusOpen`, `capsVacanciesFilledWhenExceedsTotal`
- **closeModule:** `success_closesOpenModule`, `fails_whenModuleNotFound`, `fails_whenNotAuthorized`, `fails_whenAlreadyClosed`

### AdminServiceIntegrationTest（10）

- **reassignApplication:** `successMovesApplicationAndIncrementsVacancy`, `rejectsWhenTaDoesNotAllowAdjustment`, `rejectsWhenNotWaitingForAssignment`, `rejectsWhenTaWasRejectedOnTargetModule`, `rejectsWhenTaAlreadyHasAnotherApplicationForTargetModule`, `rejectsWhenTargetModuleHasNoVacancy`
- **finalRejectApplication:** `successSetsRejected`, `rejectsWhenNotWaiting`
- **hasUnreviewedApplications:** `trueWhenAnySubmitted`, `falseWhenNoSubmitted`

---

## 附录 B：复现步骤（给答辩/审稿人）

```bat
cd C:\Users\tangyuan\Documents\GitHub\EBU6304_Group58
scripts\compile.bat
scripts\test.bat
echo Exit code: %ERRORLEVEL%
```

预期：`ERRORLEVEL` 为 `0`，控制台末尾显示 **78 tests successful**, **0 tests failed**。
