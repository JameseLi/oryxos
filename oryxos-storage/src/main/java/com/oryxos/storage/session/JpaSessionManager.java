package com.oryxos.storage.session;

import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import com.oryxos.storage.entity.SessionEntity;
import com.oryxos.storage.repository.SessionRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@link SessionManager} 的 JPA 实现（第 18 节）：{@code sessions} 表存取 + {@code messages_json} 编解码。
 *
 * <p><b>session_id 拼接公式只存在于此一处</b>（课件「三」：所有入口只提供三元组，两处各拼一遍、格式差一个分隔符， 同一个人就会出现两条互不相认的历史）。 {@code
 * getOrCreate} 查有则携历史恢复（进程重启后同一三元组拿回老会话）、查无则新建（首次 save 落库）。
 *
 * <p>与审计链同构 DIP：接口立在 core（{@code SessionManager}），实现在 storage（本类）。
 */
@Component
public class JpaSessionManager implements SessionManager {

  private final SessionRepository repository;
  private final MessageJsonCodec codec = new MessageJsonCodec();

  public JpaSessionManager(SessionRepository repository) {
    this.repository = repository;
  }

  @Override
  public Session getOrCreate(String channel, String user, String profileName) {
    String sessionId = sessionId(channel, user, profileName);
    return repository
        .findById(sessionId)
        .map(this::toDomain)
        .orElseGet(() -> new Session(sessionId, profileName, channel, user));
  }

  @Override
  public Optional<Session> get(String sessionId) {
    return repository.findById(sessionId).map(this::toDomain);
  }

  @Override
  public void save(Session session) {
    // 查有则只更新历史与活跃时间（channel/user 等身份列不被抹掉）；查无则整行插入。
    // 第 17 节时代的会话（channel/user 为 null）走插入路径，身份列存 null，表列可空。
    SessionEntity entity = repository.findById(session.id()).orElseGet(() -> newEntity(session));
    entity.setMessagesJson(codec.serialize(session.messages()));
    entity.setLastActiveAt(Instant.now().toString());
    repository.save(entity);
  }

  /** session_id 唯一拼接点：channel + user + profile 三元组以 ":" 联接（内部约定，入口侧不感知）。 */
  private static String sessionId(String channel, String user, String profileName) {
    return String.join(":", channel, user, profileName);
  }

  private Session toDomain(SessionEntity entity) {
    return Session.restore(
        entity.getSessionId(),
        entity.getProfileName(),
        entity.getChannel(),
        entity.getUserId(),
        codec.deserialize(entity.getMessagesJson()));
  }

  private static SessionEntity newEntity(Session session) {
    SessionEntity entity = new SessionEntity();
    entity.setSessionId(session.id());
    entity.setProfileName(session.profileName());
    entity.setChannel(session.channel());
    entity.setUserId(session.userId());
    entity.setStatus("active");
    return entity;
  }
}
