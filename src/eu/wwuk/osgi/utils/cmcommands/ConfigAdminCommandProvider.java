/*******************************************************************************
 * Copyright (c) 2009 Neil Bartlett.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Neil Bartlett - initial API and implementation
 ******************************************************************************/
package eu.wwuk.osgi.utils.cmcommands;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Properties;

import org.eclipse.osgi.framework.console.CommandInterpreter;
import org.eclipse.osgi.framework.console.CommandProvider;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;

public class ConfigAdminCommandProvider implements CommandProvider {
	
	private static final Object ALIAS_KEY = "_alias_pid";
	private final ServiceTracker cmTracker;
	
	public ConfigAdminCommandProvider(ServiceTracker cmTracker) {
		this.cmTracker = cmTracker;
	}

	public String getHelp() {
		return "---Configuration Admin---\n"
				+ "\tinstallConfig <url> - install a configuration properties file\n"
				+ "\tlistConfigs - list configurations\n"
				+ "\tshowConfig <pid> - show contents of specified configuration\n"
				+ "\tdeleteConfig <pid> - delete specified configuration";
	}
	
	public void _installConfig(CommandInterpreter ci) throws IOException, InvalidSyntaxException {
		String argument = ci.nextArgument();
		if(argument == null) {
			ci.println("Usage: installConfig <url>");
		} else {
			ConfigurationAdmin cm = (ConfigurationAdmin) cmTracker.getService();
			if(cm == null) {
				ci.println("Configuration Admin service not available");
				return;
			}
			URL url = new URL(argument);
			InputStream stream = null;
			try {
				stream = url.openStream();
				String pid = installProperties(url.getPath(), stream, cm);
				ci.println("Installed PID: " + pid);
			} finally {
				if(stream != null) stream.close();
			}
		}
	}
	
	public void _listConfigs(CommandInterpreter ci) throws IOException, InvalidSyntaxException {
		ConfigurationAdmin cm = (ConfigurationAdmin) cmTracker.getService();
		if(cm == null) {
			ci.println("Configuration Admin service not available");
			return;
		}
		Configuration[] configs = cm.listConfigurations(null);
		if(configs == null || configs.length == 0) {
			ci.println("No configurations found");
		} else {
			for (int i = 0; i < configs.length; i++) {
				StringBuffer buffer = new StringBuffer();
				buffer.append("PID: ").append(configs[i].getPid());
				if(configs[i].getFactoryPid() != null) {
					buffer.append(", Factory_PID: ").append(configs[i].getFactoryPid());
				}
				String aliasPid = (String) configs[i].getProperties().get(ALIAS_KEY);
				if(aliasPid != null) {
					buffer.append(", Alias_PID: ").append(aliasPid);
				}
				ci.println(buffer.toString());
			}
		}
	}
	
   public void _showConfig(CommandInterpreter ci) throws IOException, InvalidSyntaxException {
		String pid = ci.nextArgument();
		if(pid == null) {
			ci.println("Usage: showConfig <pid>");
		} else {
			ConfigurationAdmin cm = (ConfigurationAdmin) cmTracker.getService();
			if(cm == null) {
				ci.println("Configuration Admin service not available");
				return;
			}

			Configuration config = findConfig(cm, pid);
			if(config == null) {
				ci.println("No configurations matching PID " + pid);
			} else {
			   @SuppressWarnings("unchecked") Dictionary props = config.getProperties();
            @SuppressWarnings("unchecked") Enumeration keys = props.keys();
				StringBuffer buffer = new StringBuffer();
				
				while(keys.hasMoreElements()) {
					String key = (String) keys.nextElement();
					String value = (String) props.get(key);
					
					buffer.append('\t').append(key).append('=').append(value).append('\n');
				}
				
				ci.print(buffer.toString());
			}
		}
	}
	
	public void _deleteConfig(CommandInterpreter ci) throws IOException {
		String pid = ci.nextArgument();
		if(pid == null) {
			ci.println("Usage: deleteConfig <pid>");
		} else {
			ConfigurationAdmin cm = (ConfigurationAdmin) cmTracker.getService();
			if(cm == null) {
				ci.println("Configuration Admin service not available");
				return;
			}
			
			Configuration config = findConfig(cm, pid);
			if(config == null) {
				ci.println("No configurations matching PID " + pid);
			} else {
				String realPid = config.getPid();
				config.delete();
				ci.println("Deleted configuration with PID = " + realPid);
			}
		}
	}
	
	private Configuration findConfig(ConfigurationAdmin cm, String param) throws IOException {
		// Lookup by simple pid
		StringBuffer buf = new StringBuffer();
		buf.append('(').append(Constants.SERVICE_PID).append('=').append(param).append(')');
		
		try {
			Configuration[] results = cm.listConfigurations(buf.toString());
			if(results != null && results.length > 0) {
				return results[0];
			}
		} catch (InvalidSyntaxException e) {
			// Shouldn't happen
			throw new RuntimeException(e);
		}
		
		// Lookup using factory and alias pids
		String[] split = splitPids(param);
		String aliasPid = split[0]; String factoryPid = split[1];
		if(factoryPid != null) {
			buf.setLength(0);
			buf.append("(&");
			buf.append('(').append(ConfigurationAdmin.SERVICE_FACTORYPID).append('=').append(factoryPid).append(')');
			buf.append('(').append(ALIAS_KEY).append('=').append(aliasPid).append(')');
			buf.append(')');
			try {
				Configuration[] results = cm.listConfigurations(buf.toString());
				if(results != null && results.length > 0) {
					return results[0];
				}
			} catch (InvalidSyntaxException e) {
				// Shouldn't happen
				throw new RuntimeException(e);
			}
		}
		
		return null;
	}

	private String installProperties(String path, InputStream stream, ConfigurationAdmin cm) throws IOException, InvalidSyntaxException {
		String fullFileName;
		int lastSlashIndex = path.lastIndexOf('/');
		if(lastSlashIndex < 0) {
			fullFileName = path;
		} else {
			fullFileName = path.substring(lastSlashIndex + 1);
		}
		
		String fileName;
		int lastDotIndex = fullFileName.lastIndexOf('.');
		if(lastDotIndex < 0) {
			fileName = fullFileName;
		} else {
			fileName = fullFileName.substring(0, lastDotIndex);
		}
		
		String[] split = splitPids(fileName);
		String pid = split[0];
		String factoryPid = split[1];
		
		Properties props = new Properties();
		props.load(stream);
		
		Configuration config;
		if(factoryPid == null) {
			config = cm.getConfiguration(pid, null);
		} else {
			props.put(ALIAS_KEY, pid);
			Configuration[] configs = cm.listConfigurations("(" + ALIAS_KEY + "=" + pid + ")");
			if(configs == null || configs.length == 0) {
				config = cm.createFactoryConfiguration(factoryPid, null);
			} else {
				config = configs[0];
			}
		}
		if(config.getBundleLocation() != null) {
			config.setBundleLocation(null);
		}
		config.update(props);
		return config.getPid();
	}
	
	/**
	 * Split a configuration pid string into its PID and Factory PID components
	 * @return [pid, factoryPid]
	 */
	private String[] splitPids(String original) {
		String[] result = new String[2];
		
		int hyphenIndex = original.lastIndexOf('-');
		if(hyphenIndex < 0) {
			result[1] = null;
			result[0] = original;
		} else {
			result[1] = original.substring(0, hyphenIndex);
			result[0] = original.substring(hyphenIndex + 1);
		}
		
		return result;
	}

}
