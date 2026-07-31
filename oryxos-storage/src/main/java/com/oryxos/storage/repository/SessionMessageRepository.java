package com.oryxos.storage.repository;

import com.oryxos.storage.entity.SessionMessageEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionMessageRepository extends JpaRepository<SessionMessageEntity, String> {

  List<SessionMessageEntity> findBySessionIdOrderBySeqAsc(String sessionId);

  Optional<SessionMessageEntity> findTopBySessionIdOrderBySeqDesc(String sessionId);
}
