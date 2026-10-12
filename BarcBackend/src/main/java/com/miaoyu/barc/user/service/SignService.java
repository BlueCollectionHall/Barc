package com.miaoyu.barc.user.service;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.response.*;
import com.miaoyu.barc.security.AccountInput;
import com.miaoyu.barc.security.RequestLimits;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.BarcNaigosUuidMapper;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.*;
import com.miaoyu.barc.user.model.dto.SignupRequest;
import com.miaoyu.barc.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** 账号创建只接受白名单字段；注册验证码和两张账号表写入在同一事务提交。 */
@Service
public class SignService {
    @Autowired private JwtService jwtService;
    @Autowired private PasswordHash passwordHash;
    @Autowired private UserBasicMapper userBasicMapper;
    @Autowired private BarcNaigosUuidMapper barcNaigosUuidMapper;
    @Autowired private UserArchiveMapper userArchiveMapper;
    @Autowired private NaigosService naigosService;
    @Autowired private VerificationService verificationService;
    @Autowired private RequestLimits limits;

    @Transactional
    public ResponseEntity<J> signInUserService(String type, String account, String password) {
        if (account == null || account.isBlank() || account.length() > 255 || password == null || password.length() > 128) return rejectedLogin();
        limits.require("login:" + account.trim().toLowerCase(Locale.ROOT), 20, 15 * 60 * 1000L);
        UserBasicModel user = switch (Objects.toString(type, "")) {
            case "username" -> userBasicMapper.selectByUsername(account);
            case "email" -> userBasicMapper.selectByEmail(AccountInput.email(account));
            default -> null;
        };
        if (!active(user) || Boolean.FALSE.equals(user.getLocal_password_enabled())) return rejectedLogin();
        // 只阻断历史外部账号的自动默认口令；其他旧口令验证成功后渐进升级，不批量重置账号。
        if (passwordHash.needsUpgrade(user.getPassword()) && barcNaigosUuidMapper.selectByUuid(user.getUuid()) != null
                && passwordHash.matches("123456", user.getPassword())) {
            return ResponseEntity.ok(new ErrorR().normal("请使用 Naigos 登录后，通过绑定邮箱设置本地密码"));
        }
        if (!passwordHash.matches(password, user.getPassword())) return rejectedLogin();
        if (passwordHash.needsUpgrade(user.getPassword())) {
            if (!userBasicMapper.upgradePassword(user.getUuid(), user.getPassword(), passwordHash.encode(password))) {
                return ResponseEntity.ok(new ErrorR().normal("账号凭据已变化，请重新登录"));
            }
        }
        return ResponseEntity.ok(new SuccessR().normal(jwtService.jwtSigned(user.getUuid())));
    }

    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<J> signInByNaigosService(String type, String account, String password) {
        if ((!"uid".equals(type) && !"email".equals(type)) || account == null || account.isBlank()
                || account.length() > 255 || password == null || password.length() > 128) return rejectedLogin();
        limits.require("naigos-login:" + account.trim().toLowerCase(Locale.ROOT), 20, 15 * 60 * 1000L);
        try {
            String token = naigosService.getNaigosToken(type, account, password);
            if (token == null) return rejectedLogin();
            NaigosUserArchiveModel remote = naigosService.getNaigosArchive(token);
            if (remote == null || remote.getGroup_real_user_id() == null || remote.getQq_id() == null) return rejectedLogin();
            BarcNaigosUuidModel mapping = barcNaigosUuidMapper.selectByNaigosUuid(remote.getGroup_real_user_id());
            String uuid;
            if (mapping == null) {
                String username = remote.getQq_id().toString();
                String email = remote.getEmail();
                if (email == null || email.contains("绑定") || email.contains("未知")) email = username + "@qq.com";
                email = AccountInput.email(email);
                checkAvailable(username, email);
                UserBasicModel user = new UserBasicModel();
                uuid = new GenerateUUID().getUuid32u();
                user.setUuid(uuid); user.setUsername(username); user.setEmail(email);
                // 不信任推导邮箱为已验证，更不能设置任何可预测的本地口令。
                user.setEmail_verified(false); user.setPassword_version(2); user.setLocal_password_enabled(false);
                user.setPassword(passwordHash.encode(UUID.randomUUID().toString()));
                if (!userBasicMapper.insert(user)) throw writeFailure();
                UserArchiveModel archive = initialArchive(uuid, remote.getNickname(), remote.getAvatar());
                if (!userArchiveMapper.insert(archive)) throw writeFailure();
                mapping = new BarcNaigosUuidModel(); mapping.setUuid(uuid); mapping.setNaigos_uuid(remote.getGroup_real_user_id());
                if (!barcNaigosUuidMapper.insert(mapping)) throw writeFailure();
            } else {
                uuid = mapping.getUuid();
                if (!active(userBasicMapper.selectByUuid(uuid))) return rejectedLogin();
            }
            return ResponseEntity.ok(new SuccessR().normal(jwtService.jwtSigned(uuid)));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ApiException(new ErrorR().normal("外部登录请求已中断"));
        } catch (IOException failure) {
            throw new ApiException(new ErrorR().normal("外部登录服务暂不可用"));
        }
    }

