package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.notice.model.NoticeModel;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 用攻击样例与真实序列化/映射边界验证富文本净化，而非只检查依赖或方法调用。 */
class RichTextTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "<img src=x onerror=alert(1)><script>alert(1)</script>",
        "<svg><a xlink:href='javascript:alert(1)'>x</a></svg>",
        "<a href='java&#x73;cript:alert(1)'>x</a><iframe srcdoc='<script>alert(1)</script>'></iframe>",
        "<math><mtext><table><mglyph><style><!--</style><img title='--><img src=x onerror=alert(1)>'>",
        "<img src='data:image/svg+xml,<svg onload=alert(1)>'><p style='background:url(https://evil.example);position:fixed' onclick='alert(1)'>x</p>"
    })
    void removesExecutableMarkup(String attack) {
        String clean = RichText.clean(attack);
        var document = Jsoup.parseBodyFragment(clean);
        assertTrue(document.select("script, svg, math, iframe, style, [onerror], [onload], [onclick], [srcdoc]").isEmpty());
        assertFalse(clean.contains("javascript:"));
        assertFalse(clean.contains("data:image"));
        assertFalse(clean.contains("position:"));
        assertEquals(clean, RichText.clean(clean));
    }

    @Test void retainsSupportedEditorFormatting() {
        String clean = RichText.clean("<h2 class='other ql-align-center' style='color:#aabbcc; font-weight:bold; font-style:italic; text-decoration:underline; text-align:center'>标题</h2><ul><li data-list='bullet'>正文</li></ul><a href='https://example.com'>链接</a><img src='https://example.com/a.png'>");
        assertTrue(clean.contains("ql-align-center"));
        assertTrue(clean.contains("color:#aabbcc"));
        assertTrue(clean.contains("data-list=\"bullet\""));
        assertTrue(clean.contains("https://example.com/a.png"));
        assertTrue(clean.contains("noopener noreferrer"));
        assertFalse(clean.contains("other"));
        assertFalse(RichText.clean("<li data-list='arbitrary'>x</li>").contains("data-list"));
        assertNull(RichText.clean(null));
        assertEquals("", RichText.clean(""));
    }

    @Test void sanitizesJsonWritesAndHistoricalMyBatisReads() throws Exception {
        WorkModel work = new ObjectMapper().readValue("{\"content\":\"<p onclick='x()'>作品</p>\"}", WorkModel.class);
        assertEquals("<p>作品</p>", work.getContent());
        NoticeModel notice = new NoticeModel();
        SystemMetaObject.forObject(notice).setValue("content", "<img src='https://example.com/x' onerror='x()'>");
        assertFalse(new ObjectMapper().writeValueAsString(notice).contains("onerror"));
    }
}
