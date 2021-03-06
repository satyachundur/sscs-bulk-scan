package uk.gov.hmcts.reform.sscs.domain.validation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Data;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.OcrDataField;

@Data
public class OcrDataValidationRequest {

    @ApiModelProperty(value = "List of ocr data fields to be validated.", required = true)
    @NotEmpty
    private final List<OcrDataField> ocrDataFields;

    public OcrDataValidationRequest(
        @JsonProperty("ocr_data_fields") List<OcrDataField> ocrDataFields
    ) {
        this.ocrDataFields = ocrDataFields;
    }
}
