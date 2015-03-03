package com.cloudjee.extensions.tomcat6;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Created by anshulr on 3/3/15.
 */
public class WaveMakerServerListener implements LifecycleListener {

    private static Log log = LogFactory.getLog(WaveMakerServerListener.class);

    private static final List<String> defaultApps = Arrays.asList("host-manager.xml", "manager.xml");

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        String type = event.getType();
        if (Lifecycle.BEFORE_START_EVENT.equals(type)) {
            Lifecycle source = event.getLifecycle();
            if (source instanceof StandardServer) {
                StandardServer server = (StandardServer) source;
                try {
                    removeStudioApps();
                } catch (Throwable th) {
                    log.error("Error while deleting studio Apps XML", th);
                }
            }
        }
    }

    private void removeStudioApps() {
        String tomcatBase = System.getProperty("catalina.base");
        File file = new File(tomcatBase + "/conf/Catalina/localhost");
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File xmlFile : files) {
                if (!defaultApps.contains(xmlFile.getName())) {
                    log.info("Deleting File with Name :- " + xmlFile.getName());
                    xmlFile.delete();
                }
            }
        }
    }
}
