package com.zain.almksazain.services;

import java.util.Map;

import com.zain.almksazain.model.UplActionType;

/** One line item within a create-change-request call — a single row's edit or delete. */
public class UplChangeRequestItem {
    private Long uplRecordNo;
    private UplActionType changeType;
    /** Only present for UPDATE: proposed new values, keyed by the 7 whitelisted field names. */
    private Map<String, Object> fields;

    public Long getUplRecordNo() {
        return uplRecordNo;
    }

    public void setUplRecordNo(Long uplRecordNo) {
        this.uplRecordNo = uplRecordNo;
    }

    public UplActionType getChangeType() {
        return changeType;
    }

    public void setChangeType(UplActionType changeType) {
        this.changeType = changeType;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }
}
