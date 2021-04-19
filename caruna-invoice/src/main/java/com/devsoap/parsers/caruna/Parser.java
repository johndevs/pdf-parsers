package com.devsoap.parsers.caruna;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile("(\\d\\d?\\.\\d\\d?\\.\\d\\d\\d\\d) - (\\d\\d?\\.\\d\\d?\\.\\d\\d\\d\\d)");
    private static final Pattern PERUSMAKSU_PATTERN = Pattern.compile("Perusmaksu.* (\\d*,\\d\\d) EUR");
    private static final Pattern P_SIIRTO_PATTERN = Pattern.compile("Päiväsiirto.* (\\d*,\\d\\d) snt.* (\\d*,\\d\\d) EUR");
    private static final Pattern O_SIIRTO_PATTERN = Pattern.compile("Yösiirto.* (\\d*,\\d\\d) snt.* (\\d*,\\d\\d) EUR");
    private static final Pattern TAX_PATTERN = Pattern.compile("Sähkövero.* (\\d*,\\d\\d) EUR");

    private static final Locale FI_LOCALE = new Locale("FI", "fi");
    private static final DateTimeFormatter FI_DATE = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.SHORT)
            .withLocale(FI_LOCALE);

    public static void main(String[] args) {
        var filename = args[0];
        var file = Paths.get(filename);
        try(var reader = new PdfReader(file.toFile())) {
            var document = new PdfDocument(reader);
            var page2 = document.getPage(2);
            var text = PdfTextExtractor.getTextFromPage(page2);

            var scanner = new Scanner(text);
            var month = "";
            var basicPay = 0.0;
            var transferDayPrice = 0.0;
            var transferDayTotal = 0.0;
            var transforDayKwh = 0L;
            var transferNightPrice = 0.0;
            var transferNightTotal = 0.0;
            var transforNightKwh = 0L;
            var tax = 0.0;

            System.out.println("Kuukausi,Perusmaksu (energia),Perusmaksu (siirto),Päiväenergia (kWh),Päiväenergia " +
                    "(EUR),Yöenergia (kWh),Yöenergia (EUR),Päiväsiirto (kWh),Päiväsiirto (EUR),Yösiirto (kWh)" +
                    ",Yösiirto (EUR),Vero");

            while(scanner.hasNextLine()) {
                var line = scanner.nextLine();
                if(DATE_RANGE_PATTERN.asPredicate().test(line)) {
                    if(!Objects.equals(month, "")) {
                        var csv = String.format("%s,,%.02f,,,,,%d, %.02f,%d, %.02f, %.02f",
                                month, basicPay, transforDayKwh, transferDayTotal, transforNightKwh, transferNightTotal, tax);
                        System.out.println(csv);
                    }

                    var matcher = DATE_RANGE_PATTERN.matcher(line);
                    while(matcher.find()) {
                        month = LocalDate.from( FI_DATE.parse(matcher.group(1)))
                                .getMonth()
                                .getDisplayName(TextStyle.FULL, new Locale("FI","fi"));
                        month = month.substring(0,1).toUpperCase() + month.substring(1, month.length()-2);
                    }
                } else if(PERUSMAKSU_PATTERN.asPredicate().test(line)) {
                    var matcher = PERUSMAKSU_PATTERN.matcher(line);
                    while (matcher.find()) {
                        basicPay = Double.parseDouble(matcher.group(1).replace(",", "."));
                    }
                } else if(P_SIIRTO_PATTERN.asPredicate().test(line)) {
                    var matcher = P_SIIRTO_PATTERN.matcher(line);
                    while (matcher.find()) {
                        transferDayPrice = Double.parseDouble(matcher.group(1).replace(",", ".")) / 100.0;
                        transferDayTotal = Double.parseDouble(matcher.group(2).replace(",", "."));
                        transforDayKwh = Math.round(transferDayTotal / transferDayPrice);
                    }
                } else if(O_SIIRTO_PATTERN.asPredicate().test(line)) {
                    var matcher = O_SIIRTO_PATTERN.matcher(line);
                    while (matcher.find()) {
                        transferNightPrice = Double.parseDouble(matcher.group(1).replace(",", ".")) / 100.0;
                        transferNightTotal = Double.parseDouble(matcher.group(2).replace(",", "."));
                        transforNightKwh = Math.round(transferNightTotal / transferNightPrice);
                    }
                } else if(TAX_PATTERN.asPredicate().test(line)) {
                    var matcher = TAX_PATTERN.matcher(line);
                    while (matcher.find()) {
                        tax = Double.parseDouble(matcher.group(1).replace(",", "."));
                    }
                }
            }
            var csv = String.format("%s,,%.02f,,,,,%d, %.02f,%d, %.02f, %.02f",
                    month, basicPay, transforDayKwh, transferDayTotal, transforNightKwh, transferNightTotal, tax);
            System.out.println(csv);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
