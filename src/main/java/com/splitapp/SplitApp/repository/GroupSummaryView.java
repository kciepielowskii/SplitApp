package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.GroupRole;

public interface GroupSummaryView {
    Long getId();

    String getGroupName();

    String getGroupDescription();

    GroupRole getRole();
}
