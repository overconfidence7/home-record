-- N004: tasks are private on pull (Crew and Sub get only their own; a Lead also every task of their jobs).
-- Every member a task has been assigned to is recorded here, so when it is handed to someone else the former
-- assignee's next pull carries a stub (id, job, new assignee) that takes it off their phone, and nobody else learns
-- the task exists.
CREATE TABLE task_seen (
  tenant_id TEXT NOT NULL,
  task_id TEXT NOT NULL,
  member_id TEXT NOT NULL,
  PRIMARY KEY (tenant_id, member_id, task_id)
);
