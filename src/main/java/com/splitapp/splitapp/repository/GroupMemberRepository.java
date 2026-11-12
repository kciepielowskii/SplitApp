package com.splitapp.splitapp.repository;

import com.splitapp.splitapp.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
}
