package com.miaoyu.barc.user.service;

import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.vo.UserSearchView;
import com.miaoyu.barc.utils.RequestInput;
import org.springframework.stereotype.Service;
import java.util.List;

/** 两个历史选择器及匿名搜索共用规范化、容量和最小响应规则。 */
@Service
public class UserSearchService {
    private final UserArchiveMapper users;
    public UserSearchService(UserArchiveMapper users) { this.users = users; }
    public List<UserSearchView> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String normalized = RequestInput.required(keyword.trim(), "搜索词", 100);
        return users.searchByKeyword(normalized).stream().map(UserSearchView::from).toList();
    }
}
