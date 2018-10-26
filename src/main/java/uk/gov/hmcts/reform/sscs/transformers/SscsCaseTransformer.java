package uk.gov.hmcts.reform.sscs.transformers;

import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.PERSON1_VALUE;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.PERSON2_VALUE;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.REPRESENTATIVE_VALUE;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseTransformationResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;

@Component
public class SscsCaseTransformer implements CaseTransformer {

    @Autowired
    private SscsJsonExtractor sscsJsonExtractor;

    private List<String> errors;

    @Override
    public CaseTransformationResponse transformExceptionRecordToCase(Map<String, Object> caseData) {

        Map<String, Object> transformed = new HashMap<>();
        errors = new ArrayList<>();

        Map<String, Object> pairs = sscsJsonExtractor.extractJson(caseData);
        Appeal appeal = buildAppealFromData(pairs);
        transformed.put("appeal", appeal);

        return CaseTransformationResponse.builder().transformedCase(transformed).errors(errors).build();
    }

    private Appeal buildAppealFromData(Map<String, Object> pairs) {

        Appellant appellant = null;

        if (hasPerson(pairs, PERSON2_VALUE)) {
            Appointee appointee = null;
            if (hasPerson(pairs, PERSON1_VALUE)) {
                appointee = Appointee.builder()
                    .name(buildPersonName(pairs, PERSON1_VALUE))
                    .address(buildPersonAddress(pairs, PERSON1_VALUE))
                    .contact(buildPersonContact(pairs, PERSON1_VALUE))
                    .identity(buildPersonIdentity(pairs, PERSON1_VALUE))
                .build();
            }
            appellant = Appellant.builder()
                .name(buildPersonName(pairs, PERSON2_VALUE))
                .address(buildPersonAddress(pairs, PERSON2_VALUE))
                .identity(buildPersonIdentity(pairs, PERSON2_VALUE))
                .appointee(appointee)
                .build();
        } else if (hasPerson(pairs, PERSON1_VALUE)) {
            appellant = Appellant.builder()
                .name(buildPersonName(pairs, PERSON1_VALUE))
                .address(buildPersonAddress(pairs, PERSON1_VALUE))
                .contact(buildPersonContact(pairs, PERSON1_VALUE))
                .identity(buildPersonIdentity(pairs, PERSON1_VALUE))
                .build();
        }

        return Appeal.builder()
            .benefitType(BenefitType.builder().code(getField(pairs, "benefit_type_description")).build())
            .appellant(appellant)
            .rep(buildRepresentative(pairs))
            .mrnDetails(buildMrnDetails(pairs))
            .hearingType(findHearingType(pairs))
            .hearingOptions(buildHearingOptions(pairs))
            .signer(getField(pairs,"signature_appellant_name"))
        .build();
    }

    private Representative buildRepresentative(Map<String, Object> pairs) {
        boolean doesRepExist = hasPerson(pairs, REPRESENTATIVE_VALUE);

        if (doesRepExist) {
            return Representative.builder()
                .hasRepresentative(convertBooleanToYesNoString(doesRepExist))
                .name(buildPersonName(pairs, REPRESENTATIVE_VALUE))
                .address(buildPersonAddress(pairs, REPRESENTATIVE_VALUE))
                .organisation(getField(pairs,"representative_company"))
                .contact(buildPersonContact(pairs, REPRESENTATIVE_VALUE))
                .build();
        } else {
            return Representative.builder().hasRepresentative(convertBooleanToYesNoString(doesRepExist)).build();
        }
    }

    private MrnDetails buildMrnDetails(Map<String, Object> pairs) {

        return MrnDetails.builder()
            .mrnLateReason(getField(pairs,"appeal_late_reason"))
        .build();
    }

    private Name buildPersonName(Map<String, Object> pairs, String personType) {
        return Name.builder()
            .title(getField(pairs,personType + "_title"))
            .firstName(getField(pairs,personType + "_first_name"))
            .lastName(getField(pairs,personType + "_last_name"))
        .build();
    }

    private Address buildPersonAddress(Map<String, Object> pairs, String personType) {
        return Address.builder()
            .line1(getField(pairs,personType + "_address_line1"))
            .line2(getField(pairs,personType + "_address_line2"))
            .town(getField(pairs,personType + "_address_line3"))
            .county(getField(pairs,personType + "_address_line4"))
            .postcode(getField(pairs,personType + "_postcode"))
        .build();
    }

    private Identity buildPersonIdentity(Map<String, Object> pairs, String personType) {
        return Identity.builder()
            .dob(generateDateForCcd(pairs, errors,personType + "_date_of_birth"))
            .nino(getField(pairs,personType + "_ni_number"))
        .build();
    }

    private Contact buildPersonContact(Map<String, Object> pairs, String personType) {
        return Contact.builder()
            .phone(getField(pairs,personType + "_phone"))
            .mobile(getField(pairs,personType + "_mobile"))
        .build();
    }

    private String findHearingType(Map<String, Object> pairs) {
        if (areBooleansValid(pairs, errors, "is_hearing_type_oral", "is_hearing_type_paper") && !doBooleansContradict(pairs, errors, "is_hearing_type_oral", "is_hearing_type_paper")) {
            return (boolean) pairs.get("is_hearing_type_oral") ? "Oral" : "Paper";
        }
        return null;
    }

    private HearingOptions buildHearingOptions(Map<String, Object> pairs) {

        String isLanguageInterpreterRequired = convertBooleanToYesNoString(findBooleanExists(getField(pairs,"hearing_options_language")));

        //TODO: Handle sign languages here - discuss with Josh
        return HearingOptions.builder()
            .excludeDates(buildExcludedDates(pairs))
            .arrangements(buildArrangements(pairs))
            .languageInterpreter(isLanguageInterpreterRequired)
            .languages(getField(pairs,"hearing_options_language"))
        .build();
    }

    private List<ExcludeDate> buildExcludedDates(Map<String, Object> pairs) {
        //TODO: Create story to properly implement this

        if (pairs.containsKey("hearing_options_exclude_dates")) {
            List<ExcludeDate> excludeDates = new ArrayList<>();

            excludeDates.add(ExcludeDate.builder().value(DateRange.builder().start(getField(pairs,"hearing_options_exclude_dates")).build()).build());
            return excludeDates;
        } else {
            return null;
        }
    }

    private List<String> buildArrangements(Map<String, Object> pairs) {
        // TODO: Create story to properly handle arrangements

        if (pairs.containsKey("hearing_support_arrangements")) {
            List<String> arrangements = new ArrayList<>();

            arrangements.add(getField(pairs,"hearing_support_arrangements"));
            return arrangements;
        } else {
            return null;
        }
    }
}
