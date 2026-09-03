package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 统一解析作品的平台归属与收录者信息。
 * author_nickname 是站外原作者署名，不参与平台归属解析。
 */
@Service
public class WorkAttributionService {
    @Autowired
    private UserArchiveMapper userArchiveMapper;

    /** author 始终代表平台内当前归属账号，无论作品是否已被认领。 */
    public String resolvePlatformOwnerNickname(WorkModel work) {
        return resolveUserNickname(work == null ? null : work.getAuthor(), "未知用户");
    }

    /** uploader 只代表执行收录的用户；自创作品可以没有 uploader。 */
    public String resolveUploaderNickname(WorkModel work) {
        return resolveUserNickname(work == null ? null : work.getUploader(), "");
    }

    private String resolveUserNickname(String uuid, String fallback) {
        if (uuid == null || uuid.isBlank()) return fallback;
        UserArchiveModel archive = userArchiveMapper.selectByUuid(uuid);
        if (archive == null || archive.getNickname() == null || archive.getNickname().isBlank()) return fallback;
        return archive.getNickname();
    }
}
