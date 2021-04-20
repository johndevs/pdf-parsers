package com.devsoap.parsers.caruna;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Pattern;

public class Parser {

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile("(\\d\\d?\\.\\d\\d?\\.\\d\\d\\d\\d) - (\\d\\d?\\.\\d\\d?\\.\\d\\d\\d\\d)");
    private static final Pattern PERUSMAKSU_PATTERN = Pattern.compile("Perusmaksu.* (\\d*,\\d\\d) (EUR|€)");
    private static final Pattern P_SIIRTO_PATTERN = Pattern.compile("Päiväsiirto.* (\\d*,\\d\\d) snt.* (\\d*,\\d\\d) (EUR|€)");
    private static final Pattern O_SIIRTO_PATTERN = Pattern.compile("Yösiirto.* (\\d*,\\d\\d) snt.* (\\d*,\\d\\d) (EUR|€)");
    private static final Pattern TAX_PATTERN = Pattern.compile("Sähkövero.* (\\d*,\\d\\d) (EUR|€)");

    private static final Locale FI_LOCALE = new Locale("FI", "fi");
    private static final DateTimeFormatter FI_DATE = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.SHORT)
            .withLocale(FI_LOCALE);

    public static class Period {
        public double basicPay = 0.0;
        public double transferDayPrice = 0.0;
        public double transferDayTotal = 0.0;
        public int transferDayKwh = 0;
        public double transferNightPrice = 0.0;
        public double transferNightTotal = 0.0;
        public int transferNightKwh = 0;
        public double tax = 0.0;
    }

    public static void main(String[] args) {
        var filename = args[0];
        var file = Paths.get(filename);

        System.out.println("Kuukausi,Perusmaksu (energia),Perusmaksu (siirto),Päiväenergia (kWh),Päiväenergia " +
                "(EUR),Yöenergia (kWh),Yöenergia (EUR),Päiväsiirto (kWh),Päiväsiirto (EUR),Yösiirto (kWh)" +
                ",Yösiirto (EUR),Vero");

        parse(file).forEach((n,p) -> {
            var csv = String.format("%s,,%.02f,,,,,%d, %.02f,%d, %.02f, %.02f",
                    n, p.basicPay, p.transferDayKwh, p.transferDayTotal, p.transferNightKwh, p.transferNightTotal, p.tax);
            System.out.println(csv);
        });
    }

    public static Map<String, Period> parse(Path file) {
        try(var reader = new PdfReader(file.toFile())) {
            var document = new PdfDocument(reader);
            var page2 = document.getPage(2);
            var text = PdfTextExtractor.getTextFromPage(page2);
            var scanner = new Scanner(text);
            var periods = new HashMap<String, Period>();

            Period currentPeriod = null;
            while(scanner.hasNextLine()) {
                var line = scanner.nextLine();
                if(DATE_RANGE_PATTERN.asPredicate().test(line)) {
                    var matcher = DATE_RANGE_PATTERN.matcher(line);
                    while(matcher.find()) {
                        var month = LocalDate.from( FI_DATE.parse(matcher.group(1)))
                                .getMonth()
                                .getDisplayName(TextStyle.FULL, new Locale("FI","fi"));
                        month = month.substring(0,1).toUpperCase() + month.substring(1, month.length()-2);
                        currentPeriod = periods.computeIfAbsent(month, s -> new Period());
                    }
                } else if(PERUSMAKSU_PATTERN.asPredicate().test(line)) {
                    var matcher = PERUSMAKSU_PATTERN.matcher(line);
                    while (matcher.find()) {
                        currentPeriod.basicPay = Double.parseDouble(matcher.group(1).replace(",", "."));
                    }
                } else if(P_SIIRTO_PATTERN.asPredicate().test(line)) {
                    var matcher = P_SIIRTO_PATTERN.matcher(line);
                    while (matcher.find()) {
                        currentPeriod.transferDayPrice = Double.parseDouble(matcher.group(1).replace(",", ".")) / 100.0;
                        currentPeriod.transferDayTotal = Double.parseDouble(matcher.group(2).replace(",", "."));
                        currentPeriod.transferDayKwh = (int) Math.round(currentPeriod.transferDayTotal / currentPeriod.transferDayPrice);
                    }
                } else if(O_SIIRTO_PATTERN.asPredicate().test(line)) {
                    var matcher = O_SIIRTO_PATTERN.matcher(line);
                    while (matcher.find()) {
                        currentPeriod.transferNightPrice = Double.parseDouble(matcher.group(1).replace(",", ".")) / 100.0;
                        currentPeriod.transferNightTotal = Double.parseDouble(matcher.group(2).replace(",", "."));
                        currentPeriod.transferNightKwh = (int) Math.round(currentPeriod.transferNightTotal / currentPeriod.transferNightPrice);
                    }
                } else if(TAX_PATTERN.asPredicate().test(line)) {
                    var matcher = TAX_PATTERN.matcher(line);
                    while (matcher.find()) {
                        currentPeriod.tax = Double.parseDouble(matcher.group(1).replace(",", "."));
                    }
                }
            }
            return periods;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
