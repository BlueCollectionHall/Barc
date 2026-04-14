package com.miaoyu.barc;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.J;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class HelloWorldService {

    @RequireUserAndPermissionAnno(@RequireUserAndPermissionAnno.Check(
            isSuchElseRequire = false, identity = UserIdentityEnum.USER, isHasElseUpper = true
    ))
    public ResponseEntity<J> testPermissionAnno(String uuid) {
        return ResponseEntity.ok(new SuccessR().normal("注解放行正常"));
    }
}
