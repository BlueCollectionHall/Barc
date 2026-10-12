package com.miaoyu.barc.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/** 作品与公告共享的富文本边界：按 HTML 语法清洗，历史读取与新写入遵循同一规则。 */
public final class RichText {
    private static final Safelist SAFE = Safelist.relaxed()
            .addTags("s", "del", "span", "hr")
            .addAttributes(":all", "class", "style")
            .addAttributes("li", "data-list")
            .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");

    private RichText() { }

    /** 只保留文本排版、HTTP(S) 图片和安全链接；不允许脚本、SVG、iframe、事件或任意 CSS。 */
    public static String clean(String html) {
        if (html == null || html.isEmpty()) return html;
        Document document = new Cleaner(SAFE).clean(Jsoup.parseBodyFragment(html));
        document.outputSettings().prettyPrint(false);
        for (Element element : document.body().getAllElements()) {
            String classes = Arrays.stream(element.className().split("\\s+"))
                    .filter(value -> value.matches("ql-(align-(center|right|justify)|indent-[1-8]|size-(small|large|huge)|font-(serif|monospace)|direction-rtl|syntax)"))
                    .collect(Collectors.joining(" "));
            if (classes.isEmpty()) element.removeAttr("class"); else element.attr("class", classes);
            String styles = Arrays.stream(element.attr("style").split(";"))
                    .map(String::trim).filter(RichText::safeStyle).collect(Collectors.joining("; "));
            if (styles.isEmpty()) element.removeAttr("style"); else element.attr("style", styles);
            if (element.hasAttr("data-list") && !element.attr("data-list").matches("bullet|ordered|checked|unchecked")) {
                element.removeAttr("data-list");
            }
        }
        return document.body().html();
    }

    /** CSS 只允许无 URL/函数执行能力的排版值；该正则用于属性值，不承担 HTML 解析。 */
    private static boolean safeStyle(String declaration) {
        String value = declaration.toLowerCase(Locale.ROOT);
        return value.matches("(color|background-color)\\s*:\\s*(#[0-9a-f]{3,8}|[a-z]{1,20}|rgba?\\([0-9.,% ]{1,40}\\))")
                || value.matches("text-align\\s*:\\s*(left|right|center|justify)")
                || value.matches("font-weight\\s*:\\s*(normal|bold|[1-9]00)")
                || value.matches("font-style\\s*:\\s*(normal|italic)")
                || value.matches("text-decoration\\s*:\\s*(none|underline|line-through)");
    }
}
