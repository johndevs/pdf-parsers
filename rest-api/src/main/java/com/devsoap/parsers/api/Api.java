package com.devsoap.parsers.api;

import com.devsoap.parsers.caruna.CarunaParser;
import com.devsoap.parsers.composites.CarunaHelenParser;
import com.devsoap.parsers.helen.HelenParser;
import com.devsoap.parsers.plugsurfing.PlugSurfingParser;
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

                path(CarunaParser.class.getSimpleName(), () -> {
                    get(ctx -> ctx.render(renderParserStaticPage(CarunaParser.class, "upload.html")));
                    post(ctx -> {
                       withUploadedFile(ctx.uploadedFiles().get(0), carunaFile -> {
                           renderParserOutput(ctx, result -> CarunaParser
                                   .run(carunaFile, result), "caruna-report.csv");
                       });
                    });
                });

                path(HelenParser.class.getSimpleName(), () -> {
                    get(ctx -> ctx.render(renderParserStaticPage(HelenParser.class, "upload.html")));
                    post(ctx -> {
                        withUploadedFile(ctx.uploadedFiles().get(0), helenFile -> {
                            renderParserOutput(ctx, result -> HelenParser
                                    .run(helenFile, ctx.formParam("dayKwh"), ctx.formParam("nightKwh"), result)
                            , "caruna-report.csv");
                        });
                    });
                });

                path(CarunaHelenParser.class.getSimpleName(), () -> {
                    get(ctx -> ctx.render(renderParserStaticPage(CarunaHelenParser.class, "upload.html")));
                    post(ctx -> {
                        withUploadedFile(ctx.uploadedFiles().get(1), carunaFile -> {
                            withUploadedFile(ctx.uploadedFiles().get(0), helenFile -> {
                                renderParserOutput(ctx, result ->
                                        CarunaHelenParser.run(carunaFile, helenFile, result), "caruna-helen-report.csv");
                            });
                        });
                    });
                });

                path(PlugSurfingParser.class.getSimpleName(), () -> {
                    get(ctx -> ctx.render(renderParserStaticPage(PlugSurfingParser.class, "upload.html")));
                    post(ctx -> {
                        withUploadedFile(ctx.uploadedFiles().get(0), plugFile -> {
                            renderParserOutput(ctx, result -> PlugSurfingParser
                                    .run(plugFile, result), "plugsurfing-report.csv");
                        });
                    });
                });

            });
        });
    }

    private static void renderParserOutput(Context ctx, Consumer<PrintStream> parserCommand, String filename) {
        var resultStream = new ByteArrayOutputStream();
        try (var result = new PrintStream(resultStream)) {
            parserCommand.accept(result);
        }
        renderReport(ctx, new ByteArrayInputStream(resultStream.toByteArray()), filename);
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