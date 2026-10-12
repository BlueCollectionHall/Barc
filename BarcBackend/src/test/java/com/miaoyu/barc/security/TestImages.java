package com.miaoyu.barc.security;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

/** 使用可解码的小位图验证文件内容识别，不能用任意几个字节冒充图片。 */
public final class TestImages {
    private TestImages() { }
    public static byte[] png() { return bytes("png"); }
    public static byte[] jpeg() { return bytes("jpeg"); }
    private static byte[] bytes(String type) {
        try {
            var output = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), type, output);
            return output.toByteArray();
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
}
