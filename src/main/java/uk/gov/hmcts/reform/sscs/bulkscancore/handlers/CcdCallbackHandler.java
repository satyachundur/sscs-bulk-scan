package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.HandlerResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.handler.SscsRoboticsHandler;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;

@Component
@Slf4j
public class CcdCallbackHandler {

    private static final Logger logger = getLogger(CcdCallbackHandler.class);

    private final CaseTransformer caseTransformer;

    private final CaseValidator caseValidator;

    private final CaseDataHandler caseDataHandler;

    private final SscsRoboticsHandler roboticsHandler;

    private final SscsDataHelper sscsDataHelper;

    public CcdCallbackHandler(
        CaseTransformer caseTransformer,
        CaseValidator caseValidator,
        CaseDataHandler caseDataHandler,
        SscsRoboticsHandler roboticsHandler,
        SscsDataHelper sscsDataHelper
    ) {
        this.caseTransformer = caseTransformer;
        this.caseValidator = caseValidator;
        this.caseDataHandler = caseDataHandler;
        this.roboticsHandler = roboticsHandler;
        this.sscsDataHelper = sscsDataHelper;
    }

    public CallbackResponse handle(
        ExceptionCaseData exceptionCaseData,
        Token token) {

        String exceptionRecordId = exceptionCaseData.getCaseDetails().getCaseId();

        logger.info("Processing callback for SSCS exception record id {}", exceptionRecordId);

        CaseResponse caseTransformationResponse = caseTransformer.transformExceptionRecordToCase(exceptionCaseData.getCaseDetails());
        AboutToStartOrSubmitCallbackResponse transformErrorResponse = checkForErrors(caseTransformationResponse, exceptionRecordId);

        if (transformErrorResponse != null) {
            return transformErrorResponse;
        }

        Map<String, Object> transformedCase = caseTransformationResponse.getTransformedCase();
        CaseResponse caseValidationResponse = caseValidator.validate(transformedCase);

        AboutToStartOrSubmitCallbackResponse validationErrorResponse = checkForErrors(caseValidationResponse, exceptionRecordId);

        if (validationErrorResponse != null) {
            return validationErrorResponse;
        } else {
            return update(caseValidationResponse, exceptionCaseData.isIgnoreWarnings(), token, exceptionRecordId, exceptionCaseData.getCaseDetails().getCaseData());
        }
    }

    public CallbackResponse handleValidationAndUpdate(SscsCaseData exceptionCaseData) {
        Map<String, Object> appealData = new HashMap<>();
        appealData.put("appeal", exceptionCaseData.getCaseDetails().getCaseData().getAppeal());
        appealData.put("sscsDocument", exceptionCaseData.getCaseDetails().getCaseData().getSscsDocument());
        appealData.put("evidencePresent", sscsDataHelper.hasEvidence(exceptionCaseData.getCaseDetails().getCaseData().getSscsDocument()));

        CaseResponse caseValidationResponse = caseValidator.validate(appealData);

        AboutToStartOrSubmitCallbackResponse validationErrorResponse = convertWarningsToErrors(caseValidationResponse, exceptionCaseData.getCaseDetails().getCaseId());

        if (validationErrorResponse != null) {
            return validationErrorResponse;
        } else {
            String eventId = sscsDataHelper.findEventToCreateCase(caseValidationResponse);

            roboticsHandler.handle(caseValidationResponse, Long.valueOf(exceptionCaseData.getCaseDetails().getCaseId()), eventId);

            return AboutToStartOrSubmitCallbackResponse.builder()
                .warnings(caseValidationResponse.getWarnings())
                .data(appealData).build();
        }
    }

    private CallbackResponse update(CaseResponse caseValidationResponse, Boolean isIgnoreWarnings, Token token, String exceptionRecordId, Map<String, Object> exceptionRecordData) {
        HandlerResponse handlerResponse = (HandlerResponse) caseDataHandler.handle(
            caseValidationResponse,
            isIgnoreWarnings,
            token,
            exceptionRecordId);

        if (handlerResponse != null) {
            exceptionRecordData.put("state", (handlerResponse.getState()));
            exceptionRecordData.put("caseReference", String.valueOf((handlerResponse.getCaseId())));
        }

        return AboutToStartOrSubmitCallbackResponse.builder()
            .warnings(caseValidationResponse.getWarnings())
            .data(exceptionRecordData).build();
    }

    private AboutToStartOrSubmitCallbackResponse checkForErrors(CaseResponse caseResponse,
                                                                String exceptionRecordId) {

        if (!ObjectUtils.isEmpty(caseResponse.getErrors())) {
            log.info("Errors found while transforming exception record id {}", exceptionRecordId);
            return AboutToStartOrSubmitCallbackResponse.builder()
                .errors(caseResponse.getErrors())
                .build();
        }
        return null;
    }

    private AboutToStartOrSubmitCallbackResponse convertWarningsToErrors(CaseResponse caseResponse,
                                                                String exceptionRecordId) {

        if (!ObjectUtils.isEmpty(caseResponse.getWarnings())) {
            log.info("Errors found while transforming exception record id {}", exceptionRecordId);

            List<String> appendedWarningsAndErrors = new ArrayList<>(caseResponse.getErrors());
            appendedWarningsAndErrors.addAll(caseResponse.getWarnings());

            return AboutToStartOrSubmitCallbackResponse.builder()
                .errors(appendedWarningsAndErrors)
                .build();
        }
        return null;
    }
}
