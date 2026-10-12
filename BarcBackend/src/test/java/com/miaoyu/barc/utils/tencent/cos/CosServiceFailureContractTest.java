package com.miaoyu.barc.utils.tencent.cos;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 锁定现有 COS 适配器的失败返回、签名顺序和复制前置条件，不改 client，不访问真实桶。 */
@ExtendWith(MockitoExtension.class)
class CosServiceFailureContractTest {
    @Mock CosClient clients;
    @Mock COSClient sdk;
    @Spy CosConfig config = new CosConfig();
    @InjectMocks CosService service;
    @BeforeEach void configure() { config.setImage(bucket("test-image")); config.setAvatar(bucket("test-avatar")); config.setTest(bucket("test-only")); config.setBucketName("test-fallback"); }
    @Test void signedUrlsUseRequestedBucketAndBatchKeepsInputOrder() throws Exception {
        when(clients.cosClient(any())).thenReturn(sdk);
        when(sdk.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenAnswer(call -> URI.create("https://example.test/" + ((GeneratePresignedUrlRequest) call.getArgument(0)).getKey()).toURL());
        var expiration = new Date(2000000000000L);
        assertEquals(List.of("https://example.test/b", "https://example.test/a"), service.generateBatchSignedUrl(List.of("b", "a"), expiration, CosBucketConfigEnum.image));
        service.generateSignedUrl("avatar", expiration, CosBucketConfigEnum.avatar); service.generateSignedUrl("test", expiration, CosBucketConfigEnum.test); service.generateSignedUrl("file", expiration, CosBucketConfigEnum.file);
        verify(sdk).generatePresignedUrl(argThat(request -> request.getBucketName().equals("test-avatar") && request.getExpiration().equals(expiration)));
        verify(sdk).generatePresignedUrl(argThat(request -> request.getBucketName().equals("test-only")));
        verify(sdk).generatePresignedUrl(argThat(request -> request.getBucketName().equals("test-fallback")));
    }
    @Test void emptyKeyAndBrokenFileDoNotStartUpload() throws Exception {
        var file = mock(MultipartFile.class); when(file.getInputStream()).thenThrow(new IOException("模拟读取失败"));
        assertEquals(1, service.uploadFileWithKey(file, "", CosBucketConfigEnum.image).getCode());
        assertEquals(1, service.uploadFileWithKey(file, "images/a", CosBucketConfigEnum.image).getCode());
        assertEquals(1, service.uploadFile(file, "images/", CosBucketConfigEnum.image).getCode()); verifyNoInteractions(clients, sdk);
    }
    @Test void sdkFailureReturnsFailureForUploadAndDelete() {
        when(clients.cosClient(CosBucketConfigEnum.image)).thenReturn(sdk); when(sdk.putObject(any(PutObjectRequest.class))).thenThrow(new IllegalStateException("模拟上传失败"));
        var file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        assertEquals(1, service.uploadFileWithKey(file, "images/a", CosBucketConfigEnum.image).getCode());
        assertEquals(1, service.deleteFile(null, CosBucketConfigEnum.image).getCode());
        doThrow(new IllegalStateException("模拟删除失败")).when(sdk).deleteObject("test-image", "images/a");
        assertNotEquals(0, service.deleteFile("images/a", CosBucketConfigEnum.image).getCode());
    }
    @Test void moveCopiesBeforeDeletingAndCopyFailureKeepsSource() {
        when(clients.cosClient(CosBucketConfigEnum.image)).thenReturn(sdk);
        service.moveFile("source", "target", CosBucketConfigEnum.image); var order = inOrder(sdk); order.verify(sdk).copyObject(any(CopyObjectRequest.class)); order.verify(sdk).deleteObject("test-image", "source");
        when(sdk.copyObject(any(CopyObjectRequest.class))).thenThrow(new IllegalStateException("模拟复制失败"));
        assertThrows(RuntimeException.class, () -> service.moveFile("other-source", "target", CosBucketConfigEnum.image)); verify(sdk, never()).deleteObject("test-image", "other-source");
    }
    private CosConfig.CosBucketPojo bucket(String name) { var value = new CosConfig.CosBucketPojo(); value.setBucketName(name); return value; }
}
