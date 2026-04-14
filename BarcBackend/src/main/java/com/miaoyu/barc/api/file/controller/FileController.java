package com.miaoyu.barc.api.file.controller;

import com.miaoyu.barc.api.file.service.FileService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/file")
public class FileController {
    @Autowired
    private FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<J> uploadFileControl(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String path
        ) {
        return fileService.uploadFileService(request.getAttribute("uuid").toString(), file, path, null);
    }
    @PostMapping("/delete")
    public ResponseEntity<J> deleteFileControl(
            HttpServletRequest request,
            @RequestParam("path") String path,
            @RequestParam("filename") String filename
    ) {
        return fileService.forceDeleteFileService(request.getAttribute("uuid").toString(), path, filename);
    }
}
