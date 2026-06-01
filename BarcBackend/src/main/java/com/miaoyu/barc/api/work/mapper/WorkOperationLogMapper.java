package com.miaoyu.barc.api.work.mapper;

import com.miaoyu.barc.api.work.model.WorkOperationLogModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 作品操作日志数据访问层
 * 仅提供插入和查询，不提供删除（日志永久保留用于审计）
 */
@Mapper
public interface WorkOperationLogMapper {

    /** 插入操作日志 */
    @Insert("INSERT INTO work_operation_log (id, work_id, operator_uuid, operation_type, detail) " +
            "VALUES (#{id}, #{work_id}, #{operator_uuid}, #{operation_type}, #{detail})")
    int insert(WorkOperationLogModel log);

    /** 根据作品ID查询操作日志，按时间倒序 */
    @Select("SELECT * FROM work_operation_log WHERE work_id = #{workId} ORDER BY created_at DESC")
    List<WorkOperationLogModel> selectByWorkId(String workId);

    /** 分页查询所有操作日志 */
    @Select("SELECT * FROM work_operation_log ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<WorkOperationLogModel> selectByPage(@Param("offset") int offset, @Param("limit") int limit);

    /** 统计总数 */
    @Select("SELECT COUNT(*) FROM work_operation_log")
    Long countAll();

    /** 按操作类型分页查询 */
    @Select("SELECT * FROM work_operation_log WHERE operation_type = #{operationType} " +
            "ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<WorkOperationLogModel> selectByTypeAndPage(
            @Param("operationType") String operationType,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 按操作类型统计 */
    @Select("SELECT COUNT(*) FROM work_operation_log WHERE operation_type = #{operationType}")
    Long countByType(@Param("operationType") String operationType);
}
