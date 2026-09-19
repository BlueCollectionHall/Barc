package com.miaoyu.barc.user.mapper;

import com.miaoyu.barc.user.model.OfficialAccountModel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 官方业务指定账号的绑定关系数据访问层。
 *
 * 注意（换绑迁移为什么放在这里而不是 WorkMapper）：
 * 本模块位于 {@code user} 包，而 {@code api.work} 包已经依赖 {@code user} 包
 * （WorkService / WorkManageService 都注入了 user 下的 Mapper）。
 * 若把换绑迁移 SQL 放进 WorkMapper，就会形成 {@code user ↔ api.work} 的包级双向依赖。
 * 因此把「按旧绑定 UUID 迁移未认领作品」这条 SQL 收在本模块自己的 Mapper 里，
 * 保证依赖方向始终是 {@code api.work → user} 单向。
 */
@Mapper
public interface OfficialAccountMapper {

    /**
     * 全部绑定记录（原始行，不含账号信息）。
     */
    @Select("SELECT business_key, user_uuid, remark, updated_by, created_at, updated_at FROM official_account")
    List<OfficialAccountModel> selectAll();

    /**
     * 管理端列表用：绑定记录 + 绑定账号信息 + 最后操作管理员昵称。
     * 全部 LEFT JOIN：绑定行存在但账号已被删除时账号字段为 null，由 dangling 标记。
     */
    @Select("SELECT oa.business_key, oa.user_uuid, oa.remark, oa.updated_by, oa.created_at, oa.updated_at, " +
            "ua.nickname, ub.username, ua.avatar, " +
            "CASE WHEN ua.uuid IS NULL THEN 1 ELSE 0 END AS dangling, " +
            "operator.nickname AS updated_by_nickname " +
            "FROM official_account oa " +
            "LEFT JOIN user_archive ua ON ua.uuid = oa.user_uuid " +
            "LEFT JOIN user_basic ub ON ub.uuid = oa.user_uuid " +
            "LEFT JOIN user_archive operator ON operator.uuid = oa.updated_by")
    List<OfficialAccountModel> selectAllWithAccount();

    /**
     * 读取某业务被指定的官方账号 UUID。
     *
     * JOIN user_archive 是刻意的：绑定行存在但账号已被删除（悬空绑定）时返回 null，
     * 在调用方语义中等同「未配置」，从而保证 work.author 永远不会写入悬空 UUID。
     */
    @Select("SELECT oa.user_uuid FROM official_account oa " +
            "JOIN user_archive ua ON ua.uuid = oa.user_uuid " +
            "WHERE oa.business_key = #{business_key}")
    String selectBoundUuid(@Param("business_key") String businessKey);

    /**
     * 某业务当前的绑定记录（可能是悬空绑定）。
     */
    @Select("SELECT business_key, user_uuid, remark, updated_by, created_at, updated_at " +
            "FROM official_account WHERE business_key = #{business_key}")
    OfficialAccountModel selectByBusinessKey(@Param("business_key") String businessKey);

    /**
     * 该账号是否已被「其它」业务绑定（exclude_business_key 用于更换场景排除自身），
     * 与唯一索引一起构成 D2「一个账号只能绑一个业务」的双重保证：
     * 这里先查一次是为了给出可读提示，而不是把 SQL 约束异常抛给前端。
     */
    @Select("<script>" +
            "SELECT COUNT(1) FROM official_account WHERE user_uuid = #{user_uuid} " +
            "<if test='exclude_business_key != null'> AND business_key != #{exclude_business_key} </if>" +
            "</script>")
    int countByUserUuid(@Param("user_uuid") String userUuid,
                        @Param("exclude_business_key") String excludeBusinessKey);

    /**
     * 写入绑定（首次绑定或更换）。
     */
    @Insert("INSERT INTO official_account(business_key, user_uuid, remark, updated_by) " +
            "VALUES(#{business_key}, #{user_uuid}, #{remark}, #{updated_by}) " +
            "ON DUPLICATE KEY UPDATE user_uuid = #{user_uuid}, remark = #{remark}, " +
            "updated_by = #{updated_by}, updated_at = CURRENT_TIMESTAMP")
    int upsert(OfficialAccountModel model);

    /**
     * 换绑迁移：把该业务此前的未认领作品归属迁到新账号。
     *
     * 双条件是刻意的：
     * 1、is_claim = 0 —— 已认领作品的 author 是真实认领人，任何情况下都不能改动；
     * 2、author = 旧绑定 UUID —— 精确锁定「本业务此前写入的作品」，不会误伤其它来源的未认领数据
     *    （历史上存在 is_claim=0 但 author 为真实用户的数据，必须原样保留）。
     *
     * @return 受影响行数，即本次被重新归属的作品数
     */
    @Update("UPDATE work SET author = #{new_uuid} WHERE is_claim = 0 AND author = #{old_uuid}")
    int migrateUnclaimedAuthor(@Param("old_uuid") String oldUuid, @Param("new_uuid") String newUuid);
}
