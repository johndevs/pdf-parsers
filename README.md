# PDF Parsers

This project contains different PDF parsers for my personal use. Feel free to copy for personal use.

### Caruna Invoice Parser
PDF parser to parse Caruna invoices.

Usage: ``./gradlew :caruna-invoice:run --args="/path/to/pdf"``

### Helen Invoice Parser
PDF parser to parse Helen invoices.

Usage: ``./gradlew :helen-invoice:run --args="/path/to/pdf <month>:dayKwh <month>:nightKwh"``


### Helen/Caruna Composite Parser
PDF parser to combine Helen/Caruna invoices into single lines

Usage: ``./gradlew :composite-parsers:run --args="<caruna invoice>.pdf <helen-invoice>.pdf""``





