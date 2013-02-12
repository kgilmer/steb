/*
 * Copyright (c) 2009 - 2011, Ken Gilmer
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without modification, 
 * are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list 
 * of conditions and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright notice, 
 * this list of conditions and the following disclaimer in the documentation 
 * and/or other materials provided with the distribution.
 * 
 * Neither the name Ken Gilmer nor the names of other contributors may be used 
 * to endorse or promote products derived from this software without specific 
 * prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE 
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED 
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR 
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.steb;

import java.io.IOException;
import java.net.Socket;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.osgi.framework.BundleContext;
import org.steb.preferences.PreferenceConstants;


/**
 * Steb Activator.
 * @author kgilmer
 *
 */
public class Activator extends Plugin implements IStartup, IPropertyChangeListener {
	public static final String PLUGIN_ID = "org.steb";
	private static int currentPort = 4404;
	private volatile ScopedPreferenceStore preferenceStore;
	private static Activator plugin;

	private FileOpenListener listener;
	private Command toggleCommand;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.runtime.Plugins#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		ICommandService cs = (ICommandService) PlatformUI.getWorkbench().getService(ICommandService.class);
		toggleCommand = cs.getCommand("org.steb.commands.sampleCommand");
		
		//Set the command to false by default, then enable if the listener is configured to be on and it's able to open the port.
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

			@Override
			public void run() {
				toggleCommand.getState("org.eclipse.ui.commands.toggleState").setValue(Boolean.FALSE);
			}
		});		
		
		getPreferenceStore();
		currentPort = preferenceStore.getInt(PreferenceConstants.LISTENER_PORT);
		if (preferenceStore.getBoolean(PreferenceConstants.LISTENER_ENABLED)) {
			try {
				startupListener();		
				PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
					
					@Override
					public void run() {
						try {
							HandlerUtil.toggleCommandState(toggleCommand);
						} catch (ExecutionException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				});		
			} catch (IOException e) {
				//Unable to startup listener.
				preferenceStore.setValue(PreferenceConstants.LISTENER_ENABLED, false);
			}
		}
		
		preferenceStore.addPropertyChangeListener(this);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.runtime.Plugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		preferenceStore.removePropertyChangeListener(this);
		shutdownListener();
		preferenceStore = null;
		super.stop(context);
	}

	public static Activator getDefault() {
		return plugin;
	}

	public void earlyStartup() {
		// Do nothing here, but this makes our plugin load so we can create the
		// listener.
	}

	/**
	 * Startup the listener.
	 * @throws IOException 
	 */
	public void startupListener() throws IOException {
		if (listener != null) {
			return;
		}
        listener = new FileOpenListener(this.getLog(), currentPort);
        listener.start();
		
	}

	/**
	 * Shutdown the listener thread.
	 */
	public void shutdownListener() {
		if (listener == null) {
			return;
		}

		listener.setRunning(false);
		//Send poison pill so listener wakes up to exit.
		try {
			Socket s = new Socket("127.0.0.1", currentPort);
			s.getOutputStream().write('\n');
			s.close();
		} catch (Exception e) {
		}
		listener = null;
	}

	synchronized public IPreferenceStore getPreferenceStore() {
		// Create the preference store lazily.
		if (preferenceStore == null) {
			preferenceStore = new ScopedPreferenceStore(new InstanceScope(), getBundle().getSymbolicName());

		}
		return preferenceStore;
	}

	public void propertyChange(PropertyChangeEvent event) {
		// Handle activating/deactivating listener.
		if (event.getProperty().equals(PreferenceConstants.LISTENER_ENABLED)) {
			boolean oldVal = ((Boolean) event.getOldValue()).booleanValue();
			boolean newVal = ((Boolean) event.getNewValue()).booleanValue();
			try {
				if (oldVal == false && newVal == true && listener == null) {
					startupListener();
				} else if (oldVal == true && newVal == false && listener != null) {
					shutdownListener();
				}
			
				HandlerUtil.toggleCommandState(toggleCommand);
			} catch (Exception e) {
				this.getLog().log(new Status(IStatus.WARNING, Activator.PLUGIN_ID, "Unable to start steb listener. (" + e.getMessage() + ")"));
				preferenceStore.setValue(PreferenceConstants.LISTENER_ENABLED, Boolean.FALSE);
				try {
					HandlerUtil.toggleCommandState(toggleCommand);
				} catch (ExecutionException e1) {					
				}
			}
		} else if (event.getProperty().equals(PreferenceConstants.LISTENER_PORT)) {
			// Handle change of port # to listen on.
			String nstr = event.getNewValue().toString();
			String ostr = event.getOldValue().toString();

			// Do nothing, port hasn't changed.
			if (nstr.equals(ostr)) {
				return;
			}

			if (listener != null) {
				shutdownListener();
				currentPort = Integer.parseInt(nstr);
				try {
					startupListener();
				} catch (IOException e) {
					this.getLog().log(new Status(IStatus.WARNING, Activator.PLUGIN_ID, "Unable to start steb listener. (" + e.getMessage() + ")"));
					preferenceStore.setValue(PreferenceConstants.LISTENER_ENABLED, Boolean.FALSE);
					try {
						HandlerUtil.toggleCommandState(toggleCommand);
					} catch (ExecutionException e1) {					
					}
				}
			} else {
				currentPort = Integer.parseInt(nstr);
			}
		}
	}
}
