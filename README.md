tomcat-extensions
=================

repository for extensions to tomcat

Tomcat-extensions contains two modules. 
1. tomcat6
2. tomcat7
Each of the module contains a pom.xml and a custom Listener which restore the webapp state after the tomcat server is restarted.
Steps to perform to use the above listener are:
1. Build the projects tomcat6 and tomcat7 using mvn clean install.
2. It creates a jar file in the target folder.
3. Copy the above jar in the TOMCAT_HOME/lib/
4. Edit the server.xml file at TOMCAT_HOME/conf/ and add the listener under the host tag.
Example: To add the Listener for tomcat7, add the following under host tag. 
<Listener className="com.cloudjee.extensions.tomcat7.AppStateRetainerHostListener" />
NOTE: If we have multiple hosts, we need to add the listener for each of the hosts.
