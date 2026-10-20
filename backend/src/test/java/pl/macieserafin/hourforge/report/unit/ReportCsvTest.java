package pl.macieserafin.hourforge.report.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.macieserafin.hourforge.report.ReportCsv;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCsvTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "=SUM(A1:A2)|'=SUM(A1:A2)", "+48 600|'+48 600", "-1+2|'-1+2", "@cmd|'@cmd",
        "plain text|plain text", "Zażółć gęślą jaźń|Zażółć gęślą jaźń", "a=b|a=b"})
    void neutralizesValuesThatSpreadsheetsWouldEvaluateAsFormulas(String input, String expected) {
        assertThat(ReportCsv.field(input)).isEqualTo(expected);
    }

    @Test
    void quotesFieldsWithSeparatorsQuotesAndLineBreaks() {
        assertThat(ReportCsv.field("a;b")).isEqualTo("\"a;b\"");
        assertThat(ReportCsv.field("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ReportCsv.field("first\nsecond")).isEqualTo("\"first\nsecond\"");
        assertThat(ReportCsv.field("tab\tinside")).isEqualTo("tab\tinside");
        assertThat(ReportCsv.field("\t=1;2")).isEqualTo("\"'\t=1;2\"");
        assertThat(ReportCsv.field(null)).isEmpty();
    }

    @Test
    void formatsDurationsWithHoursBeyondOneDay() {
        assertThat(ReportCsv.clock(0)).isEqualTo("00:00:00");
        assertThat(ReportCsv.clock(3725)).isEqualTo("01:02:05");
        assertThat(ReportCsv.clock(100 * 3600 + 59)).isEqualTo("100:00:59");
    }
}
