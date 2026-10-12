package com.miaoyu.barc.aspect;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.permission.PermissionPolicy;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 按顺序检查全部条件，通过全部检查后才执行一次业务方法。 */
@Aspect
@Component
public class RequireUserAndPermissionAspect {
    @Autowired
    private UserArchiveMapper userArchiveMapper;

    @Around("@annotation(anno)")
    public Object requireSuchAndPermissionAspect(ProceedingJoinPoint joinPoint, RequireUserAndPermissionAnno anno) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (anno.value().length == 0) throw new ApiException(new ErrorR().normal("鉴权条件不能为空"));
        for (RequireUserAndPermissionAnno.Check check : anno.value()) {
            int index = check.uuidIndex();
            if (index < 0 || index >= args.length || !(args[index] instanceof String uuid) || uuid.isBlank()) {
                throw new ApiException(new ErrorR().normal("鉴权用户参数无效"));
            }
            UserArchiveModel user = userArchiveMapper.selectByUuid(uuid);
            if (user == null) throw new ApiException(new UserR().noSuchUser());
            // 多个 Check 是 AND；同一身份的 OR 权限应写为单个 Check 的按位或掩码。
            if (!check.isSuchElseRequire() && !PermissionPolicy.matches(user, check.identity(), check.targetPermission(), check.isHasElseUpper())) {
                throw new ApiException(new UserR().insufficientAccountPermission());
            }
        }
        return joinPoint.proceed();
    }
}
