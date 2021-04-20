package com.devsoap.parsers.composites;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

public class CarunaHelenParser {

    public static void main(String[] args) {
        var carunaFile = Path.of("/home/john/Downloads/caruna-lasku.pdf");
        var helenFile = Path.of("/home/john/Downloads/helen-lasku.pdf");

        var carunaPeriods = com.devsoap.parsers.caruna.Parser.parse(carunaFile);
        var nightSiirto = carunaPeriods.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().transferNightKwh));
        var daySiirto = carunaPeriods.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().transferDayKwh));
        var helenPeriods = com.devsoap.parsers.helen.Parser.parse(helenFile, daySiirto, nightSiirto);

        System.out.println("Kuukausi,Perusmaksu (energia),Perusmaksu (siirto),Päiväenergia (kWh),Päiväenergia " +
                "(EUR),Yöenergia (kWh),Yöenergia (EUR),Päiväsiirto (kWh),Päiväsiirto (EUR),Yösiirto (kWh)" +
                ",Yösiirto (EUR),Vero");

        var months = new HashSet<>(carunaPeriods.keySet());
        months.addAll(helenPeriods.keySet());
        months.forEach(month -> {
            var hp = helenPeriods.getOrDefault(month, new com.devsoap.parsers.helen.Parser.Period());
            var cp = carunaPeriods.getOrDefault(month, new com.devsoap.parsers.caruna.Parser.Period());

            var csv = String.format("%s,%.02f,%.02f,%d,%.02f,%d,%.02f,%d,%.02f,%d,%.02f,%.02f",
                    month, hp.basicPay, cp.basicPay, hp.dayEnergy, hp.dayEnergyEur, hp.nightEnergy,
                    hp.nightEnergyEur, cp.transferDayKwh, cp.transferDayTotal, cp.transferNightKwh,
                    cp.transferNightTotal, cp.tax);
            csv = csv.replace(",0,",",,").replace(",0.00",",");
            System.out.println(csv);
        });
    }
}