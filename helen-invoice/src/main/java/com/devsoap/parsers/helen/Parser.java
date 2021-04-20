package com.devsoap.parsers.helen;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Function;
import java.util.regex.Pattern;

public class Parser {

    private static final Pattern PERUSMAKSU_PATTERN = Pattern.compile(
            "perusmaksu (\\d\\d?\\.\\d\\d?\\.\\d\\d\\d\\d)-(\\d\\d?\\.\\d\\d?\\.\\d\\d\\d\\d).* (\\d*,\\d\\d) e");
    private static final Pattern ENERGIA_PATTERN = Pattern.compile(
            "energia (\\d\\d?\\.\\d\\d?\\.\\d\\d\\d\\d)-(\\d\\d?\\.\\d\\d?\\.\\d\\d\\d\\d) ([0-9 ]*) kWh (\\d+,\\d\\d) c");


    private static final Locale FI_LOCALE = new Locale("FI", "fi");
    private static final DateTimeFormatter FI_DATE = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.SHORT)
            .withLocale(FI_LOCALE);



    static class Period {
        double basicPay = 0.0;
        int dayEnergy = 0;
        int nightEnergy = 0;
        double dayEnergyEur = 0.0;
        double nightEnergyEur = 0.0;

        @Override
        public String toString() {
            return "Period{" +
                    "basicPay=" + basicPay +
                    ", dayEnergy=" + dayEnergy +
                    ", nightEnergy=" + nightEnergy +
                    ", dayEnergyEur=" + dayEnergyEur +
                    ", nightEnergyEur=" + nightEnergyEur +
                    '}';
        }
    }

    public static void main(String[] args) {
        var filename = args[0];
        var daySiirtoKwh = 937;
        var nightSiirtoKwh = 920;

        var file = Paths.get(filename);
        try(var reader = new PdfReader(file.toFile())) {
            var document = new PdfDocument(reader);
            var page2 = document.getPage(2);
            var text = PdfTextExtractor.getTextFromPage(page2);

            var scanner = new Scanner(text);

            System.out.println("Kuukausi,Perusmaksu (energia),Perusmaksu (siirto),Päiväenergia (kWh),Päiväenergia " +
                    "(EUR),Yöenergia (kWh),Yöenergia (EUR),Päiväsiirto (kWh),Päiväsiirto (EUR),Yösiirto (kWh)" +
                    ",Yösiirto (EUR),Vero");

            var periods = new HashMap<String, Period>();

            while(scanner.hasNextLine()) {
                var line = scanner.nextLine();
                if(PERUSMAKSU_PATTERN.asPredicate().test(line)) {
                    var matcher = PERUSMAKSU_PATTERN.matcher(line);
                    while (matcher.find()) {
                         var month = LocalDate.from( FI_DATE.parse(matcher.group(1)))
                                .getMonth().getDisplayName(TextStyle.FULL, new Locale("FI","fi"));
                         month = month.substring(0,1).toUpperCase() + month.substring(1, month.length()-2);
                         var  basicPay = Double.parseDouble(matcher.group(3).replace(",", "."));

                         periods.computeIfAbsent(month, s -> new Period());
                         periods.computeIfPresent(month, (s,p) -> {
                             p.basicPay =basicPay;
                             return p;
                         });
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

                        periods.computeIfAbsent(month, s -> new Period());
                        periods.computeIfPresent(month, (s,p) -> {
                            p.dayEnergy = totalEnergy - nightSiirtoKwh;
                            p.dayEnergyEur = p.dayEnergy * eurPerKwh;
                            p.nightEnergy = totalEnergy - daySiirtoKwh;
                            p.nightEnergyEur = p.nightEnergy * eurPerKwh;
                            return p;
                        });
                    }
                }
            }

            periods.forEach((month,period ) -> {
                var csv = String.format("%s,%.02f,,%d,%.02f,%d,%.02f,,,,", month, period.basicPay,
                        period.dayEnergy, period.dayEnergyEur,period.nightEnergy, period.nightEnergyEur);
                System.out.println(csv);
            });

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
