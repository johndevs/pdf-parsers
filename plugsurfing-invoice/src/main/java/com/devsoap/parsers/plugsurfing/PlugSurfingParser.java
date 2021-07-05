package com.devsoap.parsers.plugsurfing;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

public class PlugSurfingParser {

    private static final Pattern DATE_TIME_KWH_DURATION_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}) \\((.*), (.*)kWh\\)");
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(\\d+) (\\d+,\\d+) (\\d+)% (\\d+,\\d+) €");

    private static final Locale FI_LOCALE = new Locale("FI", "fi");
    private static final DateTimeFormatter FI_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withLocale(FI_LOCALE);
    private static final DateTimeFormatter FI_TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withLocale(FI_LOCALE);
    private static final DateTimeFormatter KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-M");
    private static final NumberFormat GERMANY_AMOUNT = NumberFormat.getNumberInstance(Locale.FRANCE);

    public static void main(String[] args) {
        var filename = args[0];
        var file = Paths.get(filename);
        run(file, System.out);
    }

    public static void run(Path file, PrintStream result) {
        result.println("Vuosi,Kuukausi,Latauksia,Perusmaksu(€),Total(€),Energia(kwh)");
        parse(file).forEach((month, sessions) -> result.println(sessions.stream()
            .reduce(Session::add)
            .map(session -> String.format("%s,%s,%d,%.02f,%.02f,%.02f",
                month.split("-")[0], month.split("-")[1],
                session.quantity, session.unitPrice, session.amountEur, session.kwh))
                .orElseThrow()));
    }

    public static class Session {
        public LocalDateTime timestamp;
        public Duration duration;
        public int quantity = 0;
        public double unitPrice = 0;
        public double taxRate = 0;
        public double amountEur = 0;
        public double kwh;
        public Session add(Session session) {
            this.duration = this.duration.plus(session.duration);
            this.quantity += session.quantity;
            this.unitPrice += this.unitPrice;
            this.taxRate = Math.max(this.taxRate, session.taxRate);
            this.amountEur += session.amountEur;
            this.kwh += session.kwh;
            return this;
        }
    }

    public static Map<String, List<Session>> parse(Path file) {
        var sessions = new HashMap<String, List<Session>>();
        try (var reader = new PdfReader(file.toFile())) {
            var document = new PdfDocument(reader);
            Session session = null;
            for (var pageIndex=1; pageIndex <= document.getNumberOfPages(); pageIndex++) {
                var page = document.getPage(pageIndex);
                var text = PdfTextExtractor.getTextFromPage(page);
                var scanner = new Scanner(text);
                while(scanner.hasNextLine()) {
                    var line = scanner.nextLine();
                    if (DATE_TIME_KWH_DURATION_PATTERN.asPredicate().test(line)) {
                        var matcher = DATE_TIME_KWH_DURATION_PATTERN.matcher(line);
                        session = new Session();
                        while(matcher.find()) {
                            session.timestamp = LocalDateTime.parse(matcher.group(1), FI_DATE).withSecond(0);
                            session.duration = Duration.between(LocalTime.of(0,0,0), LocalTime.from(FI_TIME.parse(matcher.group(2))));
                            session.kwh = Double.parseDouble(matcher.group(3));
                        }
                    } else if (session != null && QUANTITY_PATTERN.asPredicate().test(line)) {
                        var matcher = QUANTITY_PATTERN.matcher(line);
                        while(matcher.find()) {
                            session.quantity = Integer.parseInt(matcher.group(1));
                            session.unitPrice = GERMANY_AMOUNT.parse(matcher.group(2)).doubleValue();
                            session.taxRate =  Double.parseDouble(matcher.group(3)) / 100.0;
                            session.amountEur = GERMANY_AMOUNT.parse(matcher.group(4)).doubleValue();
                            sessions.computeIfAbsent(
                                KEY_FORMATTER.format(session.timestamp),
                                (k) -> new ArrayList<>())
                                .add(session);
                        }
                        session = null;
                    }
                }
            }
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
        return sessions;
    }
}
