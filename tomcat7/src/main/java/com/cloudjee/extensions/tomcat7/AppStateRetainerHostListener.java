/**
 * Copyright (c) 2013 - 2014 CloudJee Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of CloudJee Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance
 * with the terms of the source code license agreement you entered into with CloudJee Inc.
 */
package com.cloudjee.extensions.tomcat7;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.catalina.Container;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Custom Listener to restore the webapp state of each host after the Tomcat is 
 * restarted.
 * @author <a href="mailto:pankaj.n@imaginea.com">pankaj</a>
 *
 */
public class AppStateRetainerHostListener implements LifecycleListener {

	private static Log log = LogFactory.getLog(AppStateRetainerHostListener.class);

	public void lifecycleEvent(LifecycleEvent event) {
		Host host = (Host) event.getLifecycle();
		String eventType = event.getType();
		log.debug("Received " + eventType + " event on host: " + host.getName());

		if (host instanceof StandardHost) {
			StandardHost standardHost = (StandardHost) host;
			if (Lifecycle.BEFORE_START_EVENT.equals(eventType)) {
				handleBeforeStartEvent(standardHost);
			} else if (Lifecycle.AFTER_START_EVENT.equals(eventType)) {
				handleAfterStartEvent(standardHost);
			} else if (Lifecycle.BEFORE_STOP_EVENT.equals(eventType)) {
				handleBeforeStopEvent(standardHost);
			}
		} else {
			log.warn("Ignoring the event of type " + eventType + " for unknown Host Class: " + host.getClass() +".");
		}
	}

	private void handleBeforeStopEvent(StandardHost host) {
		Set<String> appNames = new LinkedHashSet<String>();
		Container[] contexts = host.findChildren();
		for (Container context : contexts) {
			if (LifecycleState.STARTED.equals(context.getState())) {
				appNames.add(context.getName());
			}
		}
		serializeAndStoreObject(appNames, host);
	}

	private void handleAfterStartEvent(StandardHost host) {
		Set<String> appNames = deserializeAndRestoreState(host);
		if(appNames != null && !appNames.isEmpty()) { 
			try {
				Container[] contexts = host.findChildren();
				for (Container context : contexts) {
					String webAppName = context.getName();
					if (appNames.contains(webAppName)) {
						log.info("Starting webapp " + webAppName + " in host " + host.getName());
						try {
							((StandardContext) context).start();
						} catch (LifecycleException e) {
							log.error("Exception in starting the webapp with name: " + webAppName + " : message:  " + e.getMessage(), e);
						}
					}
				}

				deleteFile(host);
			} catch (Throwable t) {
				log.error("Exception occurred in starting the apps." + t.getMessage(), t);
			}
		}
	}

	private void handleBeforeStartEvent(StandardHost host) {
		Set<String> appNames = deserializeAndRestoreState(host);
		if(appNames != null) {
			host.setStartChildren(false);
		}
	}

	/**
	 * Serializes the given set of appNames for the host with all the started states.
	 * @param appNames
	 * @param host
	 */
	private void serializeAndStoreObject(Set<String> appNames, StandardHost host)
	{
		File serializedFile = getSerializedFile(host);
		log.info("serializing the apps in started state to : " + serializedFile.getAbsolutePath() + " with set as " + appNames);

		ObjectOutputStream out = null;
		try {
			FileOutputStream fileOut = new FileOutputStream(serializedFile.getPath());
			out = new ObjectOutputStream(fileOut);
			out.writeObject(appNames);
			out.flush();

			log.info("Serialized the state and saved at " + serializedFile);
		} catch (IOException ioe) {
			log.error("Exception while serializeing: " + ioe.getMessage(), ioe);
		} finally {
			if(out != null) {try { out.close();	} catch (Exception ignore) {}};
		}
	}

	/**
	 * Deserializes and returns the set of appNames for the host.
	 * @param host
	 * @return
	 */
	private Set<String> deserializeAndRestoreState(StandardHost host)
	{
		Set<String> appNames = null;
		File serializedFile = getSerializedFile(host);
		if(serializedFile.exists()) {
			log.info("File found to Deserialize at " + serializedFile);

			ObjectInputStream in = null;
			try {
				FileInputStream fileIn = new FileInputStream(serializedFile);
				in = new ObjectInputStream(fileIn);
				appNames = (Set<String>) in.readObject();
	  		} catch (Exception e) {
	  			log.error("Exception occurred while deserializing: " + e.getMessage(), e);
			} finally {
				if(in != null) {
					try {in.close(); } catch (Exception ignore) {}
				}
			}
		} else {
			log.info("No file found to Deserialize at " + serializedFile.getAbsolutePath());
		}
		return appNames;
	}

	private File getSerializedFile(StandardHost host)
	{
		String home = System.getProperty(Globals.CATALINA_HOME_PROP);
		if(home == null) {
			home = System.getProperty("java.io.tmpdir");
		}

		String engineName = host.getDomain();
		String hostName = host.getName();
		StringBuilder path = new StringBuilder();
		path.append(home).append(File.separatorChar)
			.append("work").append(File.separatorChar)
		    .append(engineName).append(File.separatorChar)
		    .append(hostName);

		File serializedPath = new File(path.toString());
		if(!serializedPath.isDirectory()) {
			if(!serializedPath.mkdirs()) {
				log.info("Failed to create the directories or the dir already exists at " + path.toString());
			}
		}

		String serializedFileName = getSerializationFileName(host);
		return new File(serializedPath.getPath(), serializedFileName);
	}

	private String getSerializationFileName(Host host) {
		return host.getName() + ".ser";
	}

	/**
	 * Deletes the serialized file for the host after deserializing it.
	 * @param host
	 */
	private void deleteFile(StandardHost host)
	{
		File file = getSerializedFile(host);
		if (file.exists()) {
			if (file.delete()) {
				log.info("Successfully deleted the file at " + file.getAbsolutePath());
			}
		} else {
			log.info("File does not exist to delete: " + file.getAbsolutePath());
		}
	}

}
