package com.miaoyu.barc.api.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

public interface CosGcMapper {

    @Insert("INSERT INTO cos_garbage_key(bucket, object_key) VALUES(#{bucket}, #{object_key})")
    int enqueue(@Param("bucket") String bucket, @Param("object_key") String object_key);

    @Select("SELECT * FROM cos_garbage_key WHERE processed_at IS NULL ORDER BY created_at LIMIT #{limit}")
    List<CosGcItem> selectPending(@Param("limit") int limit);

    @Update("UPDATE cos_garbage_key SET processed_at = NOW() WHERE id = #{id}")
    int markProcessed(@Param("id") long id);

    @Update("UPDATE cos_garbage_key SET retry_count = retry_count + 1, last_error = #{error} WHERE id = #{id}")
    int markFailed(@Param("id") long id, @Param("error") String error);

    class CosGcItem {
        private Long id;
        private String bucket;
        private String object_key;
        private Integer retry_count;
        private String last_error;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getObject_key() { return object_key; }
        public void setObject_key(String object_key) { this.object_key = object_key; }
        public Integer getRetry_count() { return retry_count; }
        public void setRetry_count(Integer retry_count) { this.retry_count = retry_count; }
        public String getLast_error() { return last_error; }
        public void setLast_error(String last_error) { this.last_error = last_error; }
    }
}
