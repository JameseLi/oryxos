package com.oryxos.storage.repository;

import java.util.List;

import com.oryxos.storage.entity.ToolInvocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolInvocationRepository extends JpaRepository<ToolInvocationEntity, String> {

    List<ToolInvocationEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
