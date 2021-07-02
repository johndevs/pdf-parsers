package com.devsoap.parsers.api;

import com.devsoap.parsers.composites.CarunaHelenParser;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import org.apache.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import static io.javalin.apibuilder.ApiBuilder.*;

public class Api {

    static {
        var logBuilder = ConfigurationBuilderFactory.newConfigurationBuilder();
        logBuilder.add(logBuilder.newAppender("stdout", "Console"));

        var rootLogger = logBuilder.newRootLogger(Level.INFO);
        rootLogger.add(logBuilder.newAppenderRef("stdout"));
        logBuilder.add(rootLogger);

        Configurator.initialize(logBuilder.build());
    }

    private static final Logger LOGGER = Logger.getLogger(Api.class);

    public static void main(String[] args) {
        var app = Javalin.create().start(7000);
        app.routes(() -> {
            get(ctx -> { ctx.result("PDF Parsers REST API"); });
            path("parsers", () -> {
                path(CarunaHelenParser.class.getSimpleName(), () -> {
                    get(ctx -> ctx.render(renderParserStaticPage(CarunaHelenParser.class, "upload.html")));
                    post(ctx -> {
                        withUploadedFile(ctx.uploadedFiles().get(1), carunaFile -> {
                            withUploadedFile(ctx.uploadedFiles().get(0), helenFile -> {
                                var resultStream = new ByteArrayOutputStream();
                                try (var result = new PrintStream(resultStream)) {
                                    CarunaHelenParser.run(carunaFile, helenFile, result);
                                }
                                renderReport(ctx, new ByteArrayInputStream(resultStream.toByteArray()),
                                        "caruna-helen-report.csv");
                            });
                        });
                    });
                });
            });
        });
    }

    private static void withUploadedFile(UploadedFile fileUpload, Consumer<Path> processor) {
        try {
            var file = Files.createTempFile(fileUpload.getFilename(), fileUpload.getExtension());
            Files.copy(fileUpload.getContent(), file, StandardCopyOption.REPLACE_EXISTING);
            try {
                processor.accept(file);
            } finally {
                file.toFile().delete();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to process file " + fileUpload.getFilename(), e);
            return;
        }
    }

    private static void renderReport(Context context, InputStream result, String filename) {
        context.result(result)
            .header("Content-Type", "text/csv; charset=utf-8")
            .header("Content-Disposition","inline; filename=\""+filename+"\"");
    }

    private static String renderParserStaticPage(Class<?> clazz, String page) {
        var folder = clazz.getPackageName().replace(".", "/");
        return folder + "/" + page;
    }
}