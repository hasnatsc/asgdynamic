package com.asg.fabricerp.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Every query names the signed-in user as one of the two people. */
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * One row per person this user has exchanged messages with: {@code [otherUserId, lastMessageId,
     * unreadFromThem]}, most recent conversation first.
     */
    @Query("""
           select case when m.senderUserId = :userId then m.recipientUserId else m.senderUserId end,
                  max(m.id),
                  sum(case when m.recipientUserId = :userId and m.readAt is null then 1 else 0 end)
           from Message m
           where m.senderUserId = :userId or m.recipientUserId = :userId
           group by case when m.senderUserId = :userId then m.recipientUserId else m.senderUserId end
           order by max(m.id) desc
           """)
    List<Object[]> conversations(@Param("userId") Long userId, Pageable pageable);

    /** The thread between two people, newest first - the client reverses a page to show it. */
    @Query("""
           select m from Message m
           where (m.senderUserId = :userId and m.recipientUserId = :otherId)
              or (m.senderUserId = :otherId and m.recipientUserId = :userId)
           """)
    Page<Message> thread(@Param("userId") Long userId, @Param("otherId") Long otherId, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(Long userId);

    List<Message> findTop5ByRecipientUserIdAndReadAtIsNullAndIdGreaterThanOrderByIdDesc(Long userId, Long afterId);

    @Query("select coalesce(max(m.id), 0) from Message m where m.recipientUserId = :userId")
    long latestId(@Param("userId") Long userId);

    /** Opening a thread reads everything the other person sent in it. */
    @Modifying
    @Query("""
           update Message m set m.readAt = :at
           where m.recipientUserId = :userId and m.senderUserId = :otherId and m.readAt is null
           """)
    int markThreadRead(@Param("userId") Long userId, @Param("otherId") Long otherId, @Param("at") LocalDateTime at);
}
