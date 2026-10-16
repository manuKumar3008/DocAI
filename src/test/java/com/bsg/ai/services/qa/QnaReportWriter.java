package com.bsg.ai.services.qa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Slf4j
public class QnaReportWriter {

    private static final String TEXT_FILE_NAME = "qna-test-report.txt";
    private static final String CSV_FILE_NAME = "qna-report.csv";
    public static void write(List<TestResult> results, boolean csvFormat) throws Exception {
        if (csvFormat) {
            writeCsv(results);
        } else {
            writeTxt(results);
        }
    }

    // ---------------------------------------------------------
    // TXT
    // ---------------------------------------------------------

    private static void writeTxt(List<TestResult> results) throws Exception {
        String fileName = LocalDateTime.now() + "-" + TEXT_FILE_NAME;
        Path path = Path.of("temp/" + fileName);

        StringBuilder sb = new StringBuilder();

        sb.append("QnA TEST REPORT\n");
        sb.append("====================================\n\n");

        for (TestResult r : results) {
            sb.append("Test: ").append(r.id()).append("\n");
            sb.append("Q   : ").append(r.question()).append("\n");
            sb.append("Exp : ").append(r.expected()).append("\n");
            sb.append("Act : ").append(trim(r.actual())).append("\n");
            sb.append("Res : ").append(r.passed() ? "PASS ✅" : "FAIL ❌").append("\n");
            sb.append("Score: ").append(r.score()).append("\n");
            sb.append("------------------------------------\n");
        }

        long pass = results.stream().filter(TestResult::passed).count();

        sb.append("\nSUMMARY\n");
        sb.append("Total : ").append(results.size()).append("\n");
        sb.append("Pass  : ").append(pass).append("\n");
        sb.append("Fail  : ").append(results.size() - pass).append("\n");

        Files.writeString(path, sb.toString());

        System.out.println("📄 TXT Report: " + path.toAbsolutePath());
    }

    // ---------------------------------------------------------
    // CSV (Excel)
    // ---------------------------------------------------------

    private static void writeCsv(List<TestResult> results) throws Exception {
        String fileName = LocalDateTime.now() + "-" + CSV_FILE_NAME;
        Path path = Path.of("temp/" + fileName);

        StringBuilder csv = new StringBuilder();
        csv.append("Id,Question,Expected,Actual,Result,score\n");

        for (TestResult r : results) {
            csv.append("\"").append(r.id()).append("\",")
                    .append("\"").append(r.question()).append("\",")
                    .append("\"").append(r.expected()).append("\",")
                    .append("\"").append(clean(r.actual())).append("\",")
                    .append(r.passed() ? "PASS" : "FAIL").append("\",")
                    .append("\"").append(r.score()).append("\"\n");
        }

        Files.writeString(path, csv.toString());

        System.out.println("📊 CSV Report: " + path.toAbsolutePath());
    }

    // ---------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------

    private static String trim(String text) {
        if (text == null) return "";
        return text.substring(0, Math.min(300, text.length()));
    }

    private static String clean(String text) {
        if (text == null) return "";
        return text.replace("\"", "'").replace("\n", " ");
    }
}
