package com.bsg.ai.services;

import com.bsg.ai.services.extraction.classification.service.ClassificationService;
import com.bsg.ai.services.extraction.filter.dto.SubjectsResponse;
import com.bsg.ai.services.extraction.filter.service.ExternalAPIService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
class DocClassificationTestsUpdated {

	@Autowired
	private ClassificationService classificationService;
	@MockitoBean
	private ExternalAPIService externalAPIService;

	static final List<ClassificationTestCase> CLASSIFICATION_TEST_CASES = new ArrayList<>();

    private static final LocalDateTime TEST_START_TIME;

	static {
        TEST_START_TIME = LocalDateTime.now();
		CLASSIFICATION_TEST_CASES.add(newCase("1",
				"static/summary/mistral_llm_test_docs/BEWERBUNG/ADP_Rademacher_Anschreiben_Johannes-Dietrich Rademacher-Schumann_BEWERBUNG_Anschreiben_01.01.1990.pdf",
				"BEWERBUNG", "Anschreiben", "hoch", "1990-10-01", "Johannes-Dietrich Rademacher-Schumann"));
		CLASSIFICATION_TEST_CASES.add(newCase("2",
				"static/summary/mistral_llm_test_docs/BEWERBUNG/AH_Bewerbung komplett_Alois Hinterhuber_BEWERBUNG_Bewerbungsunterlagen_01.07.1985.pdf",
				"BEWERBUNG", "Bewerbungsunterlagen", "hoch", "1985-07-01", "Alois Hinterhuber"));
		CLASSIFICATION_TEST_CASES.add(newCase("3",
				"static/summary/mistral_llm_test_docs/BEWERBUNG/Bewerbung IT Spezialist_Daniel Zimmermann_BEWERBUNG_Anschreiben_01.12.2018.pdf",
				"BEWERBUNG", "Anschreiben", "hoch", "2018-12-01", "Daniel Zimmermann"));
		CLASSIFICATION_TEST_CASES.add(newCase("4",
				"static/summary/mistral_llm_test_docs/BEWERBUNG/Bewerbungsunterlagen Organisationsprogrammierer_Franz Winter_BEWERBUNG_Bewerbungsunterlagen_24.04.2015.pdf",
				"BEWERBUNG", "Bewerbungsunterlagen", "hoch", "2015-04-24", "Franz Winter"));
		CLASSIFICATION_TEST_CASES.add(newCase("5",
				"static/summary/mistral_llm_test_docs/BEWERBUNG/Bewerbungsunterlagen Organisationsprogrammierer_Sarah Sommer_BEWERBUNG_Bewerbungsunterlagen_26.04.2015.pdf",
				"BEWERBUNG", "Bewerbungsunterlagen", "hoch", "2015-04-26", "Sarah Sommer"));
		CLASSIFICATION_TEST_CASES.add(newCase("6",
				"static/summary/mistral_llm_test_docs/BEWERBUNG/DZ_Bewerbung_Anschreiben_Daniel Zimmermann_BEWERBUNG_Bewerbungsunterlagen_17.07.2008.pdf",
				"BEWERBUNG", "Bewerbungsunterlagen", "hoch", "2008-07-14", "Daniel Zimmermann"));
		CLASSIFICATION_TEST_CASES.add(newCase("7",
				"static/summary/mistral_llm_test_docs/BEWERBUNG/DZ_Bewerbung_Daniela Zimmermann_BEWERBUNG_Bewerbungsunterlagen_14.07.2008.pdf",
				"BEWERBUNG", "Bewerbungsunterlagen", "hoch", "2008-07-14", "Daniela Zimmermann"));
		CLASSIFICATION_TEST_CASES.add(newCase("8",
				"static/summary/mistral_llm_test_docs/BEWERBUNG/HP_Bewerbungsunterlagen_Hans Probe_BEWERBUNG_Bewerbungsunterlagen_02.12.2014.pdf",
				"BEWERBUNG", "Bewerbungsunterlagen", "hoch", "2014-12-02", "Hans Probe"));
		CLASSIFICATION_TEST_CASES.add(newCase("9",
				"static/summary/mistral_llm_test_docs/BEWERBUNG/Laura_Bewerbungsunterlagen_Laura Lange_BEWERBUNG_Bewerbungsunterlagen_02.04.2012.pdf",
				"BEWERBUNG", "Bewerbungsunterlagen", "hoch", "2012-04-02", "Laura Lange"));
		CLASSIFICATION_TEST_CASES.add(newCase("10",
				"static/summary/mistral_llm_test_docs/BEWERBUNG/LL_Bewerbungsunterlagen_Lara Lohmann_BEWERBUNG_Bewerbungsunterlagen_02.04.2015.pdf",
				"BEWERBUNG", "Bewerbungsunterlagen", "hoch", "2015-04-02", "Lara Lohmann"));
		// VERTRAG
		CLASSIFICATION_TEST_CASES.add(newCase("11",
				"static/summary/mistral_llm_test_docs/VERTRAG/Alois_Vertrag_Alois Hinterhuber_VERTRAG_Dienstvertrag_15.12.1985.pdf",
				"VERTRAG", "Dienstvertrag", "hoch", "1985-12-15", "Alois Hinterhuber"));
		CLASSIFICATION_TEST_CASES.add(newCase("12",
				"static/summary/mistral_llm_test_docs/VERTRAG/Daniel_Befristeter Traineevertrag_Daniel Zimmermann_VERTRAG_Befristeter Trainee Vertrag_01.01.2007.pdf",
				"VERTRAG", "Befristeter Trainee Vertrag", "hoch", "2007-01-01", "Daniel Zimmermann"));
		CLASSIFICATION_TEST_CASES.add(newCase("13",
				"static/summary/mistral_llm_test_docs/VERTRAG/Daniela_Anstellungsvertrag_Daniela Zimmermann_Vertrag_Anstellungsvertrag_01.01.2008.pdf",
				"VERTRAG", "Anstellungsvertrag", "hoch", "2008-01-01", "Daniela Zimmermann"));
		CLASSIFICATION_TEST_CASES.add(newCase("14",
				"static/summary/mistral_llm_test_docs/VERTRAG/Hans_Arbeitsvertrag_Hans Probe_VERTRAG_Anstellungsvertrag_08.05.2015.pdf",
				"VERTRAG", "Anstellungsvertrag", "hoch", "2015-05-08", "Hans Probe"));
		CLASSIFICATION_TEST_CASES.add(newCase("15",
				"static/summary/mistral_llm_test_docs/VERTRAG/Helmut_Arbeitsvertrag_Helmut Meier_VERTRAG_Anstellungsvertrag_20.08.2010.pdf",
				"VERTRAG", "Anstellungsvertrag", "hoch", "2010-08-20", "Helmut Meier"));
		CLASSIFICATION_TEST_CASES.add(newCase("16",
				"static/summary/mistral_llm_test_docs/VERTRAG/Lara_Arbeitsvertrag_Lara Lohmann_VERTRAG_Anstellungsvertrag_08.05.2015.pdf",
				"VERTRAG", "Anstellungsvertrag", "hoch", "2015-05-08", "Lara Lohmann"));
		CLASSIFICATION_TEST_CASES.add(newCase("17",
				"static/summary/mistral_llm_test_docs/VERTRAG/Laura_Arbeitsvertrag_Laura Lange_VERTRAG_Anstellungsvertrag_08.05.2012.pdf",
				"VERTRAG", "Anstellungsvertrag", "hoch", "2012-05-08", "Laura Lange"));

        // BS
        CLASSIFICATION_TEST_CASES.add(newCase("18",
                "static/summary/mistral_llm_test_docs/BS/Bescheinigung SAP HR Schulung_Helmut Meier_BS_Bescheinigung_10.10.2011.pdf",
                "BS", "Bescheinigung", "hoch", "2011-10-10", "Herr Helmut Meier"));
        CLASSIFICATION_TEST_CASES.add(newCase("19",
                "static/summary/mistral_llm_test_docs/BS/DZimmermann_Fahrerlaubnis_Daniel Zimmermann_BS_Bescheinigung_01.06.2025.pdf",
                "BS", "Bescheinigung", "hoch", "2025-06-01", "Daniel Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("20",
                "static/summary/mistral_llm_test_docs/BS/IHK Zertifikat_Helmut Meier_BS_Bescheinigung_09.10.2011.pdf",
                "BS", "Bescheinigung", "hoch", "2011-10-09", "Herr Helmut Meier"));
        CLASSIFICATION_TEST_CASES.add(newCase("21",
                "static/summary/mistral_llm_test_docs/BS/IHK Zertifikat_Laura Lange_BS_Bescheinigung_09.10.2013.pdf",
                "BS", "Bescheinigung", "hoch", "2013-10-09", "Frau Lange"));
        CLASSIFICATION_TEST_CASES.add(newCase("22",
                "static/summary/mistral_llm_test_docs/BS/Zeitmanagement_Alois Hinterhuber_BS_Bescheinigung_11.05.2014.pdf",
                "BS", "Bescheinigung", "hoch", "2014-05-11", "Alois Hinterhuber"));
        CLASSIFICATION_TEST_CASES.add(newCase("23",
                "static/summary/mistral_llm_test_docs/BS/Zeitmanagement_Daniela Zimmermann_BS_Bescheinigung_11.05.2014.pdf",
                "BS", "Bescheinigung", "hoch", "2014-05-11", "Daniela Zimmermann"));

        // DATZ
        CLASSIFICATION_TEST_CASES.add(newCase("24",
                "static/summary/mistral_llm_test_docs/DATZ/Datenschutz_Alois Hinterhuber_DATZ_Datenschutzerklärung_16.03.2006.pdf",
                "DATZ", "Datenschutzerklärung", "hoch", "2006-03-16", "Herr Alois Hinterhuber"));
        CLASSIFICATION_TEST_CASES.add(newCase("25",
                "static/summary/mistral_llm_test_docs/DATZ/Datenschutzerklärung_Daniela Zimmermann_DATZ_Datenschutzerklärung_16.03.2007.pdf",
                "DATZ", "Datenschutzerklärung", "hoch", "2007-03-16", "Frau Daniela Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("26",
                "static/summary/mistral_llm_test_docs/DATZ/Datenschutzerklärung_Daniel Zimmermann_DATZ_Datenschutzerklärung_16.03.2007.pdf",
                "DATZ", "Datenschutzerklärung", "hoch", "2007-03-16", "Daniel Zimmermann"));

        // JUBI
        CLASSIFICATION_TEST_CASES.add(newCase("27",
                "static/summary/mistral_llm_test_docs/JUBI/ADP_Rademacher_Jubiläum_Johannes-Dietrich Rademacher-Schumann_JUBI_Arbeitsjubiläum_01.01.2006.pdf",
                "JUBI", "Arbeitsjubiläum", "hoch", "2006-01-01", "Johannes-Dietrich Rademacher-Schumann"));
        CLASSIFICATION_TEST_CASES.add(newCase("28",
                "static/summary/mistral_llm_test_docs/JUBI/Arbeitsjubiläum_Daniela Zimmermann_JUBI_Arbeitsjubiläum_15.07.2006.pdf",
                "JUBI", "Arbeitsjubiläum", "hoch", "2006-07-15", "Frau Daniela Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("29",
                "static/summary/mistral_llm_test_docs/JUBI/Arbeitsjubiläum_Daniel Zimmermann_JUBI_Arbeitsjubiläum_15.07.2006.pdf",
                "JUBI", "Arbeitsjubiläum", "hoch", "2006-07-15", "Daniel Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("30",
                "static/summary/mistral_llm_test_docs/JUBI/Jubiläum_Alois Hinterhuber_JUBI_Arbeitsjubiläum_01.01.2006.pdf",
                "JUBI", "Arbeitsjubiläum", "hoch", "2006-01-01", "Alois Hinterhuber"));

        // NETK
        CLASSIFICATION_TEST_CASES.add(newCase("31",
                "static/summary/mistral_llm_test_docs/NETK/ADP_Rademacher_Nebenbeschäftigung_Johannes-Dietrich Rademacher-Schumann_NETK_genehmigung_23.02.2006.pdf",
                "NETK", "genehmigung", "hoch", "2006-02-23", "Johannes-Dietrich Rademacher-Schumann"));
        CLASSIFICATION_TEST_CASES.add(newCase("32",
                "static/summary/mistral_llm_test_docs/NETK/Nebentätigkeit_Alois Hinterhuber_NETK_Genehmigung_23.02.2006.pdf",
                "NETK", "genehmigung", "hoch", "2006-02-23", "Alois Hinterhuber"));
        CLASSIFICATION_TEST_CASES.add(newCase("33",
                "static/summary/mistral_llm_test_docs/NETK/Nebentätigkeit_Daniela Zimmermann_NETK_Genehmigung_10.07.2008.pdf",
                "NETK", "genehmigung", "hoch", "2008-07-10", "Daniela Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("34",
                "static/summary/mistral_llm_test_docs/NETK/Nebentätigkeit_Daniel Zimmermann_NETK_Genehmigung_10.07.2008.pdf",
                "NETK", "genehmigung", "hoch", "2008-07-10", "Daniel Zimmermann"));

        // SOZI
        CLASSIFICATION_TEST_CASES.add(newCase("35",
                "static/summary/mistral_llm_test_docs/SOZI/Sozialversicherungsausweis_Daniela Zimmermann_SOZI_Sozialversicherungsausweis_17.01.2007.pdf.pdf",
                "SOZI", "Sozialversicherungsausweis", "hoch", "2007-01-17", "Daniela Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("36",
                "static/summary/mistral_llm_test_docs/SOZI/Sozialversicherungsausweis_Daniel Zimmermann_SOZI_Sozialversicherungsausweis_17.01.2007.pdf",
                "SOZI", "Sozialversicherungsausweis", "hoch", "2007-01-17", "Daniel Zimmermann"));

        // STAM
        CLASSIFICATION_TEST_CASES.add(newCase("37",
                "static/summary/mistral_llm_test_docs/STAM/DZ_ARAGKrankenversichicherung_Daniel Zimmermann_STAM_Änderung Krankenkasse_07.06.2024.pdf",
                "STAM", "Änderung Krankenkasse", "hoch", "2024-06-07", "Daniel Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("38",
                "static/summary/mistral_llm_test_docs/STAM/DZ_Bankaenderung_Daniel Zimmermann_STAM_Änderung der Bankverbindung_09.09.2024.pdf",
                "STAM", "Änderung der Bankverbindung", "hoch", "2024-09-09", "Daniel Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("39",
                "static/summary/mistral_llm_test_docs/STAM/DZ_Meldebescheinigung_Daniel Zimmermann_STAM_Adressänderung_17.09.2024.pdf",
                "STAM", "Adressänderung", "hoch", "2024-09-17", "Daniel Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("40",
                "static/summary/mistral_llm_test_docs/STAM/DZ_Meldebescheinigung_mT_Daniel Zimmermann_STAM_Adressänderung_17.09.2024.pdf",
                "STAM", "Adressänderung", "hoch", "2024-09-17", "Daniel Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("41",
                "static/summary/mistral_llm_test_docs/STAM/DZ_Meldebescheinigung_Paul-Dessau_Daniel Zimmermann_STAM_Adressänderung_17.09.2024.pdf",
                "STAM", "Adressänderung", "hoch", "2024-09-17", "Daniel Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("42",
                "static/summary/mistral_llm_test_docs/STAM/DZ_Meldebescheinigung_Paul-Dessau_nW_Daniel Zimmermann_STAM_Adressänderung_17.09.2024.pdf",
                "STAM", "Adressänderung", "hoch", "2024-09-17", "Daniel Zimmermann"));

        // VERS
        CLASSIFICATION_TEST_CASES.add(newCase("43",
                "static/summary/mistral_llm_test_docs/VERS/ADP_Anhörung BR Versetzung_Johannes-Dietrich Rademacher-Schumann_VERS_Unterrichtung Betriebsrat_01.01.2005.pdf",
                "VERS", "Unterrichtung Betriebsrat", "hoch", "2005-01-01", "Johannes-Dietrich Rademacher-Schumann"));
        CLASSIFICATION_TEST_CASES.add(newCase("44",
                "static/summary/mistral_llm_test_docs/VERS/Versetzung_Alois Hinterhuber_VERS_Unterrichtung Betriebsrat_01.01.2005.pdf",
                "VERS", "Unterrichtung Betriebsrat", "hoch", "2005-01-01", "Alois Hinterhuber"));
        CLASSIFICATION_TEST_CASES.add(newCase("45",
                "static/summary/mistral_llm_test_docs/VERS/Versetzung_Daniela Zimmermann_VERS_Unterrichtung Betriebsrat_01.07.2008.pdf",
                "VERS", "Unterrichtung Betriebsrat", "hoch", "2008-07-01", "Daniela Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("46",
                "static/summary/mistral_llm_test_docs/VERS/Versetzung_Daniel Zimmermann_VERS_Unterrichtung Betriebsrat_01.07.2008.pdf",
                "VERS", "Unterrichtung Betriebsrat", "hoch", "2008-07-01", "Daniel Zimmermann"));
        //// ZEUGNIS
        CLASSIFICATION_TEST_CASES.add(newCase("47",
                "static/summary/mistral_llm_test_docs/ZEUGNIS/Arbeitszeugnis_Hans Probe_ZEUGNIS_Zeugnis_20.11.2014",
                "ZEUGNIS", "Zeugnis", "hoch", "2014-11-20", "Hans Probe"));
        CLASSIFICATION_TEST_CASES.add(newCase("48",
                "static/summary/mistral_llm_test_docs/ZEUGNIS/Daniela_Zertifikat_Daniela Zimmermann_ZEUGNIS_Zertifikat_09.07.2005.pdf",
                "ZEUGNIS", "Zertifikat", "hoch", "2005-07-09", "Daniela Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("49",
                "static/summary/mistral_llm_test_docs/ZEUGNIS/Daniela_Zeugnis_Bari_Daniela Zimmermann_ZEUGNIS_Zertifikat_30.07.2002.pdf",
                "ZEUGNIS", "Zertifikat", "hoch", "2002-07-30", "Daniela Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("50",
                "static/summary/mistral_llm_test_docs/ZEUGNIS/Daniela_Zeugnis_MMA_Daniela Zimmermann_ZEUGNIS_Zeugnis_31.08.2004.pdf",
                "ZEUGNIS", "Zeugnis", "hoch", "2004-08-31", "Daniela Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("51",
                "static/summary/mistral_llm_test_docs/ZEUGNIS/Daniel_Zertifikat_Hogeschool_Daniel Zimmermann_ZEUGNIS_Zertifikat_09.07.2005.pdf",
                "ZEUGNIS", "Zertifikat", "hoch", "2005-07-09", "Daniel Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("52",
                "static/summary/mistral_llm_test_docs/ZEUGNIS/Daniel_Zeugnis_Barri_Daniel Zimmermann_ZEUGNIS_Zertifikat_30.07.2002.pdf",
                "ZEUGNIS", "Zertifikat", "hoch", "2002-07-30", "Daniel Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("53",
                "static/summary/mistral_llm_test_docs/ZEUGNIS/DZ_Promotionsurkunde_HSBA_Daniel Zimmermann_ZEUGNIS_Zertifikat_18.09.2024.pdf",
                "ZEUGNIS", "Zertifikat", "hoch", "2024-09-18", "Daniel Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("54",
                "static/summary/mistral_llm_test_docs/ZEUGNIS/DZ_Zwischenzeugnis_Daniel Zimmermann_ZEUGNIS_Zeugnis_14.09.2024.pdf",
                "ZEUGNIS", "Zeugnis", "hoch", "2024-09-14", "Daniel Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("55",
                "static/summary/mistral_llm_test_docs/ZEUGNIS/Zertifikat_Hogeschool_Daniela Zimmermann_ZEUGNIS_Zertifikat_09.07.2005.pdf",
                "ZEUGNIS", "Zertifikat", "hoch", "2005-07-09", "Daniela Zimmermann"));
        CLASSIFICATION_TEST_CASES.add(newCase("56",
                "static/summary/mistral_llm_test_docs/ZEUGNIS/Zeugnis_MMA_Daniel Zimmermann_ZEUGNIS_Zeugnis_31.08.2004.pdf",
                "ZEUGNIS", "Zeugnis", "hoch", "2004-08-31", "Daniel Zimmermann"));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClassificationTestCases")
    void testClassificationProcess(ClassificationTestCase testCase) {
        when(externalAPIService.getSubjectsResponse()).thenReturn(MOCK_RESPONSE);
//        String resolvedPath = resolveResourcePath(testCase.getResourcePath());
			Resource resource = resolveResource(testCase.getResourcePath());
        byte[] pdfBytes = resource
                .getInputStream()
                .readAllBytes();
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                testCase.resourcePath,
                "application/pdf",
                pdfBytes
        );
			try {
                JsonNode result = classificationService.processResource(pdfFile, "test_"+testCase.docId);
                System.out.println("result:" + result);
                String actualMain = getValue(result, "klassifikation", "hauptkategorie", "value");
                String actualSub = getValue(result, "klassifikation", "unterkategorie", "value");
//                String actualConf = getValue(result, "klassifikation", "konfidenz_gesamt");
                String actualConf = getValue(result, "klassifikation", "konfidenz_gesamt");
//                double conf = actualConf != null ? Double.parseDouble(actualConf) : 0.0;
//                if (conf > 0.7) {
//                    actualConf = "hoch";
//                }
                String actualDate = getValue(result, "klassifikation", "dokument_datum", "value");
                String actualEntity = getValue(result, "klassifikation", "entitaet", "value");

                String mismatchDetails = buildMismatchDetails(testCase, actualMain, actualSub, actualConf, actualDate, actualEntity);
                boolean pass = mismatchDetails.isBlank();

                if (!pass) {
                    writeInFile(formatResultLine(testCase, "FAIL", mismatchDetails), "DocClassificationTests-testClassificationProcess-"+testCase.getDocId());
                }
                assertTrue(pass);
            }catch (Exception e) {
                Assertions.fail();
            }
    }

