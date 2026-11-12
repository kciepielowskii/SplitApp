package com.splitapp.splitapp.repository;

import com.splitapp.splitapp.entity.GroupRole;

public interface GroupSummaryView {
    Long getId();

    String getGroupName();

    String getGroupDescription();

    GroupRole getRole();
}
