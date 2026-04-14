package com.miaoyu.barc.aspect;

import com.miaoyu.barc.annotation.SuchWorkCommentAnno;
import com.miaoyu.barc.comment.mapper.WorkCommentMapper;
import com.miaoyu.barc.comment.mapper.WorkCommentReplyMapper;
import com.miaoyu.barc.comment.model.WorkCommentModel;
import com.miaoyu.barc.comment.model.WorkCommentReplyModel;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SuchWorkCommentAspect {
    @Autowired
    private WorkCommentMapper workCommentMapper;
    @Autowired
    private WorkCommentReplyMapper workCommentReplyMapper;
    @Around("@annotation(anno)")
    public Object suchWorkCommentAspect(ProceedingJoinPoint pj, SuchWorkCommentAnno anno) throws Throwable {
        ResponseEntity<J> responseSuchFalse = ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (anno.selectType().equals("id")) {
            return suchWorkCommentAndReplyById(pj, pj.getArgs()[anno.index()].toString(), anno.commentAndReply());
        } else if (anno.selectType().equals("model")) {
            String id = null;
            if (anno.commentAndReply().equals("comment")) {
                id = ((WorkCommentModel) pj.getArgs()[anno.index()]).getId();
            } else if (anno.commentAndReply().equals("reply")) {
                id = ((WorkCommentReplyModel) pj.getArgs()[anno.index()]).getId();
            }
            return suchWorkCommentAndReplyById(pj, id, anno.commentAndReply());
        }
        return responseSuchFalse;
    }

    private Object suchWorkCommentAndReplyById(ProceedingJoinPoint pj, String id, String commentAndReply) throws Throwable {
        ResponseEntity<J> responseSuchFalse = ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        return switch (commentAndReply) {
            case "comment" -> workCommentMapper.selectById(id) == null ? responseSuchFalse : pj.proceed();
            case "reply" -> workCommentReplyMapper.selectById(id) == null? responseSuchFalse: pj.proceed();
            default -> responseSuchFalse;
        };
    }
}
