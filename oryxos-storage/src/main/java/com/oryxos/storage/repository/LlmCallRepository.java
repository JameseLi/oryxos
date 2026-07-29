package com.oryxos.storage.repository;

import java.util.List;

import com.oryxos.storage.entity.LlmCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCallRepository extends JpaRepository<LlmCallEntity, String> {

    List<LlmCallEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
