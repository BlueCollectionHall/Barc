package com.miaoyu.barc.api.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.*;

/** 对象绑定和回收共享这一行锁，回收中的 key 不能被新业务引用。 */
public interface StorageObjectMapper {
    @Insert("INSERT IGNORE INTO storage_object(bucket,object_key,owner_uuid) VALUES(#{bucket},#{key},#{owner})")
    int reserve(@Param("bucket") String bucket, @Param("key") String key, @Param("owner") String owner);
    @Select("SELECT * FROM storage_object WHERE bucket=#{bucket} AND object_key=#{key} FOR UPDATE")
    StoredObject lock(@Param("bucket") String bucket, @Param("key") String key);
    @Update("UPDATE storage_object SET state=#{state} WHERE bucket=#{bucket} AND object_key=#{key}")
    int setState(@Param("bucket") String bucket, @Param("key") String key, @Param("state") String state);

    /** 历史签名 URL 和带编码路径也按引用保守保留，不能只比较原始 key。 */
    default long references(String key) {
        if (key == null || key.isBlank()) return 0;
        String encoded = java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8)
            .replace("+", "%20").toLowerCase(java.util.Locale.ROOT);
        return countReferences(key, encoded, encoded.replace("%2f", "/"));
    }

    /** 可恢复作品仍计入引用；匹配允许保守多留对象，避免误删历史 URL 中的图片。 */
    @Select("SELECT " +
            "(SELECT COUNT(*) FROM school WHERE deleted_at IS NULL AND ((LOCATE(#{key},logo)>0 OR LOCATE(#{encoded},LOWER(logo))>0 OR LOCATE(#{encodedPath},LOWER(logo))>0) OR (LOCATE(#{key},beautify_logo)>0 OR LOCATE(#{encoded},LOWER(beautify_logo))>0 OR LOCATE(#{encodedPath},LOWER(beautify_logo))>0) OR (LOCATE(#{key},bg)>0 OR LOCATE(#{encoded},LOWER(bg))>0 OR LOCATE(#{encodedPath},LOWER(bg))>0))) + " +
            "(SELECT COUNT(*) FROM club WHERE deleted_at IS NULL AND ((LOCATE(#{key},logo)>0 OR LOCATE(#{encoded},LOWER(logo))>0 OR LOCATE(#{encodedPath},LOWER(logo))>0) OR (LOCATE(#{key},bg)>0 OR LOCATE(#{encoded},LOWER(bg))>0 OR LOCATE(#{encodedPath},LOWER(bg))>0))) + " +
            "(SELECT COUNT(*) FROM student WHERE deleted_at IS NULL AND ((LOCATE(#{key},avatar_square)>0 OR LOCATE(#{encoded},LOWER(avatar_square))>0 OR LOCATE(#{encodedPath},LOWER(avatar_square))>0) OR (LOCATE(#{key},avatar_rectangle)>0 OR LOCATE(#{encoded},LOWER(avatar_rectangle))>0 OR LOCATE(#{encodedPath},LOWER(avatar_rectangle))>0) OR (LOCATE(#{key},body_image)>0 OR LOCATE(#{encoded},LOWER(body_image))>0 OR LOCATE(#{encodedPath},LOWER(body_image))>0))) + " +
            "(SELECT COUNT(*) FROM work_cover_image WHERE ((LOCATE(#{key},object_key)>0 OR LOCATE(#{encoded},LOWER(object_key))>0 OR LOCATE(#{encodedPath},LOWER(object_key))>0))) + " +
            "(SELECT COUNT(*) FROM work_image WHERE ((LOCATE(#{key},object_key)>0 OR LOCATE(#{encoded},LOWER(object_key))>0 OR LOCATE(#{encodedPath},LOWER(object_key))>0))) + " +
            "(SELECT COUNT(*) FROM trend_image WHERE ((LOCATE(#{key},object_key)>0 OR LOCATE(#{encoded},LOWER(object_key))>0 OR LOCATE(#{encodedPath},LOWER(object_key))>0))) + " +
            "(SELECT COUNT(*) FROM work WHERE ((LOCATE(#{key},cover_image)>0 OR LOCATE(#{encoded},LOWER(cover_image))>0 OR LOCATE(#{encodedPath},LOWER(cover_image))>0) OR (LOCATE(#{key},banner_image)>0 OR LOCATE(#{encoded},LOWER(banner_image))>0 OR LOCATE(#{encodedPath},LOWER(banner_image))>0) OR (LOCATE(#{key},content)>0 OR LOCATE(#{encoded},LOWER(content))>0 OR LOCATE(#{encodedPath},LOWER(content))>0))) + " +
            "(SELECT COUNT(*) FROM notice WHERE ((LOCATE(#{key},content)>0 OR LOCATE(#{encoded},LOWER(content))>0 OR LOCATE(#{encodedPath},LOWER(content))>0))) + " +
            "(SELECT COUNT(*) FROM user_archive WHERE ((LOCATE(#{key},avatar)>0 OR LOCATE(#{encoded},LOWER(avatar))>0 OR LOCATE(#{encodedPath},LOWER(avatar))>0))) + " +
            "(SELECT COUNT(*) FROM background_image WHERE deleted_at IS NULL AND ((LOCATE(#{key},object_key)>0 OR LOCATE(#{encoded},LOWER(object_key))>0 OR LOCATE(#{encodedPath},LOWER(object_key))>0))) + " +
            "(SELECT COUNT(*) FROM friend_link WHERE deleted_at IS NULL AND ((LOCATE(#{key},logo_key)>0 OR LOCATE(#{encoded},LOWER(logo_key))>0 OR LOCATE(#{encodedPath},LOWER(logo_key))>0)))")
    long countReferences(@Param("key") String key, @Param("encoded") String encoded, @Param("encodedPath") String encodedPath);

    @Data class StoredObject {
        private String owner_uuid;
        private String state;
    }
}
