package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
}
