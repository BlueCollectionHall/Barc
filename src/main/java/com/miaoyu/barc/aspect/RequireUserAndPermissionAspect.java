package com.miaoyu.barc.aspect;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.permission.ComparePermission;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequireUserAndPermissionAspect {
    @Autowired
    private UserArchiveMapper userArchiveMapper;
    @Autowired
    private ComparePermission comparePermission;
    @Around("@annotation(anno)")
    public Object requireSuchAndPermissionAspect(
            ProceedingJoinPoint pj,
            RequireUserAndPermissionAnno anno
    ) throws Throwable {
        Object [] args = pj.getArgs();
        for (RequireUserAndPermissionAnno.Check check: anno.value()) {
            if (check.uuidIndex() < 0 || args.length <= check.uuidIndex()) {
                return ResponseEntity.ok(new ErrorR().normal("索引越界！"));
            }
            String uuid = args[check.uuidIndex()].toString();
            UserArchiveModel userArchive = userArchiveMapper.selectByUuid(uuid);
            // 验证用户是否存在
            if (userArchive == null) {
                return ResponseEntity.ok(new UserR().noSuchUser());
            }
            // 检测是否鉴权分支
            if (check.isSuchElseRequire()) {
                return pj.proceed();
            }
            // 验证身份匹配
            if (userArchive.getIdentity() == check.identity()) {
                // 若是Has式二进制鉴权
                if (check.isHasElseUpper()) {
                    return (
                            comparePermission.has(
                                    userArchive.getPermission(), check.targetPermission())?
                                    pj.proceed():
                                    ResponseEntity.ok(new UserR().insufficientAccountPermission()
                                    ));
                }
                // 若是Upper式二进制鉴权
                else {
                    return (
                            comparePermission.compare(
                                    userArchive.getPermission(), check.targetPermission())?
                                    pj.proceed():
                                    ResponseEntity.ok(new UserR().insufficientAccountPermission())
                            );
                }
            }
            return ResponseEntity.ok(new UserR().insufficientAccountPermission());
        }
        // 若是数组参数长度为空
        if (args.length == 0) {
            return ResponseEntity.ok(new ErrorR().normal("参数低于最小界！"));
        }
        return pj.proceed();
    }
}
