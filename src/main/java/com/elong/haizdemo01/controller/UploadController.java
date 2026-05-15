package com.elong.haizdemo01.controller;
//负责上传知识库文件

import com.elong.haizdemo01.service.DocumentService;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/document")
public class UploadController {

    private final DocumentService documentService;

    // 构造器注入（@Lazy：向量库未就绪时不阻塞应用启动）
    public UploadController(@Lazy DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 上传 txt 文件
     */
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) {

        try {
            // 调用 service 层
            return documentService.upload(file);

        } catch (Exception e) {

            // 打印错误
            e.printStackTrace();

            return "上传失败：" + e.getMessage();
        }
    }
}
