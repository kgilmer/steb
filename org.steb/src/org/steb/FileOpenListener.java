/*
 * Copyright (c) 2009, Ken Gilmer
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.PlatformUI;

/**
 * A socket listener that accepts full file paths to open in an Eclipse editor.
 * 
 * Inspired by the sunshade project by Matt Conway. Sunshade is available at:
 * http://sunshade.sourceforge.net/
 * 
 * Some of sunshade code is based on "fileopen" plugin from Ed Burnette
 * http://www.eclipsepowered.org/
 * 
 * @author kgilmer
 * 
 */
public class FileOpenListener extends Thread {

	private ServerSocket socket;
	private final ILog log;
	private volatile boolean running = false;

	public boolean isRunning() {
		return running;
	}

	synchronized public void setRunning(boolean running) {
		this.running = running;
	}

	public FileOpenListener(ILog log, int port) throws IOException {
		super("File Open Listener");
		this.log = log;
		setDaemon(true);

		socket = new ServerSocket(port);
	}

	@Override
	public void run() {
		BufferedReader reader = null;
		Socket s = null;
		running = true;
		while (running) {
			try {
				s = socket.accept();

				// If we have been interrupted exit immediately.
				if (!running) {
					continue;
				}

				// If the request comes from non-local address ignore.
				if (!s.getInetAddress().isLoopbackAddress()) {
					log.log(new Status(IStatus.WARNING, Activator.PLUGIN_ID, "Ignoring a request from non-local client: " + s.getInetAddress().toString()));
					continue;
				}

				reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
				String line = reader.readLine();

				if (line == null || line.trim().length() == 0) {
					continue;
				}
				
				boolean projectMode = false;
				boolean compareMode = false;
				File file = null, cfile1 = null, cfile2 = null;
				
				if (line.trim().startsWith("-p ")) {
					projectMode = true;
					line = line.split(" ")[1];
				} else if (line.trim().startsWith("-c ")) {
				    compareMode = true;
				    String[] fileLines = line.split(" ");
				    
				    if (fileLines.length != 3)
				        continue;
				    
				    cfile1 = new File(fileLines[1]);
				    cfile2 = new File(fileLines[2]);
				}

				if (!compareMode) {
    				file = new File(line);
    
    				if (file.exists() && !file.isFile() && !projectMode) {
    					continue;
    				}
    				
    				if (file.exists() && !file.isDirectory() && projectMode) {
    					continue;
    				}
				} else {				    
				    if (!cfile1.exists()) {
                        continue;
                    }
				    
				    if (!cfile2.exists()) {
                        continue;
                    }
				}
				
				final File ffile = file;
				if (projectMode) {
					PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
						
						public void run() {
							try {
								EclipseEditorHelper.createEclipseProjectForDirectory(ffile, true, PlatformUI.getWorkbench());
							} catch (Exception e) {
								log.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Failed to open editor for file: " + ffile.getAbsolutePath(), e));
							}
						}
	
					});	
				} else if (compareMode) {
				        final File fcfile1 = cfile1, fcfile2 = cfile2;
				        PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
                        
                        public void run() {
                            try {
                                EclipseEditorHelper.createEclipseCompareEditorForFiles(fcfile1, fcfile2, PlatformUI.getWorkbench());
                            } catch (Exception e) {
                                log.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Failed to open editor for file: " + ffile.getAbsolutePath(), e));
                            }
                        }
    
                    }); 
				} else {
					PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
	
						public void run() {
							try {
								EclipseEditorHelper.openEclipseEditorForFile(ffile, true, PlatformUI.getWorkbench());
							} catch (Exception e) {
								log.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Failed to open editor for file: " + ffile.getAbsolutePath(), e));
							}
						}
	
					});
				}
			} catch (IOException e) {
				log.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Problem in listener.", e));
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) {
					}
				}

				if (s != null) {
					try {
						s.close();
					} catch (IOException e) {
					}
				}
			}
		}

		if (socket != null) {
			try {
				socket.close();
				socket = null;
			} catch (IOException e) {
			}
		}		
	}
}