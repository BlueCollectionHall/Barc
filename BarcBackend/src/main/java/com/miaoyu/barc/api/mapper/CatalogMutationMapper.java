package com.miaoyu.barc.api.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** 小型配置集合的事务互斥锁，防止并发 max+1、重排及关联删除出现交错结果。 */
public interface CatalogMutationMapper {
    /** 必须在调用方业务事务内执行；唯一行的写锁一直持有到提交或回滚。scope 只使用服务端常量。 */
    @Insert("INSERT INTO catalog_mutation_lock(scope) VALUES(#{scope}) ON DUPLICATE KEY UPDATE scope=#{scope}")
    int lock(@Param("scope") String scope);
}
