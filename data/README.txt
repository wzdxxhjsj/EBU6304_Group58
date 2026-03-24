JSON files (see database.md in repo root)
========================================

Field names in JSON use camelCase to match Java/Gson (e.g. qmId, moUserId).

users.json
  Accounts: qmId, password, role (TA|MO|ADMIN), name, email.

profiles.json
  TA profiles only: profileId, qmId, name, phone, email, skills[], cvFilePath, allowAdjustment, updatedAt.

modules.json
  Module postings: moduleId, moduleCode, moduleName, description, workload, requirements,
  vacanciesTotal, vacanciesFilled, moUserId, status (OPEN|CLOSED|FINISHED), createdAt, updatedAt.

applications.json
  TA applications: applicationId, taUserId, moduleId, appliedRoleName, status,
  cvSnapshotPath, cvFilePath, moDecisionBy, decisionTime, createdAt, updatedAt.

reassign_logs.json
  Admin audit (v2): logId, applicationId, fromModuleId, toModuleId, actionType, adminUserId, reason, createdAt.

cvs/
  Binary CV files; paths referenced from profiles / applications.
