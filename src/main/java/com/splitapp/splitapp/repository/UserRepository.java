package com.splitapp.splitapp.repository;

import com.splitapp.splitapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    @Query("select u.id as id, u.email as email, u.password as password from User u where u.email = :email")
    Optional<LoginView> findLoginByEmail(@Param("email") String email);
}
