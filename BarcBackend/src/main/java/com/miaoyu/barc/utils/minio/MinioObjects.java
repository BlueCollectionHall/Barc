package com.miaoyu.barc.utils.minio;

import io.minio.*;
import io.minio.messages.Item;
import jakarta.annotation.Resource;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MinioObjects {
    @Resource
    private MinioClient minioClient;
    @Autowired
    private MinioBuckets minioBuckets;

    private static final Tika tika = new Tika();

    public String putObject(String uuid, String fileName, MultipartFile requestFile) {
        String lowUuid = uuid.toLowerCase().replace("-", "");
        if (fileName == null) {
            fileName = requestFile.getOriginalFilename();
        }
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(requestFile.getBytes());
            ObjectWriteResponse objectWriteResponse = minioClient.putObject(PutObjectArgs.builder()
                    .bucket(lowUuid)
                    .stream(inputStream, requestFile.getSize(), -1)
                    .contentType(tika.detect(requestFile.getInputStream()))
                    .object(fileName).build());
            if (objectWriteResponse == null){
                return null;
            }
            return "https://file.naigos.cn:52011/" + lowUuid + "/" + fileName;
        } catch (Exception e){
            return null;
        }
    }
    public List<Map<String, Object>> getObjectList(String uuid) {
        String lowUuid = uuid.toLowerCase().replace("-", "");
        if (!minioBuckets.isBucket(lowUuid)) {
            return null;
        }
        Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(lowUuid).build());
        List<Map<String, Object>> list = new ArrayList<>();
        results.forEach(itemResult -> {
            try {
                Item item = itemResult.get();
                Map<String, Object> map = new HashMap<>();
                map.put("url", "https://file.naigos.cn:52011/" + lowUuid + "/" + item.objectName());
                map.put("size", item.size());
//                map.put("size_text", new HumanFileSize().humanFileSize(item.size()));
                map.put("name", item.objectName());
                list.add(map);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        });
        return list;
    }
    public void deleteObject(String uuid, String objectName) {
        String lowUuid = uuid.toLowerCase().replace("-", "");
        if (!minioBuckets.isBucket(lowUuid)) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(lowUuid)
                    .object(objectName).build());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
