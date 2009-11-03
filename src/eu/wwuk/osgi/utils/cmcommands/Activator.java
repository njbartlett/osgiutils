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

import org.eclipse.osgi.framework.console.CommandProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;

public class Activator implements BundleActivator {

	private ServiceTracker cmtracker;

	public void start(BundleContext context) throws Exception {
		cmtracker = new ServiceTracker(context, ConfigurationAdmin.class.getName(), null);
		cmtracker.open();
		
		ConfigAdminCommandProvider commands = new ConfigAdminCommandProvider(cmtracker);
		context.registerService(CommandProvider.class.getName(), commands, null);
	}

	public void stop(BundleContext context) throws Exception {
		cmtracker.close();
	}

}
