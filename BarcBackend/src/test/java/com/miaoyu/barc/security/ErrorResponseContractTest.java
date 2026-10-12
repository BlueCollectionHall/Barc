package com.miaoyu.barc.security;

import com.miaoyu.barc.exception.ApiExceptionHandler;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 并发唯一键/外键失败与缺失附件保持稳定错误响应，不把数据库细节传给双端。 */
class ErrorResponseContractTest {
    @RestController static class Fixture {
        @PostMapping("/conflict/{kind}") J conflict(@PathVariable String kind) {
            if ("duplicate".equals(kind)) throw new DuplicateKeyException("INSERT user_basic secret@example.test");
            throw new DataIntegrityViolationException("FOREIGN KEY internal_constraint");
        }
        @PostMapping("/multipart") J upload(@RequestPart("file") MultipartFile file) { return new J(0, "ok", null); }
    }

    @Test void conflictsReturnOnlyTheStableBusinessEnvelope() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Fixture()).setControllerAdvice(new ApiExceptionHandler()).build();
        for (String kind : new String[]{"duplicate", "relation"}) {
            mvc.perform(post("/conflict/" + kind)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.msg").value("记录已存在或关联数据已变化，请刷新后重试"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret@example.test"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal_constraint"))));
        }
        mvc.perform(multipart("/multipart")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1));
    }

    @Test void textualErrorsAreReadableByEitherFrontendWithoutChangingDataShape() {
        var responses = new com.miaoyu.barc.response.ErrorR();
        var text = responses.normal("标题不能为空");
        org.junit.jupiter.api.Assertions.assertEquals("标题不能为空", text.getMsg());
        org.junit.jupiter.api.Assertions.assertEquals(text.getMsg(), text.getData());
        var payload = java.util.Map.of("field", "title"); var structured = responses.normal(payload);
        org.junit.jupiter.api.Assertions.assertEquals("失败：Error", structured.getMsg());
        org.junit.jupiter.api.Assertions.assertSame(payload, structured.getData());
        org.junit.jupiter.api.Assertions.assertEquals("失败：Error", responses.normal(" ").getMsg());
    }
}
