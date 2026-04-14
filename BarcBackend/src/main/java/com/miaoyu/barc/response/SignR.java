package com.miaoyu.barc.response;

import com.miaoyu.barc.utils.J;

public class SignR {
    public J noSign() {
        J j = new J();
        j.setCode(1);
        j.setMsg("账号未登录");
        j.setData(j.getMsg());
        return j;
    }
    public J usernameClash() {
        J j = new J();
        j.setCode(1);
        j.setMsg("用户名已经存在");
        j.setData(j.getMsg());
        return j;
    }
    public J signIn(boolean isSignIn) {
        J j = new J();
        j.setCode(isSignIn? 0: 1);
        j.setMsg("登录" + (isSignIn? "成功": "失败") + ": Sign in " + (isSignIn? "success" : "fail"));
        j.setData(j.getMsg());
        return j;
    }
    public J signUp(boolean isSignUp) {
        J j = new J();
        j.setCode(isSignUp? 0: 1);
        j.setMsg("注册" + (isSignUp? "成功": "失败") + ": Sign up " + (isSignUp? "success" : "fail"));
        j.setData(j.getMsg());
        return j;
    }
}