	private static ClassificationTestCase newCase(String id, String path, String main, String sub, String conf,
			String documentDate, String entity) {
		return ClassificationTestCase.builder().docId(id).resourcePath(path).expectedMainCategory(main)
				.expectedSubCategory(sub).expectedConfidence(conf).expectedDocumentDate(documentDate)
				.expectedEntity(entity).build();
	}

	@Data
	@Builder
	private static class ClassificationTestCase {
		private String docId;
		private String resourcePath;
		private String expectedMainCategory;
		private String expectedSubCategory;
		private String expectedConfidence;
		private String expectedDocumentDate;
		private String expectedEntity;
	}

	public static Stream<ClassificationTestCase> getClassificationTestCases() {
		return CLASSIFICATION_TEST_CASES.stream();
	}

	private static String resolveResourcePath(String resourcePath) {
		if (resourcePath == null) {
			return null;
		}
		if (resourcePath.startsWith("static/")) {
			return resourcePath.substring("static/".length());
		}
		return resourcePath;
	}

	private static Resource resolveResource(String resourcePath) {
		if (resourcePath == null || resourcePath.isBlank()) {
			return null;
		}
		Resource classpathResource = new ClassPathResource(resourcePath);
		if (classpathResource.exists()) {
			return classpathResource;
		}
		Path fsPath = Paths.get(resourcePath);
		if (Files.exists(fsPath)) {
			return new FileSystemResource(fsPath.toFile());
		}
		Path testResourcePath = Paths.get("src/test/resources").resolve(resourcePath);
		if (Files.exists(testResourcePath)) {
			return new FileSystemResource(testResourcePath.toFile());
		}
		return null;
	}

