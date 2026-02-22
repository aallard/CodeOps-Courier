package com.codeops.courier.service;

import com.codeops.courier.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataFileParserTest {

    private DataFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new DataFileParser();
    }

    // ─── CSV Tests ───

    @Test
    void parseCsv_simpleRows() {
        String csv = "username,password,status\nadmin,secret,200\nguest,,401";
        List<Map<String, String>> result = parser.parse(csv, "data.csv");

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("username", "admin")
                .containsEntry("password", "secret")
                .containsEntry("status", "200");
        assertThat(result.get(1)).containsEntry("username", "guest")
                .containsEntry("password", "")
                .containsEntry("status", "401");
    }

    @Test
    void parseCsv_quotedValues() {
        String csv = "name,address\nJohn,\"123 Main St, Apt 4\"\nJane,\"He said \"\"hi\"\"\"";
        List<Map<String, String>> result = parser.parse(csv, "data.csv");

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("address", "123 Main St, Apt 4");
        assertThat(result.get(1)).containsEntry("address", "He said \"hi\"");
    }

    @Test
    void parseCsv_emptyValues() {
        String csv = "a,b,c\n,,\n1,,3";
        List<Map<String, String>> result = parser.parse(csv, "test.csv");

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("a", "").containsEntry("b", "").containsEntry("c", "");
        assertThat(result.get(1)).containsEntry("a", "1").containsEntry("b", "").containsEntry("c", "3");
    }

    @Test
    void parseCsv_singleRow() {
        String csv = "key,value\ntoken,abc123";
        List<Map<String, String>> result = parser.parse(csv, "test.csv");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("key", "token").containsEntry("value", "abc123");
    }

    @Test
    void parseCsv_emptyContent_throws() {
        assertThatThrownBy(() -> parser.parse("", "data.csv"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void parseCsv_headerOnly_returnsEmpty() {
        String csv = "username,password";
        List<Map<String, String>> result = parser.parse(csv, "data.csv");

        assertThat(result).isEmpty();
    }

    @Test
    void parseCsv_windowsLineEndings() {
        String csv = "name,age\r\nAlice,30\r\nBob,25";
        List<Map<String, String>> result = parser.parse(csv, "data.csv");

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("name", "Alice").containsEntry("age", "30");
    }

    // ─── JSON Tests ───

    @Test
    void parseJson_arrayOfObjects() {
        String json = "[{\"user\":\"admin\",\"role\":\"owner\"},{\"user\":\"guest\",\"role\":\"viewer\"}]";
        List<Map<String, String>> result = parser.parse(json, "data.json");

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("user", "admin").containsEntry("role", "owner");
        assertThat(result.get(1)).containsEntry("user", "guest").containsEntry("role", "viewer");
    }

    @Test
    void parseJson_numericValuesConvertedToString() {
        String json = "[{\"name\":\"test\",\"count\":42,\"rate\":3.14,\"active\":true}]";
        List<Map<String, String>> result = parser.parse(json, "test.json");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("name", "test")
                .containsEntry("count", "42")
                .containsEntry("rate", "3.14")
                .containsEntry("active", "true");
    }

    @Test
    void parseJson_invalidJson_throws() {
        assertThatThrownBy(() -> parser.parse("{not valid json", "data.json"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid JSON");
    }

    @Test
    void parseJson_notArray_throws() {
        assertThatThrownBy(() -> parser.parse("{\"key\":\"value\"}", "data.json"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("array");
    }

    // ─── Format Detection ───

    @Test
    void parse_detectsFormatFromFilename() {
        String csvContent = "key,val\na,b";
        List<Map<String, String>> csvResult = parser.parse(csvContent, "DATA.CSV");
        assertThat(csvResult).hasSize(1);

        String jsonContent = "[{\"key\":\"val\"}]";
        List<Map<String, String>> jsonResult = parser.parse(jsonContent, "test.JSON");
        assertThat(jsonResult).hasSize(1);
    }

    @Test
    void parse_unsupportedFormat_throws() {
        assertThatThrownBy(() -> parser.parse("content", "data.xml"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void parse_nullContent_throws() {
        assertThatThrownBy(() -> parser.parse(null, "data.csv"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void parse_nullFilename_throws() {
        assertThatThrownBy(() -> parser.parse("content", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("filename");
    }
}
