package org.update4j.demo.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.update4j.Configuration;
import org.update4j.FileMetadata;
import org.update4j.OS;

public class CreateConfig {

    public static void main(String[] args) throws IOException {


        String configLoc = System.getProperty("config.location");
        String dir = configLoc + "/business";

        Configuration config = Configuration.builder()
                        .baseUri("http://docs.update4j.org/demo/business")
                        .basePath("${user.dir}/business")
                        .file(FileMetadata.readFrom(dir + "/business-1.0.0.jar").path("business-1.0.0.jar").classpath())
                        .file(FileMetadata.readFrom(dir + "/controlsfx-9.0.0.jar")
                                        .uri(mavenUrl("org.controlsfx", "controlsfx", "9.0.0"))
                                        .classpath())
                        .file(FileMetadata.readFrom(dir + "/jfoenix-9.0.8.jar")
                                        .uri(mavenUrl("com.jfoenix", "jfoenix", "9.0.8"))
                                        .classpath())
                        .file(FileMetadata.readFrom(dir + "/jfxtras-common-10.0-r1.jar")
                                        .uri(mavenUrl("org.jfxtras", "jfxtras-common", "10.0-r1"))
                                        .classpath())
                        .file(FileMetadata.readFrom(dir + "/jfxtras-gauge-linear-10.0-r1.jar")
                                        .uri(mavenUrl("org.jfxtras", "jfxtras-gauge-linear", "10.0-r1"))
                                        .classpath())
                        .property("maven.central", MAVEN_BASE)
                        .build();

        try (Writer out = Files.newBufferedWriter(Paths.get(dir + "/config.xml"))) {
            config.write(out);
        }

        String cacheLoc = System.getProperty("maven.dir") + "/fxcache";
        dir = configLoc + "/bootstrap";

        cacheJavafx();

        config = Configuration.builder()
                        .baseUri("${maven.central.javafx}")
                        .basePath("${user.dir}/bootstrap")
                        .file(FileMetadata.readFrom(dir + "/../business/config.xml") // fall back if no internet
                                        .uri("http://docs.update4j.org/demo/business/config.xml")
                                        .path("../business/config.xml"))
                        .file(FileMetadata.readFrom(dir + "/bootstrap-1.0.0.jar")
                                        .classpath()
                                        .uri("http://docs.update4j.org/demo/bootstrap/bootstrap-1.0.0.jar"))
                        .files(FileMetadata.streamDirectory(cacheLoc)
                                        .filter(fm -> fm.getSource().getFileName().toString().startsWith("javafx"))
                                        .peek(f -> f.classpath())
                                        .peek(f -> f.ignoreBootConflict()) // if run with JDK 9/10
                                        .peek(f -> f.osFromFilename())
                                        .peek(f -> f.uri(extractJavafxURL(f.getSource(), f.getOs()))))

                        .property("default.launcher.main.class", "org.update4j.Bootstrap")
                        .property("maven.central", MAVEN_BASE)
                        .property("maven.central.javafx", "${maven.central}/org/openjfx/")
                        .build();

        try (Writer out = Files.newBufferedWriter(Paths.get(configLoc + "/setup.xml"))) {
            config.write(out);
        }

    }

    private static final String MAVEN_BASE = "https://repo1.maven.org/maven2";

    private static String mavenUrl(String groupId, String artifactId, String version, OS os) {
        StringBuilder builder = new StringBuilder();
        builder.append(MAVEN_BASE + '/');
        builder.append(groupId.replace('.', '/') + "/");
        builder.append(artifactId.replace('.', '-') + "/");
        builder.append(version + "/");
        builder.append(artifactId.replace('.', '-') + "-" + version);

        if (os != null) {
            builder.append('-' + os.getShortName());
        }

        builder.append(".jar");

        return builder.toString();
    }

    private static String mavenUrl(String groupId, String artifactId, String version) {
        return mavenUrl(groupId, artifactId, version, null);
    }

    private static String extractJavafxURL(Path path, OS os) {
        Pattern regex = Pattern.compile("javafx-([a-z]+)-([0-9.]+)(?:-(win|mac|linux))?\\.jar");
        Matcher match = regex.matcher(path.getFileName().toString());

        if (!match.find())
            return null;

        String module = match.group(1);
        String version = match.group(2);
        if (os == null && match.groupCount() > 2) {
            os = OS.fromShortName(match.group(3));
        }

        return mavenUrl("org.openjfx", "javafx." + module, version, os);
    }

    private static String injectOs(String file, OS os) {
        return file.replaceAll("(.+)\\.jar", "$1-" + os.getShortName() + ".jar");
    }

    private static void cacheJavafx() throws IOException {
        String names = System.getProperty("target") + "/javafx";
        Path cacheDir = Paths.get(System.getProperty("maven.dir"), "fxcache");

        try (Stream<Path> files = Files.list(Paths.get(names))) {
            files.forEach(f -> {
                try {
                    
                    if (!Files.isDirectory(cacheDir))
                        Files.createDirectory(cacheDir);
                    
                    for (OS os : EnumSet.of(OS.WINDOWS, OS.MAC, OS.LINUX)) {
                        Path file = cacheDir.resolve(injectOs(f.getFileName().toString(), os));

                        if (Files.notExists(file)) {
                            String download = extractJavafxURL(f, os);
                            URI uri = URI.create(download);
                            try (InputStream in = uri.toURL().openStream()) {
                                Files.copy(in, file);
                            }
                        }
                    }

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
