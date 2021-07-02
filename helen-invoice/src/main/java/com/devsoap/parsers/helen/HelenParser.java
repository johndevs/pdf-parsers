package com.devsoap.parsers.helen;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HelenParser {

    private static final Pattern PERUSMAKSU_PATTERN = Pattern.compile(
            "perusmaksu (\\d\\d?\\.\\d\\d?\\.\\d\\d\\d\\d)-(\\d\\d?\\.\\d\\d?\\.\\d\\d\\d\\d).* (\\d*,\\d\\d) e");
    private static final Pattern ENERGIA_PATTERN = Pattern.compile(
            "energia (\\d\\d?\\.\\d\\d?\\.\\d\\d\\d\\d)-(\\d\\d?\\.\\d\\d?\\.\\d\\d\\d\\d) ([0-9 ]*) kWh (\\d+,\\d\\d) c");


    private static final Locale FI_LOCALE = new Locale("FI", "fi");
    private static final DateTimeFormatter FI_DATE = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.SHORT)
            .withLocale(FI_LOCALE);

    public static class Period {
        public double basicPay = 0.0;
        public int dayEnergy = 0;
        public int nightEnergy = 0;
        public double dayEnergyEur = 0.0;
        public double nightEnergyEur = 0.0;
    }

    public static void main(String[] args) {
        var filename = Path.of(args[0]);
        var daySiirtoPeriods = args[1];
        var nightSiirtoPeriods = args[2];
        run(filename, daySiirtoPeriods, nightSiirtoPeriods, System.out);
    }

    public static void run(Path helenFile, String daySiirtoKwhPeriods, String nightSiirtoKwhPeriods,
                           PrintStream result) {
        var daySiirtoKwh = Arrays
                .stream(daySiirtoKwhPeriods.split(","))
                .map(period -> period.split(":"))
                .collect(Collectors.toMap(values -> values[0], values -> Integer.parseInt(values[1])));
        var nightSiirtoKwh = Arrays
                .stream(nightSiirtoKwhPeriods.split(","))
                .map(period -> period.split(":"))
                .collect(Collectors.toMap(values -> values[0], values -> Integer.parseInt(values[1])));

        result.println("Kuukausi,Perusmaksu (energia),Perusmaksu (siirto),Päiväenergia (kWh),Päiväenergia " +
                "(EUR),Yöenergia (kWh),Yöenergia (EUR),Päiväsiirto (kWh),Päiväsiirto (EUR),Yösiirto (kWh)" +
                ",Yösiirto (EUR),Vero");

        parse(helenFile,daySiirtoKwh, nightSiirtoKwh).forEach((month,period ) -> {
            var csv = String.format("%s,%.02f,,%d,%.02f,%d,%.02f,,,,", month,
                    period.basicPay, period.dayEnergy, period.dayEnergyEur,period.nightEnergy, period.nightEnergyEur);
            result.println(csv);
        });
    }

    public static Map<String, Period> parse(Path file, Map<String, Integer> daySiirtoKwh,
                                            Map<String, Integer> nightSiirtoKwh) {
        try(var reader = new PdfReader(file.toFile())) {
            var document = new PdfDocument(reader);
            var page2 = document.getPage(2);
            var text = PdfTextExtractor.getTextFromPage(page2);
            var scanner = new Scanner(text);
            var periods = new HashMap<String, Period>();
            var basicPay = 0.0;
            while(scanner.hasNextLine()) {
                var line = scanner.nextLine();
                if(PERUSMAKSU_PATTERN.asPredicate().test(line)) {
                    var matcher = PERUSMAKSU_PATTERN.matcher(line);
                    while (matcher.find()) {
                         basicPay = Double.parseDouble(matcher.group(3).replace(",", "."));
                    }
                } else if(ENERGIA_PATTERN.asPredicate().test(line)) {
                    var matcher = ENERGIA_PATTERN.matcher(line);
                    while (matcher.find()) {
                        var month = LocalDate.from( FI_DATE.parse(matcher.group(1)))
                                .getMonth().getDisplayName(TextStyle.FULL, new Locale("FI","fi"));
                        month = month.substring(0,1).toUpperCase() + month.substring(1, month.length()-2);

                        var totalEnergy = Integer.parseInt(matcher.group(3)
                                .replace(" ", ""));
                        var eurPerKwh = Double.parseDouble(matcher.group(4)
                                .replace(",", ".")) / 100.0;

                        var nightSiirto = nightSiirtoKwh.getOrDefault(month,0);
                        var daySriirto = daySiirtoKwh.getOrDefault(month,0);
                        periods.computeIfAbsent(month, s -> new Period());
                        periods.computeIfPresent(month, (s,p) -> {
                            p.dayEnergy = totalEnergy - nightSiirto;
                            p.dayEnergyEur = p.dayEnergy * eurPerKwh;
                            p.nightEnergy = totalEnergy - daySriirto;
                            p.nightEnergyEur = p.nightEnergy * eurPerKwh;
                            return p;
                        });
                    }
                }
            }

            var totalBasicPay = basicPay;
            periods.forEach((month, period) -> {
                period.basicPay = totalBasicPay / periods.size();
            });

            return periods;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
