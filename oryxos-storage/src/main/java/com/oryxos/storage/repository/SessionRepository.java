package com.oryxos.storage.repository;

import com.oryxos.storage.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code sessions} 表仓储（第 18 节）。主键 {@code session_id} 为应用层拼接的三元组字符串，故泛型主键类型是 {@link String} 而非自增
 * Long。
 */
public interface SessionRepository extends JpaRepository<SessionEntity, String> {}
