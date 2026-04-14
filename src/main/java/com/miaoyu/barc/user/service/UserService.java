package com.miaoyu.barc.user.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.response.*;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.mapper.VerificationCodeMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.model.VerificationCodeModel;
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

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> getMeBasicService(String uuid) {
        return ResponseEntity.ok(new SuccessR().normal(userBasicMapper.selectByUuid(uuid)));
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
