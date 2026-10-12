package com.miaoyu.barc.security;

import com.miaoyu.barc.exception.ApiExceptionHandler;
import com.miaoyu.barc.utils.J;
import org.mockito.Answers;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;

/** HTTP 合同测试仅替换业务服务；实际执行 Spring 参数绑定、JSON 转换与统一异常处理。 */
public final class HttpContractSupport {
    private HttpContractSupport() { }
    public static <T> T apiMock(Class<T> type) {
        return mock(type, invocation -> invocation.getMethod().getReturnType() == ResponseEntity.class
                ? ResponseEntity.ok(new J(0, "ok", null)) : Answers.RETURNS_DEFAULTS.answer(invocation));
    }
    public static MockMvc mvc(Object... controllers) {
        return MockMvcBuilders.standaloneSetup(controllers).setControllerAdvice(new ApiExceptionHandler()).build();
    }
}
