package com.miaoyu.barc.user.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.response.*;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.mapper.VerificationCodeMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.model.VerificationCodeModel;
import com.miaoyu.barc.user.model.vo.UserBasicVo;
import com.miaoyu.barc.user.model.vo.UserInfoVo;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.PasswordHash;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import com.miaoyu.barc.utils.dto.PageResultDto;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class UserService {
    @Autowired
    private UserArchiveMapper userArchiveMapper;
    @Autowired
    private UserBasicMapper userBasicMapper;
    @Autowired
    private PasswordHash passwordHash;
    @Autowired
    private VerificationCodeMapper verificationCodeMapper;

    @Transactional(readOnly = true)
    public ResponseEntity<J> getUsersByPage(PageRequestDto requestDto) {
        log.info("用户档案=分页查询，页码{}，大小{}", requestDto.getPage_num(), requestDto.getPage_size());
        // 初始化参数验证和默认值设置
        PageInitPojo init = new PageInitPojo(requestDto);
        int pageNum = init.getPageNum(); // 页码
        int pageSize = init.getPageSize(); // 页大小
        int offset = init.getOffset(); // 偏移量
        // 查询数据
        List<UserInfoVo> models;
        if (requestDto.getParams().isEmpty()) {
            // 不带条件参数
            models = userArchiveMapper.selectByPage(offset, pageSize);
        } else {
            // 携带条件参数
            models = userArchiveMapper.selectUserInfoByPage(offset, pageSize, requestDto.getParams());
        }
        // 查询总数
        Long total = userArchiveMapper.countUserInfo(requestDto.getParams());
        // 计算总页数
        int mathTotalPage = (int) Math.ceil((double) total / pageSize);
        Integer totalPage = mathTotalPage == 0 ? 1 : mathTotalPage;
        // 数据封装并返回
        return ResponseEntity.ok(
                new SuccessR().normal(
                        new PageResultDto<>(
                                total,
                                models,
                                pageNum,
                                pageSize,
                                totalPage)));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> getCurrentByUuidService(String uuid) {
        return ResponseEntity.ok(new SuccessR().normal(userArchiveMapper.selectByUuid(uuid)));
    }

    public ResponseEntity<J> getCurrentByUsernameService(String username) {
        UserArchiveModel userArchive = userArchiveMapper.selectByUsername(username);
        if (Objects.isNull(userArchive)) {
            return ResponseEntity.ok(new UserR().noSuchUser());
        }
        return ResponseEntity.ok(new SuccessR().normal(userArchive));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> getMeCurrentService(String uuid) {
        return ResponseEntity.ok(new SuccessR().normal(userArchiveMapper.selectByUuid(uuid)));
    }

    /**
     * 获取本账号基础信息（脱敏返回）。
     *
     * <p>安全修复（2026-10）：原先直接返回 {@link UserBasicModel} 行对象，
     * 会把 {@code password}（口令哈希）与 {@code password_version} 一并下发给前端。
     * 现改为统一经 {@link #toUserBasicVo(UserBasicModel)} 转换后返回 {@link UserBasicVo}，
     * 只保留双端实际消费的字段。</p>
     *
     * <p>为什么不去改 Mapper 的 SQL：{@code userBasicMapper.selectByUuid} 还被登录校验、
     * 封号、通知、认领富化等多条链路复用，它们需要完整的行对象（含口令哈希做比对），
     * 因此脱敏必须落在「对外出口」这一层，而不是查询层。</p>
     */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> getMeBasicService(String uuid) {
        UserBasicModel userBasic = userBasicMapper.selectByUuid(uuid);
        // 档案表与基础表是两张表，理论上存在 user_archive 有记录而 user_basic 缺失的情况，这里显式兜底
        if (Objects.isNull(userBasic)) {
            return ResponseEntity.ok(new UserR().noSuchUser());
        }
        return ResponseEntity.ok(new SuccessR().normal(toUserBasicVo(userBasic)));
    }

    /**
     * 用户基础信息行对象 → 对外脱敏视图。
     *
     * <p>这里逐字段显式拷贝（而不是用 BeanUtils 整对象复制）是刻意的：
     * 一旦 {@link UserBasicModel} 未来新增敏感列，显式映射不会把它顺手带出去。</p>
     *
     * @param model 数据库查询到的用户基础信息行对象
     * @return 只含可对外字段的视图对象
     */
    private UserBasicVo toUserBasicVo(UserBasicModel model) {
        UserBasicVo vo = new UserBasicVo();
        vo.setUuid(model.getUuid());
        vo.setUsername(model.getUsername());
        vo.setEmail(model.getEmail());
        vo.setEmail_verified(model.getEmail_verified());
        vo.setTelephone(model.getTelephone());
        vo.setSafe_level(model.getSafe_level());
        vo.setCreated_at(model.getCreated_at());
        vo.setUpdated_at(model.getUpdated_at());
        return vo;
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> editArchiveService(String uuid, UserArchiveModel requestModel) {
        if (!uuid.equals(requestModel.getUuid())) {
            return ResponseEntity.ok(new ErrorR().normal("登录账号与要修改的信息不匹配！"));
        }
        if (!Objects.isNull(requestModel.getBirthday())) {
            requestModel.setAge(Period.between(requestModel.getBirthday(), LocalDate.now()).getYears());
        }
        boolean b = userArchiveMapper.update(requestModel);
        if (b) {
            return ResponseEntity.ok(new ChangeR().udu(true, 3));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 3));
    }
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> editBasicService(String uuid, UserBasicModel requestModel) {
        if (!uuid.equals(requestModel.getUuid())) {
            return ResponseEntity.ok(new ErrorR().normal("登录账号与要修改的信息不匹配！"));
        }
        boolean b = userBasicMapper.update(requestModel);
        if (b) {
            return ResponseEntity.ok(new ChangeR().udu(true, 3));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 3));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> resetPasswordService(String uuid, String uniqueId, String code, String email, String password) {
        VerificationCodeModel vcDB = verificationCodeMapper.selectByUniqueId(uniqueId);
        if (Objects.isNull(vcDB)) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        if (!email.equals(vcDB.getUsername())) {
            return ResponseEntity.ok(new ErrorR().normal("验证码记录邮箱与要修改的邮箱不相同！"));
        }
        if (!code.equals(vcDB.getCode()) || !vcDB.getScenario().equals("ResetPassword")) {
            return ResponseEntity.ok(new ErrorR().normal("验证码不一致！"));
        }
        String s = passwordHash.passwordHash256(password);
        if (Objects.isNull(s)) {
            return ResponseEntity.ok(new ErrorR().normal("密码重置失败！"));
        }
        verificationCodeMapper.updateUsed(uniqueId);
        boolean b = userBasicMapper.updatePassword(uuid, s);
        if (b) {
            return ResponseEntity.ok(new ChangeR().udu(true, 3));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 3));
    }
}
