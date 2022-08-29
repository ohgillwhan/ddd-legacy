package calculator;

import calculator.utils.StringUtils;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class StringNumberParser {

    private static final Pattern CUSTOM_DELIMITER_PATTERN = Pattern.compile("//(.)\n(.*)");

    public List<PositiveNumber> toPositiveNumbers(String text) {
        List<String> stringNumbers = getStringNumbers(text);
        return stringNumbers.stream()
                .map(PositiveNumber::new)
                .collect(Collectors.toList());
    }

    private List<String> getStringNumbers(String text) {
        Matcher m = CUSTOM_DELIMITER_PATTERN.matcher(text);
        if (m.find()) {
            String customDelimiter = m.group(1);
            String group = m.group(2);
            return StringUtils.toList(group, customDelimiter);
        }

        return StringUtils.toList(text, "[,:]");
    }
}