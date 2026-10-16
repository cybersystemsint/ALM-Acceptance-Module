package com.zain.almksazain.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Read-only mirror of tb_Access_Role, used only to resolve a user's roleName
 * for the Admin/SuperAdmin bypass on UPL approvals. The frontend's own
 * Admin/SuperAdmin check (SignInPage.js) is a case-insensitive substring match
 * on roleName ("admin"/"superadmin") — there's no dedicated roleId convention
 * or boolean flag, so this mirrors that same substring logic server-side
 * rather than inventing a new authorization scheme.
 */
@Entity
@Table(name = "tb_Access_Role")
public class AccessRole {

    @Id
    private Integer roleId;

    private String roleName;

    private Integer status;

    public Integer getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public Integer getStatus() {
        return status;
    }
}
