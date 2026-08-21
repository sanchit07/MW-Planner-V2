package com.mw.planner.service.storage.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.mw.planner.config.PlannerS3Properties;
import com.mw.planner.exception.storage.StorageFileNotFoundException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.*;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceImplTest {

  @Mock private S3Client s3Client;
  @Mock private PlannerS3Properties plannerS3Properties;

  @InjectMocks private S3StorageServiceImpl s3StorageService;

  @BeforeEach
  void setUp() {
    lenient().when(plannerS3Properties.getBucketName()).thenReturn("test-bucket");
    lenient().when(plannerS3Properties.getParentDirectory()).thenReturn("uploads");
  }

  @Test
  void uploadFile_WithFolder_AppendsFolderToParentDirectory() throws Exception {
    S3Utilities mockUtilities = mock(S3Utilities.class);
    when(s3Client.utilities()).thenReturn(mockUtilities);
    when(mockUtilities.getUrl(any(GetUrlRequest.class)))
        .thenReturn(new URL("https://test-bucket.s3.amazonaws.com/base/custom/uuid.txt"));

    MultipartFile file =
        new MockMultipartFile(
            "file", "test.txt", "text/plain", "content".getBytes(StandardCharsets.UTF_8));
    when(plannerS3Properties.getParentDirectory()).thenReturn("base");

    String url = s3StorageService.uploadFile(file, "custom");

    assertThat(url).isNotNull();
    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
    assertThat(captor.getValue().key()).startsWith("base/custom/");
    assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
  }

  @Test
  void uploadFile_WithNullFolder_UsesParentDirectoryOnly() throws Exception {
    S3Utilities mockUtilities = mock(S3Utilities.class);
    when(s3Client.utilities()).thenReturn(mockUtilities);
    when(mockUtilities.getUrl(any(GetUrlRequest.class)))
        .thenReturn(new URL("https://test-bucket.s3.amazonaws.com/uploads/uuid.txt"));

    MultipartFile file =
        new MockMultipartFile(
            "file", "test.txt", "text/plain", "content".getBytes(StandardCharsets.UTF_8));

    String url = s3StorageService.uploadFile(file, null);

    assertThat(url).isNotNull();
    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
    assertThat(captor.getValue().key()).startsWith("uploads/");
  }

  @Test
  void downloadFile_WhenKeyExists_ReturnsBytes() {
    String fileUrl = "https://test-bucket.s3.us-east-1.amazonaws.com/uploads/file.txt";
    byte[] content = "file content".getBytes(StandardCharsets.UTF_8);
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));

    byte[] result = s3StorageService.downloadFile(fileUrl);

    assertThat(result).isEqualTo(content);
  }

  @Test
  void downloadFile_WhenKeyNotFound_ThrowsStorageFileNotFoundException() {
    String fileUrl = "https://test-bucket.s3.us-east-1.amazonaws.com/uploads/missing.txt";
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("Not found").build());

    assertThatThrownBy(() -> s3StorageService.downloadFile(fileUrl))
        .isInstanceOf(StorageFileNotFoundException.class)
        .hasMessageContaining("missing.txt");
  }

  @Test
  void fileExists_WhenKeyExists_ReturnsTrue() {
    String fileUrl = "https://test-bucket.s3.us-east-1.amazonaws.com/uploads/file.txt";
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().build());

    boolean result = s3StorageService.fileExists(fileUrl);

    assertThat(result).isTrue();
  }

  @Test
  void fileExists_WhenKeyNotFound_ReturnsFalse() {
    String fileUrl = "https://test-bucket.s3.us-east-1.amazonaws.com/uploads/missing.txt";
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("Not found").build());

    boolean result = s3StorageService.fileExists(fileUrl);

    assertThat(result).isFalse();
  }

  @Test
  void deleteFile_DeletesObject() {
    String fileUrl = "https://test-bucket.s3.us-east-1.amazonaws.com/uploads/file.txt";

    s3StorageService.deleteFile(fileUrl);

    ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3Client).deleteObject(captor.capture());
    assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
    assertThat(captor.getValue().key()).isEqualTo("uploads/file.txt");
  }
}
