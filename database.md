1. users.json

QMId
password
role（TA / MO / ADMIN）
name
email

2. profiles.json
这是 TA 个人资料表。

profileId
QMId
name
phone
email
skills(option)
cvFilePath
updatedAt

3. modules.json
这个是 岗位/招聘信息主表

moduleId
moduleCode
moduleName
description(指的是工作内容描述而不是课程的描述)
workload
requirements
vacanciesTotal
vacanciesFilled
moUserId
status（OPEN / CLOSED / FINISHED）
createdAt
updatedAt

4. applications.json
表示 TA 对某个岗位的一次申请。

applicationId
taUserId
moduleId
appliedRoleName（Lab TA / Grader / Teaching Assistant 等）
status（SUBMITTED / ACCEPTED / REJECTED / WAITING_FOR_ASSIGNMENT / REASSIGNED）
allowAdjustment（是否接受调剂）
cvSnapshotPath 或 cvFilePath
moDecisionBy
decisionTime
createdAt
updatedAt

5. reassign_logs.json（ v2 再加）
专门记录 Admin 调剂/拒绝操作。

logId
applicationId
fromModuleId
toModuleId
actionType（REASSIGN / FINAL_REJECT）
adminUserId
reason
createdAt

Java 里可以直接对应成：

User
TAProfile
ModulePosting
RecruitmentApplication   // 源码类名；对应概念仍是 Application，避免与桌面程序混淆
ReassignLog              // optional

JSON 字段名与 Java 属性一致时使用 camelCase（如 qmId、moUserId），便于 Gson 映射。