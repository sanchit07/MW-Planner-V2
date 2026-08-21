package com.mw.planner.service.storage;

import org.springframework.web.multipart.MultipartFile;

public interface CloudStorageService {

  String uploadFile(MultipartFile file, String folder);

  void deleteFile(String fileUrl);

  byte[] downloadFile(String fileUrl);

  boolean fileExists(String fileUrl);
}
