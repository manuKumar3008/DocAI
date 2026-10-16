package com.bsg.ai.services.qa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class QnaTestDataLoader {
    public static Stream<TestCase> load(String path) {
        try {
            if (!StringUtils.hasText(path)) {
                return Stream.empty();
            }
            String fileName = System.getProperty("testFile", path);

            InputStream is = QnaTestDataLoader.class
                    .getClassLoader()
                    .getResourceAsStream(fileName);

            if (is == null) {
                throw new RuntimeException("Test file not found: " + fileName);
            }

            ObjectMapper mapper = new ObjectMapper();

            List<TestCase> testCases = mapper.readValue(is, new TypeReference<List<TestCase>>() {});

            return testCases.stream();

        } catch (Exception e) {
            throw new RuntimeException("Failed to load test data", e);
        }
    }
}
