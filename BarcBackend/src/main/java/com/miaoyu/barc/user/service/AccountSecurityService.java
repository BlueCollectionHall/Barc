package com.miaoyu.barc.user.service;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.security.AccountInput;
import com.miaoyu.barc.security.RequestLimits;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.mapper.BarcNaigosUuidMapper;
import com.miaoyu.barc.user.model.UserBasicModel;
import com.miaoyu.barc.user.model.dto.EmailChangeRequest;
import com.miaoyu.barc.user.model.dto.UserBasicUpdateRequest;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.PasswordHash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** 账号安全写入统一校验当前账号、凭据和一次性验证码，成功写入才提交消费。 */
@Service
public class AccountSecurityService {
    @Autowired private UserBasicMapper userBasicMapper;
    @Autowired private BarcNaigosUuidMapper barcNaigosUuidMapper;
    @Autowired private PasswordHash passwordHash;
    @Autowired private VerificationService verificationService;
    @Autowired private RequestLimits limits;

    @Transactional
    public ResponseEntity<J> resetPassword(String uuid, String uniqueId, String code, String email, String password) {
        AccountInput.password(password);
        UserBasicModel user = requiredUser(uuid, true);
        String normalized = AccountInput.email(email);
        if (!normalized.equalsIgnoreCase(user.getEmail())) throw new ApiException(new ErrorR().normal("验证码邮箱必须是当前账号的绑定邮箱"));
        verificationService.consume(uniqueId, normalized, "ResetPassword", code);
        if (!userBasicMapper.updatePassword(uuid, passwordHash.encode(password))) throw new ApiException(new ErrorR().normal("密码更新失败"));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    @Transactional
    public ResponseEntity<J> changeEmail(String uuid, EmailChangeRequest request) {
        requiredUser(uuid, true);
        verifyCurrentPassword(uuid, request.current_password());
        String email = AccountInput.email(request.email());
        if (userBasicMapper.selectByEmail(email) != null) throw new ApiException(new ErrorR().normal("邮箱已被使用"));
        verificationService.consume(request.unique_id(), email, "ChangeEmail", request.code());
        if (!userBasicMapper.updateVerifiedEmail(uuid, email)) throw new ApiException(new ErrorR().normal("邮箱更新失败"));
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    @Transactional
    public ResponseEntity<J> updateBasic(String uuid, UserBasicUpdateRequest request) {
        UserBasicModel stored = requiredUser(uuid, true);
        if (request == null || !uuid.equals(request.uuid())) throw new ApiException(new UserR().uuidMismatch());
        if (request.email() != null && !AccountInput.email(request.email()).equalsIgnoreCase(stored.getEmail())) {
            throw new ApiException(new ErrorR().normal("请通过邮箱改绑流程验证新邮箱"));
        }
        String username = AccountInput.username(request.username());
        if (!Objects.equals(username, stored.getUsername())) {
            verifyCurrentPassword(uuid, request.current_password());
            UserBasicModel existing = userBasicMapper.selectByUsername(username);
            if (existing != null && !uuid.equals(existing.getUuid())) throw new ApiException(new ErrorR().normal("用户名已被使用"));
        }
        if (request.telephone() != null && request.telephone().length() > 20) throw new ApiException(new ErrorR().normal("电话号码过长"));
        UserBasicModel update = new UserBasicModel();
        update.setUuid(uuid); update.setUsername(username); update.setTelephone(request.telephone());
        return ResponseEntity.ok(new ChangeR().udu(userBasicMapper.update(update), 3));
    }

    public void verifyCurrentPassword(String uuid, String password) {
        limits.require("confirm-password:" + uuid, 10, 15 * 60 * 1000L);
        UserBasicModel user = requiredUser(uuid, false);
        boolean legacyDefault = passwordHash.needsUpgrade(user.getPassword())
                && barcNaigosUuidMapper.selectByUuid(uuid) != null && passwordHash.matches("123456", user.getPassword());
        if (Boolean.FALSE.equals(user.getLocal_password_enabled()) || legacyDefault || !passwordHash.matches(password, user.getPassword())) {
            throw new ApiException(new ErrorR().normal("当前密码不正确，请先通过绑定邮箱设置本地密码"));
        }
    }

    private UserBasicModel requiredUser(String uuid, boolean lock) {
        UserBasicModel user = lock ? userBasicMapper.selectByUuidForUpdate(uuid) : userBasicMapper.selectByUuid(uuid);
        if (user == null || (user.getSafe_level() != null && user.getSafe_level() < 0)) throw new ApiException(new UserR().noSuchUser());
        return user;
    }
}
