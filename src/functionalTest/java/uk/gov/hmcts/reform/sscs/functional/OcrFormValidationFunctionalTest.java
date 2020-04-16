package uk.gov.hmcts.reform.sscs.functional;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import java.io.IOException;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.sscs.domain.validation.OcrValidationResponse;
import uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus;

@SpringBootTest
@TestPropertySource(locations = "classpath:application_e2e.yaml")
@RunWith(JUnitParamsRunner.class)
@Slf4j
public class OcrFormValidationFunctionalTest extends BaseFunctionalTest {

    @Test
    @Parameters({"SSCS1", "SSCS1PE"})
    public void should_validate_ocr_data_and_return_success(String formType) throws IOException {
        String json = getJson("validation/valid-ocr-form-data.json");
        Response response = validateOcrEndpointRequest(json, formType);

        assertThat(response.getStatusCode()).isEqualTo(200);

        OcrValidationResponse validationResponse = response.getBody()
            .as(OcrValidationResponse.class, ObjectMapperType.JACKSON_2);

        assertThat(validationResponse.status).isEqualTo(ValidationStatus.SUCCESS);
        assertThat(validationResponse.errors).isNull();
        assertThat(validationResponse.warnings).isNull();
    }
}