	private static String getValue(JsonNode node, String... path) {
		JsonNode current = node;
		for (String segment : path) {
			if (current == null) {
				return null;
			}
			current = current.get(segment);
		}
		return current == null || current.isNull() ? null : current.asText();
	}

	private static boolean matches(String expected, String actual) {
		if (expected == null && actual == null) {
			return true;
		}
		if (expected == null || actual == null) {
			return false;
		}
        if (expected.toLowerCase().contains(actual.toLowerCase()) || actual.toLowerCase().contains(expected.toLowerCase())) {
            return true;
        }
		return expected.trim().equalsIgnoreCase(actual.trim());
	}

	private static String formatResultLine(ClassificationTestCase testCase, String status, String details) {
		StringBuilder line = new StringBuilder();
		line.append("[").append(status).append("] ");
		line.append("id=").append(testCase.getDocId());
		line.append(", path=").append(testCase.getResourcePath());
		if (details != null && !details.isBlank()) {
			line.append(", ").append(details);
		}
		return line.toString();
	}

    private static String buildMismatchDetails(ClassificationTestCase testCase, String actualMain, String actualSub,
                                               String actualConf, String actualDate, String actualEntity) {
        StringBuilder mismatches = new StringBuilder();
        appendMismatch(mismatches, "main", testCase.getExpectedMainCategory(), actualMain);
        appendMismatch(mismatches, "sub", testCase.getExpectedSubCategory(), actualSub);
//        appendMismatch(mismatches, "conf", testCase.getExpectedConfidence(), actualConf);
        appendMismatch(mismatches, "date", testCase.getExpectedDocumentDate(), actualDate);
        appendMismatch(mismatches, "entity", testCase.getExpectedEntity(), actualEntity);
        return mismatches.toString();
    }

