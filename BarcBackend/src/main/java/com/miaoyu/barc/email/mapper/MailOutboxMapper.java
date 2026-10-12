package com.miaoyu.barc.email.mapper;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.*;

/** 持久邮件队列；业务事件去重与租约领取均由数据库条件写入保证。 */
public interface MailOutboxMapper {
    @Insert("INSERT IGNORE INTO mail_outbox(id,event_key,recipient,subject,body,expires_at) VALUES(#{id},#{eventKey},#{recipient},#{subject},#{body},CASE WHEN #{minutes} IS NULL THEN NULL ELSE DATE_ADD(NOW(), INTERVAL #{minutes} MINUTE) END)")
    int enqueue(@Param("id") String id, @Param("eventKey") String eventKey, @Param("recipient") String recipient,
                @Param("subject") String subject, @Param("body") String body, @Param("minutes") Integer validityMinutes);

    @Select("SELECT * FROM mail_outbox WHERE sent_at IS NULL AND attempts < 5 AND next_attempt_at <= NOW() AND (expires_at IS NULL OR expires_at > NOW()) AND (lease_until IS NULL OR lease_until < NOW()) ORDER BY created_at LIMIT 20")
    List<Mail> pending();

    @Update("UPDATE mail_outbox SET lease_token=#{lease},lease_until=DATE_ADD(NOW(),INTERVAL 2 MINUTE),attempts=attempts+1 WHERE id=#{id} AND sent_at IS NULL AND attempts<5 AND next_attempt_at<=NOW() AND (expires_at IS NULL OR expires_at>NOW()) AND (lease_until IS NULL OR lease_until<NOW())")
    int claim(@Param("id") String id, @Param("lease") String lease);

    @Update("UPDATE mail_outbox SET sent_at=NOW(),lease_until=NULL,lease_token=NULL,body='' WHERE id=#{id} AND lease_token=#{lease}")
    int sent(@Param("id") String id, @Param("lease") String lease);

    @Update("UPDATE mail_outbox SET lease_until=NULL,lease_token=NULL,next_attempt_at=DATE_ADD(NOW(),INTERVAL 1 MINUTE) WHERE id=#{id} AND lease_token=#{lease}")
    int retry(@Param("id") String id, @Param("lease") String lease);

    /** 字段名保持数据库命名，避免依赖全局下划线映射开关。 */
    @Data class Mail {
        private String id;
        private String recipient;
        private String subject;
        private String body;
    }
}
