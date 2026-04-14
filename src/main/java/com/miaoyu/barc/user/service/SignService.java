package com.miaoyu.barc.user.service;

import com.miaoyu.barc.email.utils.SendEmailUtils;
import com.miaoyu.barc.response.*;
import com.miaoyu.barc.user.mapper.BarcNaigosUuidMapper;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.mapper.VerificationCodeMapper;
import com.miaoyu.barc.user.model.*;
import com.miaoyu.barc.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class SignService {
    @Autowired
    private SendEmailUtils sendEmailUtils;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordHash passwordHash;
    @Autowired
    private VerificationCodeMapper verificationCodeMapper;
    @Autowired
    private UserBasicMapper userBasicMapper;
    @Autowired
    private BarcNaigosUuidMapper barcNaigosUuidMapper;
    @Autowired
    private UserArchiveMapper userArchiveMapper;

    public ResponseEntity<J> signInUserService(String type, String account, String password) {
        UserBasicModel userBasic;
        switch (type) {
            case "username": {
                userBasic = userBasicMapper.selectByUsername(account);
                break;
            } case "email": {
                userBasic = userBasicMapper.selectByEmail(account);
                break;
            } default: {
                return ResponseEntity.ok(new ErrorR().normal("参数错误！"));
            }
        }
        if (userBasic == null) {
            return ResponseEntity.ok(new UserR().noSuchUser());
        }
        String s = passwordHash.passwordHash256(password);
        if (s == null) {
            return ResponseEntity.ok(new SignR().signIn(false));
        }
        if (s.equals(userBasic.getPassword())) {
            String token = jwtService.jwtSigned(userBasic.getUuid());
            return ResponseEntity.ok(new SuccessR().normal(token));
        } else {
            return ResponseEntity.ok(new ErrorR().normal("密码错误"));
        }
    }
    @Autowired
    private NaigosService naigosService;
    public ResponseEntity<J> signInByNaigosService(String type, String account, String password) {
        try {
            String token = naigosService.getNaigosToken(type, account, password);
            // 未取到Naigos服务token令牌
            if (Objects.isNull(token)) {
                return ResponseEntity.ok(new ErrorR().normal("Naigos服务登录失败！账号或密码不正确！"));
            }
            // 未取到Naigos服务用户信息
            NaigosUserArchiveModel naigosArchive = naigosService.getNaigosArchive(token);
            if (Objects.isNull(naigosArchive)) {
                return ResponseEntity.ok(new UserR().noSuchUser());
            }
            BarcNaigosUuidModel barcNaigosUuid = barcNaigosUuidMapper.selectByNaigosUuid(naigosArchive.getGroup_real_user_id());
            // 未得到交换表中记录
            if (Objects.isNull(barcNaigosUuid)) {
                // 向基础信息表新建记录
                UserBasicModel userBasic = new UserBasicModel();
                userBasic.setUuid(new GenerateUUID().getUuid32u());
                userBasic.setUsername(naigosArchive.getQq_id().toString());
                userBasic.setEmail(naigosArchive.getEmail().contains("绑定") || naigosArchive.getEmail().contains("未知")? naigosArchive.getQq_id() + "@qq.com": naigosArchive.getEmail());
                userBasic.setPassword(passwordHash.passwordHash256("123456"));
                userBasic.setEmail_verified(true);
                boolean insert = userBasicMapper.insert(userBasic);
                if (insert) {
                    // 向交换表新建记录
                    barcNaigosUuid = new BarcNaigosUuidModel();
                    barcNaigosUuid.setUuid(userBasic.getUuid());
                    barcNaigosUuid.setNaigos_uuid(naigosArchive.getGroup_real_user_id());
                    boolean barcNaigosInsert = barcNaigosUuidMapper.insert(barcNaigosUuid);
                    if (barcNaigosInsert) {
                        // 向个人信息表新建记录
                        UserArchiveModel userArchive = new UserArchiveModel();
                        userArchive.setUuid(userBasic.getUuid());
                        userArchive.setAvatar(naigosArchive.getAvatar());
                        userArchive.setNickname(naigosArchive.getNickname());
                        boolean userArchiveInsert = userArchiveMapper.insert(userArchive);
                        if (userArchiveInsert) {
                            return ResponseEntity.ok(new SuccessR().normal(jwtService.jwtSigned(userBasic.getUuid())));
                        }
                    }
                    return ResponseEntity.ok(new SignR().signIn(false));
                }
                return ResponseEntity.ok(new SignR().signIn(false));
            }
            // 得到交换表记录签发token令牌
            return ResponseEntity.ok(new SuccessR().normal(jwtService.jwtSigned(barcNaigosUuid.getUuid())));
        } catch (IOException | InterruptedException e) {
            return ResponseEntity.status(401).body(new ErrorR().normal("请求出错"));
        }
    }
    public ResponseEntity<J> signupUserService(UserBasicModel userBasic, String email) {
        if (!userBasic.getEmail().equals(email)) {
            return ResponseEntity.ok(new ErrorR().normal("注册邮箱与验证邮箱不一致"));
        }
        if (userBasicMapper.selectByUsername(userBasic.getUsername()) != null) {
            return ResponseEntity.ok(new SignR().usernameClash());
        }
        userBasic.setEmail_verified(true);
        String calcPwd = passwordHash.passwordHash256(userBasic.getPassword());
        if (Objects.isNull(calcPwd)) {
            return ResponseEntity.ok(new SignR().signUp(false));
        }
        userBasic.setPassword(calcPwd);
        userBasic.setUuid(new GenerateUUID().getUuid32u());
        boolean insert = userBasicMapper.insert(userBasic);
        if (!insert) {
            return ResponseEntity.ok(new SignR().signUp(false));
        }
        UserArchiveModel userArchive = new UserArchiveModel();
        userArchive.setUuid(userBasic.getUuid());
        userArchive.setNickname("新用户" + System.currentTimeMillis());
        userArchive.setAvatar("https://file.naigos.cn:52011/avatar/dc4d81a09fb2728a3c7d028b035652fa");
        boolean userArchiveInsert = userArchiveMapper.insert(userArchive);
        if (userArchiveInsert) {
            return ResponseEntity.ok(new SignR().signUp(true));
        }
        return ResponseEntity.ok(new SignR().signUp(false));
    }
    public J checkSignupCode(String uniqueId, String code, String email) {
        VerificationCodeModel vcDB = verificationCodeMapper.selectByUniqueId(uniqueId);
        if (vcDB == null) {
            return new ResourceR().resourceSuch(false, null);
        }
        if (!vcDB.getUsername().equals(email) || !vcDB.getScenario().equals("Signup") || vcDB.getUsed()) {
            return new ErrorR().normal("验证信息不匹配");
        }
        if (!vcDB.getCode().equals(code)) {
            return new ErrorR().normal("验证码不匹配");
        }
        if (vcDB.getExpiration_at().isBefore(LocalDateTime.now())) {
            return new ErrorR().normal("验证码已过期");
        }
        verificationCodeMapper.updateUsed(uniqueId);
        return new SuccessR().normal("验证成功");
    }
}
