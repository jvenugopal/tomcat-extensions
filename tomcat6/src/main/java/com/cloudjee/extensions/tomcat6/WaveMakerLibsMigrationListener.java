package com.cloudjee.extensions.tomcat6;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.StandardContext;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * Created by anshulr on 27/2/15.
 */
public class WaveMakerLibsMigrationListener implements LifecycleListener {

    private static Log log = LogFactory.getLog(WaveMakerLibsMigrationListener.class);

    private static final String LIB_PATH = "/vol/wm-studio-lib";
    private static final List<String> defaultApps = Arrays.asList("/docs", "/examples", "/host-manager", "/manager", "/ROOT");
    private static final String VERSION_FILE = "wm-libs-version.props";

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        String type = event.getType();
        if (Lifecycle.BEFORE_START_EVENT.equals(type)) {
            Lifecycle source = event.getLifecycle();
            if (source instanceof StandardContext) {
                StandardContext standardContext = (StandardContext) source;
                String relativeContextPath = standardContext.getName();
                // Migrate Only if it is wavemaker App.
                if (relativeContextPath != null && !relativeContextPath.isEmpty() && !defaultApps.contains(relativeContextPath)) {
                    try {
                        migrateLibs(relativeContextPath);
                    } catch (IOException e) {
                        log.error("Jar Libs Migration Failed for App :- " + relativeContextPath, e);
                    }
                }
            }
        }
    }

    private void migrateLibs(String appName) throws IOException {
        String tomcatBase = System.getProperty("catalina.base");
        boolean toMigrate = true;
        Path appWebInfVersionPropsPath = null;
        try {
            appWebInfVersionPropsPath = Paths.get(tomcatBase, "webapps", appName, "WEB-INF", "lib", VERSION_FILE);
        } catch (Exception e) {
            log.info("Path  Does not exists for appName :- " + appName, e);
            toMigrate = false;
        }
        // If Path Can not be created , we dont need to migrate.
        if (toMigrate) {
            // Migrate Only
            if (Files.notExists(appWebInfVersionPropsPath)) {
                log.info("Migrating Jar Libs for App :- " + appName);
                copyDirectory(Paths.get(LIB_PATH), Paths.get(tomcatBase, "webapps", appName, "WEB-INF", "lib"));
                Files.createFile(appWebInfVersionPropsPath);
            } else {
                log.info("No Jar Libs Migration Required for App :- " + appName);
            }
        }
    }


    private static void copyDirectory(final Path source, final Path target) throws IOException {
        Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                Integer.MAX_VALUE, new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes sourceBasic) throws IOException {
                        Path targetDir = Files.createDirectories(target.resolve(source.relativize(dir)));

                        BasicFileAttributeView targetBasic = Files.getFileAttributeView(targetDir, BasicFileAttributeView.class);
                        targetBasic.setTimes(sourceBasic.lastModifiedTime(), sourceBasic.lastAccessTime(), sourceBasic.creationTime());
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                        throw e;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                        if (e != null) throw e;
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

}