    @Transactional
    public ResponseEntity<J> register(SignupRequest request) {
        String email = AccountInput.email(request.email());
        AccountInput.username(request.username()); AccountInput.password(request.password());
        checkAvailable(request.username(), email);
        verificationService.consume(request.unique_id(), email, "Signup", request.code());
        UserBasicModel input = new UserBasicModel(); input.setUsername(request.username()); input.setEmail(email);
        input.setPassword(request.password()); input.setTelephone(request.telephone());
        return createAccount(input, true);
    }

    /** 管理端代建账号也复用创建逻辑，但不能声称用户已证明邮箱所有权。 */
    @Transactional
    public ResponseEntity<J> signupUserService(UserBasicModel input, String email) {
        if (input == null || !Objects.equals(email, input.getEmail())) return ResponseEntity.ok(new ErrorR().normal("注册邮箱与验证邮箱不一致"));
        AccountInput.username(input.getUsername()); AccountInput.password(input.getPassword());
        String normalized = AccountInput.email(email);
        checkAvailable(input.getUsername(), normalized);
        input.setEmail(normalized);
        return createAccount(input, false);
    }

    private ResponseEntity<J> createAccount(UserBasicModel input, boolean emailVerified) {
        if (input.getTelephone() != null && input.getTelephone().length() > 20) throw new ApiException(new ErrorR().normal("电话号码过长"));
        UserBasicModel user = new UserBasicModel(); user.setUuid(new GenerateUUID().getUuid32u());
        user.setUsername(input.getUsername()); user.setEmail(input.getEmail()); user.setTelephone(input.getTelephone());
        user.setPassword(passwordHash.encode(input.getPassword())); user.setPassword_version(2);
        user.setEmail_verified(emailVerified); user.setLocal_password_enabled(true);
        if (!userBasicMapper.insert(user)) throw writeFailure();
        if (!userArchiveMapper.insert(initialArchive(user.getUuid(), "新用户", ""))) throw writeFailure();
        return ResponseEntity.ok(new SignR().signUp(true));
    }

    private void checkAvailable(String username, String email) {
        if (userBasicMapper.selectByUsername(username) != null) throw new ApiException(new SignR().usernameClash());
        if (userBasicMapper.selectByEmail(email) != null) throw new ApiException(new ErrorR().normal("邮箱已被使用"));
    }
    private UserArchiveModel initialArchive(String uuid, String nickname, String avatar) {
        UserArchiveModel archive = new UserArchiveModel(); archive.setUuid(uuid); archive.setIdentity(UserIdentityEnum.USER); archive.setPermission(1);
        archive.setNickname(nickname == null || nickname.isBlank() ? "新用户" : nickname);
        archive.setAvatar(avatar == null ? "" : avatar); return archive;
    }
    private boolean active(UserBasicModel user) { return user != null && (user.getSafe_level() == null || user.getSafe_level() >= 0); }
    private ResponseEntity<J> rejectedLogin() { return ResponseEntity.ok(new ErrorR().normal("账号或密码不正确，或账号当前不可登录")); }
    private ApiException writeFailure() { return new ApiException(new ErrorR().normal("账号创建失败，请重试")); }
}
