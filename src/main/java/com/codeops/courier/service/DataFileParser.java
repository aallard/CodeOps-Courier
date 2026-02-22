package com.codeops.courier.service;

import com.codeops.courier.exception.ValidationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses CSV or JSON data files into iteration variable maps for collection runs.
 * Each row (CSV) or object (JSON) becomes one iteration's local variables.
 */
@Service
@Slf4j
public class DataFileParser {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Parses a data file (CSV or JSON) into a list of variable maps.
     * Each entry in the list represents one iteration's local variables.
     *
     * @param content  raw file content (CSV or JSON string)
     * @param filename filename used to determine format (.csv or .json)
     * @return list of variable maps, one per iteration
     * @throws ValidationException if content is malformed or format is unsupported
     */
    public List<Map<String, String>> parse(String content, String filename) {
        if (content == null || content.isBlank()) {
            throw new ValidationException("Data file content is empty");
        }
        if (filename == null || filename.isBlank()) {
            throw new ValidationException("Data filename is required");
        }

        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".csv")) {
            return parseCsv(content);
        } else if (lowerFilename.endsWith(".json")) {
            return parseJson(content);
        } else {
            throw new ValidationException("Unsupported data file format. Use .csv or .json");
        }
    }

    /**
     * Parses CSV content. First row is headers (variable names),
     * subsequent rows are values. Each row becomes one iteration.
     * Handles quoted values (commas inside double quotes) and escaped quotes.
     *
     * @param content the CSV string
     * @return list of variable maps, one per data row
     * @throws ValidationException if CSV has no header row
     */
    private List<Map<String, String>> parseCsv(String content) {
        String[] lines = content.split("\r?\n");
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new ValidationException("CSV file is empty or has no header row");
        }

        List<String> headers = parseCsvLine(lines[0]);
        if (headers.isEmpty()) {
            throw new ValidationException("CSV file has no headers");
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            List<String> values = parseCsvLine(lines[i]);
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), j < values.size() ? values.get(j) : "");
            }
            result.add(row);
        }
        return result;
    }

    /**
     * Parses a single CSV line into a list of field values.
     * Handles double-quoted fields and escaped quotes (doubled double-quotes).
     *
     * @param line the CSV line to parse
     * @return list of field values
     */
    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }

    /**
     * Parses JSON content. Must be a JSON array of objects.
     * Each object becomes one iteration's variables. All values are converted to strings.
     *
     * @param content the JSON string
     * @return list of variable maps, one per JSON object
     * @throws ValidationException if JSON is malformed or not an array of objects
     */
    private List<Map<String, String>> parseJson(String content) {
        try {
            Object parsed = JSON_MAPPER.readValue(content, Object.class);
            if (!(parsed instanceof List<?> list)) {
                throw new ValidationException("JSON data file must be an array of objects");
            }

            List<Map<String, String>> result = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new ValidationException("Each element in JSON data file must be an object");
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    row.put(String.valueOf(entry.getKey()),
                            entry.getValue() != null ? String.valueOf(entry.getValue()) : "");
                }
                result.add(row);
            }
            return result;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Invalid JSON data file: " + e.getMessage());
        }
    }
}
