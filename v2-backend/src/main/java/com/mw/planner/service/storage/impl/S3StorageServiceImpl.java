package com.mw.planner.service.storage.impl;

import com.mw.planner.config.PlannerS3Properties;
import com.mw.planner.exception.storage.StorageDeleteFailedException;
import com.mw.planner.exception.storage.StorageDownloadFailedException;
import com.mw.planner.exception.storage.StorageFileNotFoundException;
import com.mw.planner.exception.storage.StorageUploadFailedException;
import com.mw.planner.service.storage.CloudStorageService;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageServiceImpl implements CloudStorageService {

  private final S3Client s3Client;
  private final PlannerS3Properties plannerS3Properties;

  @Override
  public String uploadFile(MultipartFile file, String folder) {
    try {
      // Respect parent directory
      if (folder == null) {
        folder = plannerS3Properties.getParentDirectory();
      } else {
        folder = plannerS3Properties.getParentDirectory() + "/" + folder;
      }
      String fileName = generateFileName(file.getOriginalFilename());
      String bucket = plannerS3Properties.getBucketName();
      String key = folder + "/" + fileName;

      PutObjectRequest putObjectRequest =
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .contentType(file.getContentType())
              .contentLength(file.getSize())
              .build();

      s3Client.putObject(
          putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

      String fileUrl = getObjectUrl(bucket, key);
      log.info("File uploaded successfully to S3: {}", fileUrl);
      return fileUrl;

    } catch (IOException e) {
      log.error("Error uploading file to S3", e);
      throw new StorageUploadFailedException("Failed to upload file to S3", e);
    } catch (S3Exception e) {
      log.error("S3 error while uploading file", e);
      throw new StorageUploadFailedException("S3 error: " + e.getMessage(), e);
    }
  }

  @Override
  public byte[] downloadFile(String fileUrl) {
    try {
      String s3Key = extractKeyFromUrl(fileUrl);
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder().bucket(plannerS3Properties.getBucketName()).key(s3Key).build();

      return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();

    } catch (NoSuchKeyException e) {
      log.error("File not found in S3: {}", fileUrl);
      throw new StorageFileNotFoundException("File not found: " + fileUrl);
    } catch (Exception e) {
      log.error("Failed to download file from S3: {}", e.getMessage(), e);
      throw new StorageDownloadFailedException("Failed to download file from S3", e);
    }
  }

  @Override
  public boolean fileExists(String fileUrl) {
    try {
      String s3Key = extractKeyFromUrl(fileUrl);
      HeadObjectRequest headObjectRequest =
          HeadObjectRequest.builder()
              .bucket(plannerS3Properties.getBucketName())
              .key(s3Key)
              .build();

      s3Client.headObject(headObjectRequest);
      return true;

    } catch (NoSuchKeyException e) {
      return false;
    } catch (Exception e) {
      log.error("Error checking file existence in S3: {}", e.getMessage(), e);
      return false;
    }
  }

  @Override
  public void deleteFile(String fileUrl) {
    try {
      String key = extractKeyFromUrl(fileUrl);
      DeleteObjectRequest deleteObjectRequest =
          DeleteObjectRequest.builder()
              .bucket(plannerS3Properties.getBucketName())
              .key(key)
              .build();

      s3Client.deleteObject(deleteObjectRequest);
      log.info("File deleted successfully from S3: {}", fileUrl);

    } catch (Exception e) {
      log.error("S3 error while deleting file", e);
      throw new StorageDeleteFailedException("S3 error: " + e.getMessage(), e);
    }
  }

  private String generateFileName(String originalFileName) {
    String extension = "";
    if (originalFileName != null && originalFileName.contains(".")) {
      extension = originalFileName.substring(originalFileName.lastIndexOf("."));
    }
    return UUID.randomUUID() + extension;
  }

  /**
   * Extract the object key from a full S3/MinIO file URL.
   *
   * <p>Supports both URL formats: 1. Path-style (MinIO/local):
   * http://localhost:9000/bucket-name/folder/file.png 2. Virtual-hosted style (AWS S3):
   * https://bucket-name.s3.region.amazonaws.com/folder/file.png
   *
   * @param fileUrl full file URL from S3 or MinIO
   * @return object key (e.g., "folder/file.png")
   */
  private String extractKeyFromUrl(String fileUrl) {
    try {
      URI uri = URI.create(fileUrl);
      String path = uri.getPath();
      String bucketName = plannerS3Properties.getBucketName();

      // Handle path-style URLs (MinIO with pathStyleAccessEnabled=true)
      // Format: http://endpoint:port/bucket-name/key
      // Example: http://localhost:9000/my-bucket/logos/file.png
      // Path will be: /my-bucket/logos/file.png
      if (path.startsWith("/" + bucketName + "/")) {
        String key = path.substring(("/" + bucketName + "/").length());
        log.debug("Extracted key '{}' from path-style URL: {}", key, fileUrl);
        return key;
      }

      // Handle virtual-hosted style URLs (AWS S3 with pathStyleAccessEnabled=false)
      // Format: https://bucket-name.s3.region.amazonaws.com/key
      // Example: https://my-bucket.s3.us-east-1.amazonaws.com/logos/file.png
      // Path will be: /logos/file.png
      if (path.startsWith("/")) {
        String key = path.substring(1);
        log.debug("Extracted key '{}' from virtual-hosted style URL: {}", key, fileUrl);
        return key;
      }

      // Fallback: return the path as-is if it doesn't match expected patterns
      log.warn("URL format not recognized, returning path as-is: {}", fileUrl);
      return path;
    } catch (Exception e) {
      log.error("Error extracting key from URL: {}", fileUrl, e);
      throw new RuntimeException("Failed to extract key from URL: " + fileUrl, e);
    }
  }

  /**
   * @param bucketName s3 bucket name
   * @param key file with directory
   * @return s3 accessible url
   */
  private String getObjectUrl(String bucketName, String key) {
    return s3Client
        .utilities()
        .getUrl(GetUrlRequest.builder().bucket(bucketName).key(key).build())
        .toString();
  }
}