    private static void appendMismatch(StringBuilder mismatches, String fieldName, String expected, String actual) {
        if ("conf".equals(fieldName)) {
            if (matches("high", actual)) {
                return;
            }
        }
        if (matches(expected, actual)) {
            return;
        }
        if (!mismatches.isEmpty()) {
            mismatches.append(";\n");
        }
        mismatches.append(fieldName)
                .append(" expected=[")
                .append(expected)
                .append("] actual=[")
                .append(actual)
                .append("]");
    }

    private static void writeInFile(String text, String fileName) {
        String dirPath = "output/test-results-new/"+ TEST_START_TIME;
        try {
            Path dir = Paths.get(dirPath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, text.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("File written at: " + filePath.toAbsolutePath());
        } catch (IOException ignore) {}
    }

    private final SubjectsResponse MOCK_RESPONSE = SubjectsResponse.builder()
            .documentTypes(List.of(
                    "REV", "VERTRAG", "SOZI", "AODA", "BEWERBUNG", "NETK", "AN", "VERS", "BS", "JUBI", "STAM", "BV",
                    "ZEUGNIS", "VERD", "DATZ", "SV"))
            .subjects(List.of(
                    "Sonstiges", "Traineevertrag", "Befristeter Trainee Vertrag", "Sonstiges", "Dienstvertrag",
                    "Anstellungsvertrag", "Sozialversicherungsausweis", "AO-Datei", "Bewerbungsunterlagen", "Anschreiben",
                    "Antrag", "Genehmigung", "Lohnsteuerbescheinigung", "Unterrichtung Betriebsrat", "Bescheinigung",
                    "Arbeitsjubiläum", "ADDRESS_CHANGE", "NAME_CHANGE", "CHANGE_HEALTH_INSUR", "OTHER_NOTIFICATION",
                    "BANK_CHANGE", "SICK_LEAVE", "Betriebsordnung", "Betriebsvereinbarung", "Sonstiges", "Zertifikat",
                    "Zeugnis", "Verdienstabrechnung", "Datenschutzerklärung", "Bescheinigung zur Sozialversicherung"))
            .documentTypeWiseSubjects(List.of(
                    create("VERTRAG", List.of(
                            createKV("Sonstiges", "Other"),
                            createKV("Anstellungsvertrag", "Employment contract"),
                            createKV("Traineevertrag", "Trainee contract"),
                            createKV("Befristeter Trainee Vertrag", "Fixed-term trainee contract"),
                            createKV("Dienstvertrag", "Service contract"))),
                    create("BEWERBUNG", List.of(
                            createKV("Anschreiben", "Cover letter"),
                            createKV("Bewerbungsunterlagen", "Application documents"))),
                    create("DATZ", List.of(
                            createKV("Datenschutzerklärung", "Privacy policy")
                    )),
                    create("JUBI", List.of(
                            createKV("Arbeitsjubiläum", "Work anniversary")
                    )),
                    create("NETK", List.of(
                            createKV("Genehmigung", "Approval"),
                            createKV("Antrag", "Request")
                    )),
                    create("SOZI",  List.of(
                            createKV("Sozialversicherungsausweis", "Social security card")
                    )),
                    create("VERS", List.of(
                            createKV("Unterrichtung Betriebsrat", "Works council correspondence")
                    )),
                    create("ZEUGNIS", List.of(
                            createKV("Zertifikat", "Certificate"),
                            createKV("Zeugnis", "Reference")
                    )),
                    create("VERD", List.of(
                            createKV("Verdienstabrechnung", "Income statement")
                    )),
                    create("AODA", List.of(
                            createKV("AO-Datei", "AO file")
                    )),
                    create("SV", List.of(
                            createKV("Bescheinigung zur Sozialversicherung", "Social security certificate")
                    )),
                    create("AN",  List.of(
                            createKV("Lohnsteuerbescheinigung", "Income tax certificate")
                    )),
                    create("BS",   List.of(
                            createKV("Bescheinigung", "Certification")
                    )),
                    create("STAM", List.of(
                            createKV("NAME_CHANGE", "Name Change"),
                            createKV("ADDRESS_CHANGE", "Adress change"),
                            createKV("CHANGE_HEALTH_INSUR", "Change health insurance"),
                            createKV("OTHER_NOTIFICATION", "Other notifications"),
                            createKV("BANK_CHANGE", "Bank change"),
                            createKV("SICK_LEAVE", "Sick note")
                    )),
                    create("BV", List.of(
                            createKV("Betriebsordnung", "Operating regulations"),
                            createKV("Betriebsvereinbarung", "Company agreement"),
                            createKV("Sonstiges", "Other")
                    )),
                    create("REV",  List.of(
                            createKV("Sonstiges", "Other")
                    ))
                    ))
            .build();

    private static SubjectsResponse.DocumentTypeWiseSubjects create(String type, List<SubjectsResponse.SubjectKeyValue> kvs) {
        SubjectsResponse.DocumentTypeWiseSubjects obj = new SubjectsResponse.DocumentTypeWiseSubjects();
        obj.setDocumentType(type);
        obj.setSubjects(kvs);
        return obj;
    }

    private static SubjectsResponse.SubjectKeyValue createKV(String key, String value) {
        SubjectsResponse.SubjectKeyValue kv = new SubjectsResponse.SubjectKeyValue();
        kv.setKey(key);
        kv.setValue(value);
        return kv;
    }
}
