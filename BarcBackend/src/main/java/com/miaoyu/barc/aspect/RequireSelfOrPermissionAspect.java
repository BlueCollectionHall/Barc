package com.miaoyu.barc.aspect;

import com.miaoyu.barc.annotation.RequireSelfOrPermissionAnno;
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

/** 本人字段必须由调用方从持久化记录取出；禁止将模型 toString 当作作者 UUID。 */
@Aspect
@Component
public class RequireSelfOrPermissionAspect {
    @Autowired
    private UserArchiveMapper userArchiveMapper;

    @Around("@annotation(anno)")
    public Object requireItemSelfOrManagerAspect(ProceedingJoinPoint joinPoint, RequireSelfOrPermissionAnno anno) throws Throwable {
        Object[] args = joinPoint.getArgs();
        int userIndex = anno.uuidIndex();
        int authorIndex = anno.authorUuidIndex();
        if (userIndex < 0 || authorIndex < 0 || userIndex >= args.length || authorIndex >= args.length
                || userIndex == authorIndex || !(args[userIndex] instanceof String uuid) || uuid.isBlank()
                || (args[authorIndex] != null && !(args[authorIndex] instanceof String))) {
            throw new ApiException(new ErrorR().normal("本人鉴权参数无效"));
        }
        UserArchiveModel user = userArchiveMapper.selectByUuid(uuid);
        if (user == null) throw new ApiException(new UserR().noSuchUser());
        if (!uuid.equals(args[authorIndex]) && !PermissionPolicy.matches(user, anno.identity(), anno.targetPermission(), anno.isHasElseUpper())) {
            throw new ApiException(new UserR().insufficientAccountPermission());
        }
        return joinPoint.proceed();
    }
}
