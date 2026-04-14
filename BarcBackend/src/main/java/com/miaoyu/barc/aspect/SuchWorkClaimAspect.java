package com.miaoyu.barc.aspect;

import com.miaoyu.barc.annotation.SuchWorkClaimAnno;
import com.miaoyu.barc.api.work.mapper.WorkClaimMapper;
import com.miaoyu.barc.api.work.model.WorkClaimModel;
import com.miaoyu.barc.response.ResourceR;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SuchWorkClaimAspect {
    @Autowired
    private WorkClaimMapper workClaimMapper;

    @Around("@annotation(anno)")
    public Object suchWorkClaimAspect(ProceedingJoinPoint pj, SuchWorkClaimAnno anno) throws Throwable {
        WorkClaimModel workClaim = workClaimMapper.selectById(pj.getArgs()[anno.index()].toString());
        return workClaim == null? ResponseEntity.ok(new ResourceR().resourceSuch(false, null)): pj.proceed();
    }
}
