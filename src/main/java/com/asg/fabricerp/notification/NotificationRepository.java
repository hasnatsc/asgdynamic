package com.asg.fabricerp.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Every query names the recipient: a notification is only ever its recipient's. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** {@code kinds} is never empty: pass every kind with {@code allKinds} on to not filter. */
    @Query("""
           select n from Notification n
           where n.recipientUserId = :userId
             and (:unreadOnly = false or n.readAt is null)
             and (:allKinds = true or n.kind in :kinds)
             and (:q is null or lower(n.title) like lower(concat('%', cast(:q as string), '%'))
                  or lower(coalesce(n.body, '')) like lower(concat('%', cast(:q as string), '%')))
           """)
    Page<Notification> search(@Param("userId") Long userId, @Param("unreadOnly") boolean unreadOnly,
                              @Param("allKinds") boolean allKinds, @Param("kinds") Collection<NotificationKind> kinds,
                              @Param("q") String q, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(Long userId);

    /** The newest unread ones after {@code afterId}, for the "new since you last looked" toast. */
    List<Notification> findTop5ByRecipientUserIdAndReadAtIsNullAndIdGreaterThanOrderByIdDesc(Long userId, Long afterId);

    @Query("select coalesce(max(n.id), 0) from Notification n where n.recipientUserId = :userId")
    long latestId(@Param("userId") Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :at where n.recipientUserId = :userId and n.id in :ids and n.readAt is null")
    int markRead(@Param("userId") Long userId, @Param("ids") Collection<Long> ids, @Param("at") LocalDateTime at);

    @Modifying
    @Query("update Notification n set n.readAt = null where n.recipientUserId = :userId and n.id in :ids")
    int markUnread(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    @Modifying
    @Query("update Notification n set n.readAt = :at where n.recipientUserId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("at") LocalDateTime at);

    @Modifying
    @Query("delete from Notification n where n.recipientUserId = :userId and n.id in :ids")
    int deleteOwn(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    @Modifying
    @Query("delete from Notification n where n.recipientUserId = :userId and n.readAt is not null")
    int deleteAllRead(@Param("userId") Long userId);
}
