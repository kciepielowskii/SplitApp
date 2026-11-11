package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
}
