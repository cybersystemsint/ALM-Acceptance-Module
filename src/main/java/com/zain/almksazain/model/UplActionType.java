package com.zain.almksazain.model;

public enum UplActionType {
    UPDATE,
    DELETE,
    // Matches the value tb_UPL_Change_Request.changeType's ENUM already reserved for this
    // (sql/upl_approval_workflow_schema.sql) - a brand new UPL line, routed through the same
    // change-request/approval workflow as edits/deletes instead of being inserted immediately.
    CREATE
}
