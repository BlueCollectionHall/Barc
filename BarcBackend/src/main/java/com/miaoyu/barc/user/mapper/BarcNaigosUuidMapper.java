package com.miaoyu.barc.user.mapper;

import com.miaoyu.barc.user.model.BarcNaigosUuidModel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BarcNaigosUuidMapper {
    /** 本地登录识别历史外部账号，阻断曾经自动设置的默认口令。 */
    @Select("SELECT * FROM barc_naigos_uuid WHERE uuid = #{uuid}")
    BarcNaigosUuidModel selectByUuid(@Param("uuid") String uuid);
    @Select("SELECT * FROM barc_naigos_uuid WHERE naigos_uuid = #{naigos_uuid}")
    BarcNaigosUuidModel selectByNaigosUuid(@Param("naigos_uuid") String naigos_uuid);
    @Insert("INSERT INTO barc_naigos_uuid (uuid, naigos_uuid) VALUES (#{uuid}, #{naigos_uuid})")
    boolean insert(BarcNaigosUuidModel barcNaigosUuidModel);
}
