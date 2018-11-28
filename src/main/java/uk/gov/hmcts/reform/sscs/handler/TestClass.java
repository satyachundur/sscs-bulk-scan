package uk.gov.hmcts.reform.sscs.handler;

import java.net.URI;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.document.DocumentUploadClientApi;
import uk.gov.hmcts.reform.document.domain.Document;
import uk.gov.hmcts.reform.document.domain.UploadResponse;
import uk.gov.hmcts.reform.sscs.document.EvidenceDownloadClientApi;
import uk.gov.hmcts.reform.sscs.document.EvidenceMetadataDownloadClientApi;
import uk.gov.hmcts.reform.sscs.exception.UnsupportedDocumentTypeException;

@Service
@Slf4j
public class TestClass {

    public static final String S2S_TOKEN = "oauth2Token";

    public static final String DS_USER_ID = "sscs";

    private final AuthTokenGenerator authTokenGenerator;
    private final DocumentUploadClientApi documentUploadClientApi;
    private final EvidenceDownloadClientApi evidenceDownloadClientApi;
    private final EvidenceMetadataDownloadClientApi evidenceMetadataDownloadClient;

    @Autowired
    public TestClass(
        AuthTokenGenerator authTokenGenerator,
        DocumentUploadClientApi documentUploadClientApi,
        EvidenceDownloadClientApi evidenceDownloadClientApi,
        EvidenceMetadataDownloadClientApi evidenceMetadataDownloadClient
    ) {
        this.authTokenGenerator = authTokenGenerator;
        this.documentUploadClientApi = documentUploadClientApi;
        this.evidenceDownloadClientApi = evidenceDownloadClientApi;
        this.evidenceMetadataDownloadClient = evidenceMetadataDownloadClient;
    }

    public UploadResponse upload(List<MultipartFile> files) {

        String serviceAuthorization = authTokenGenerator.generate();

        try {
            return documentUploadClientApi
                .upload(S2S_TOKEN, serviceAuthorization, DS_USER_ID, files);
        } catch (HttpClientErrorException httpClientErrorException) {
            log.error("Doc Store service failed to upload documents...", httpClientErrorException);
            if (null != files) {
                logFiles(files);
            }
            throw new UnsupportedDocumentTypeException(httpClientErrorException);
        }
    }

    public byte[] download(URI documentSelf) {
        String serviceAuthorization = authTokenGenerator.generate();

        try {
            Document documentMetadata = evidenceMetadataDownloadClient.getDocumentMetadata(
                S2S_TOKEN,
                serviceAuthorization,
                DS_USER_ID,
                documentSelf.getPath().replaceFirst("/", "")
            );

            ResponseEntity<Resource> responseEntity =  evidenceDownloadClientApi.downloadBinary(
                S2S_TOKEN,
                serviceAuthorization,
                DS_USER_ID,
                URI.create(documentMetadata.links.binary.href).getPath()
            );

            ByteArrayResource resource = (ByteArrayResource) responseEntity.getBody();
            return resource.getByteArray();
        } catch (HttpClientErrorException httpClientErrorException) {
            log.error("Doc Store service failed to download document...", httpClientErrorException);
            throw new UnsupportedDocumentTypeException(httpClientErrorException);
        }
    }

    private void logFiles(List<MultipartFile> files) {
        files.forEach(file -> {
            log.info("Name: {}", file.getName());
            log.info("OriginalName {}", file.getOriginalFilename());
        });
    }

}

