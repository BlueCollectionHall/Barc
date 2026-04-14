package com.miaoyu.barc.aspect;

import com.miaoyu.barc.annotation.SuchMessageAnno;
import com.miaoyu.barc.comment.mapper.MessageBoardMapper;
import com.miaoyu.barc.comment.model.MessageBoardModel;
import com.miaoyu.barc.response.ResourceR;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SuchMessageAspect {
    @Autowired
    private MessageBoardMapper messageBoardMapper;

    @Around("@annotation(anno)")
    public Object suchMessageAspect(ProceedingJoinPoint pj, SuchMessageAnno anno) throws Throwable {
        if (anno.selectType().equals("id")) {
            return this.suchMessageById(pj.getArgs()[anno.index()].toString())? pj.proceed(): ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        } else if (anno.selectType().equals("model")) {
            return this.suchMessageById(((MessageBoardModel) pj.getArgs()[anno.index()]).getId())? pj.proceed(): ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
    }

    private boolean suchMessageById(String id) {
        return messageBoardMapper.selectById(id) != null;
    }
}
