/*
 * Copyright (c) 2009, Ken Gilmer All rights reserved. Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * 
 * Neither the name Ken Gilmer nor the names of other contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.steb;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.ICompareInputLabelProvider;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.part.FileEditorInput;
import org.steb.preferences.PreferenceConstants;

/**
 * Static helper methods for dealing with Eclipse editors. Based on FileUtil of the sunshade project. Sunshade is
 * available at: http://sunshade.sourceforge.net/
 * 
 * Some of sunshade code is based on "fileopen" plugin from Ed Burnette http://www.eclipsepowered.org/
 * 
 * @author kgilmer
 * 
 */
public class EclipseEditorHelper {

    /**
     * Open an eclipse editor for a given file.
     * 
     * @param filename
     * @param create
     * @param workbench
     * @return
     * @throws IOException
     * @throws CoreException
     */
    public static IEditorPart openEclipseEditorForFile(File file, boolean create, final IWorkbench workbench) throws IOException,
        CoreException {
        IEditorPart editorPart = null;

        final IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        IEditorInput input = createEditorInput(file, create);

        if (input != null) {
            String editorId = getEditorId(workbench, file);
            IWorkbenchPage page = window.getActivePage();

            editorPart = page.openEditor(input, editorId);

            // Check to see if the editor open failed, if so use the default text editor instead of the configured
            // editor.
            if (editorPart.getClass().getName().equals("org.eclipse.ui.internal.ErrorEditorPart")) {
                window.getActivePage().closeEditor(editorPart, true);
                editorPart = page.openEditor(input, EditorsUI.DEFAULT_TEXT_EDITOR_ID);
            }

            window.getShell().forceActive();
        }

        return editorPart;
    }

    /**
     * @param workbench
     * @param file
     * @return Id of editor for given file type, defaults to EditorsUI.DEFAULT_TEXT_EDITOR_ID
     */
    private static String getEditorId(IWorkbench workbench, File file) {
        String editorId = EditorsUI.DEFAULT_TEXT_EDITOR_ID;
        IEditorRegistry editorRegistry = workbench.getEditorRegistry();
        IEditorDescriptor descriptor = editorRegistry.getDefaultEditor(file.getName());
        if (descriptor != null && !editorOverride(descriptor.getId())) {
            editorId = descriptor.getId();
        }

        return editorId;
    }

    /**
     * @param id
     * @return true if user via preference item has opted to override this editor with default.
     */
    private static boolean editorOverride(String id) {
        String s = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.P_EDITOR_OVERRIDES);

