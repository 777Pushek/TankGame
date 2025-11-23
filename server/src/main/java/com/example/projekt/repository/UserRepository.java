package com.example.projekt.repository;

import com.example.projekt.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Integer> {
	Optional<User> findByUsername(String username);

	@Query("select u from User u order by (u.wins - u.loses) desc, u.wins desc, u.username asc")
	List<User> findAllOrderByScore(Pageable pageable);
}