        return s.indexOf(id) > -1;
    }

    /**
     * @param file
     * @param create
     * @return
     * @throws IOException
     * @throws CoreException
     */
    private static IEditorInput createEditorInput(File file, boolean create) throws IOException, CoreException {
        if (file == null) {
            return null;
        }
        IEditorInput result = null;
        IFile workspaceFile = getWorkspaceRelativeFile(file);
        // If we have a file in the workspace, return the input for it, else
        // return an input for an External file
        if (workspaceFile != null && workspaceFile.getProject().isOpen()) {
            workspaceFile.refreshLocal(IResource.DEPTH_ONE, null);

            if (create && !workspaceFile.exists()) {
                workspaceFile.create(new ByteArrayInputStream(new byte[0]), true, null);
            }
            if (workspaceFile.exists()) {
                result = new FileEditorInput(workspaceFile);
            }
        }
        else {
            if (create && !file.exists()) {
                file.createNewFile();
            }

            if (file.exists()) {
                IFileStore fileStore = EFS.getLocalFileSystem().fromLocalFile(file);

                result = new FileStoreEditorInput(fileStore);
                
            }
        }
        return result;
    }

    /**
     * If File is a workspace relative file, return eclipse resource for it. By workspace relative I mean
     * "project/path/to/file.txt"
     * 
     * @param file
     * @return
     */
    private static IFile getWorkspaceRelativeFile(File file) {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IPath location = null;

        if (file.isAbsolute()) {
            location = new Path(file.getAbsolutePath());
        }
        else {
            location = new Path(workspace.getRoot().getLocation() + File.separator + file.getPath());
        }

        IFile[] files = workspace.getRoot().findFilesForLocation(location);

        if (files.length == 1) {
            return files[0];
        }

        return null;
    }

    public static void createEclipseProjectForDirectory(final File file, boolean b, IWorkbench workbench) {
        Job j = new Job("Create project") {

            @Override
            protected IStatus run(IProgressMonitor monitor) {
                IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

                IProject project = root.getProject(file.getName());
                IProjectDescription desc = project.getWorkspace().newProjectDescription(project.getName());
                try {
                    URI uri = new URI("file://" + file.getAbsolutePath());
                    desc.setLocationURI(uri);

                    project.create(desc, monitor);

                    project.open(monitor);
                }
                catch (Exception e) {
                    return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Failed to create project.", e);
                }

                return Status.OK_STATUS;
            }
        };

        j.schedule();
    }

    /**
     * @param fcfile1
     * @param fcfile2
     * @param b
     * @param workbench
     * @return 
     */
    public static IEditorPart createEclipseCompareEditorForFiles(File fcfile1, File fcfile2, IWorkbench workbench) {
        IEditorPart editorPart = null;

        final IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        CompareEditorInput input = createCompareEditorInput(fcfile1, fcfile2);

        if (input != null) {
            CompareUI.openCompareEditor(input);

            window.getShell().forceActive();
        }

        return editorPart;

    }

    /**
     * @param fcfile1
     * @param fcfile2
     * @return
     */
    private static CompareEditorInput createCompareEditorInput(File file1, File file2) {
        if (file1 == null || file2 == null) {
            return null;
        }
        CompareEditorInput result = null;

        IFileStore fileStore1 = EFS.getLocalFileSystem().fromLocalFile(file1);
        IFileStore fileStore2 = EFS.getLocalFileSystem().fromLocalFile(file2);

        CompareConfiguration config = new CompareConfiguration();
        
        config.setDefaultLabelProvider(new ICompareInputLabelProvider() {
            
            @Override
            public void removeListener(ILabelProviderListener listener) {
            }
            
            @Override
            public boolean isLabelProperty(Object element, String property) {
                return false;
            }
            
            @Override
            public void dispose() {
            }
            
            @Override
            public void addListener(ILabelProviderListener listener) {
            }
            
            @Override
            public String getText(Object element) {
                if (element instanceof DiffNode)
                    return ((DiffNode) element).getName();
                
                return element.toString();
            }
            
            @Override
            public Image getImage(Object element) {
                return null;
            }
            
            @Override
            public String getRightLabel(Object input) {
                if (input instanceof DiffNode)
                    return ((DiffNode) input).getRight().getName();
                
                return input.toString();
            }
            
            @Override
            public Image getRightImage(Object input) {
                if (input instanceof DiffNode)
                    return ((DiffNode) input).getRight().getImage();
                
                return null;
            }
            
            @Override
            public String getLeftLabel(Object input) {
                if (input instanceof DiffNode)
                    return ((DiffNode) input).getLeft().getName();
                
                return input.toString();
            }
            
            @Override
            public Image getLeftImage(Object input) {
                if (input instanceof DiffNode)
                    return ((DiffNode) input).getLeft().getImage();
                
                return null;
            }
            
            @Override
            public String getAncestorLabel(Object input) {
                return input.toString();
            }
            
            @Override
            public Image getAncestorImage(Object input) {
                return null;
            }
        });
        
        result = new StebCompareEditorInput(config, fileStore1, fileStore2);

        return result;
    }
    
    private static class StebCompareEditorInput extends CompareEditorInput {

        private final IFileStore r1;
        private final IFileStore r2;

        /**
         * @param configuration
         */
        public StebCompareEditorInput(CompareConfiguration configuration, IFileStore r1, IFileStore r2) {
            super(configuration);
            this.r1 = r1;
            this.r2 = r2;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.eclipse.compare.CompareEditorInput#prepareInput(org.eclipse.core.runtime.IProgressMonitor)
         */
        @Override
        protected Object prepareInput(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
            Differencer d = new Differencer();

            return d.findDifferences(false, monitor, null, null, new Input(r1, monitor),
                new Input(r2, monitor));
        }

    }

    private static class Input implements ITypedElement, IStreamContentAccessor {
        private final IFileStore fileStore;
        private final IProgressMonitor monitor;

        public Input(IFileStore fileStore, IProgressMonitor monitor) {
            this.fileStore = fileStore;
            this.monitor = monitor;
           
        }

        public String getName() {     
            return fileStore.getName();
        }

        public Image getImage() {
            return null;
        }

        public String getType() {
            String name = fileStore.getName();
            if (name.contains(".")) {
                return name.substring(name.lastIndexOf('.') + 1);
            }
            
            //Try text compare.
            return "txt";
        }

        public InputStream getContents() throws CoreException {
            return fileStore.openInputStream(0, monitor);
        }
    }
}
