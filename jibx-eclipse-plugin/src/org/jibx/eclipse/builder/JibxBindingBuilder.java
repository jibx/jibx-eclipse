package org.jibx.eclipse.builder;

import java.io.File;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.jibx.binding.Compile;
import org.jibx.eclipse.parser.MappingSAXHandler;
import org.jibx.eclipse.properties.JibxPropertyPage;
import org.osgi.framework.Bundle;

/**
 * 
 * @author Michael McMahon  <michael.mcmahon@activewire.net>
 *
 */
public class JibxBindingBuilder extends IncrementalProjectBuilder {
	
	public static final String BUILDER_ID = "org.jibx.eclipse.JibxBindingBuilder";
	private Compile compile = null;
	private MessageConsole console = null;
	private MessageConsoleStream out;

	// location of jibx libx (determined dynamically by introspecting the plugin)
	private static String jibxRuntimeJarLocation = null;
	
	// which java classes are mapped by jibx? (determined by parsing the XML mappings files)
	private Map mappedClasses = null;
	
	private String jibxMappingsFolder=JibxPropertyPage.DEFAULT_FOLDER;
	private boolean verbose = false;
	
	// list of JiBX mappings files (determined dynamically by traversing into the jibxMappingsFolder)
	private String [] mappings = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.internal.events.InternalBuilder#build(int,
	 *      java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
			throws CoreException {
		out = console.newMessageStream();
		out.println("\n\nJiBX build begin @ " + new java.util.Date());
		IProject project = getProject();
		jibxMappingsFolder = project.getPersistentProperty(new QualifiedName("", JibxPropertyPage.JIBX_SOURCE_FOLDER_PROPERTY));
		verbose = "true".equals(project.getPersistentProperty(new QualifiedName("", JibxPropertyPage.JIBX_VERBOSE_PROPERTY)));
		
		if (null == jibxMappingsFolder)
			jibxMappingsFolder = JibxPropertyPage.DEFAULT_FOLDER;
		if (kind == FULL_BUILD) {
			mappedClasses = null;
			out.println("JiBX build Workspace invoked FULL build");
			bindAll(monitor);
		} else {
			IResourceDelta delta = getDelta(project);
			if (delta == null)
				bindAll(monitor);
			else {
				if (null == mappedClasses) { // this only happens at startup as a project is being opened
					doMappings(project, JavaCore.create(project));						
				}
				incrementalBuild(delta, monitor);
			}
		}
		out.println("JiBX build end @ " + new java.util.Date());
		return null;
	}

	/**
	 * Recursive function to add JARs to classpath hash (including sub-projects)
	 * @param classPathArray
	 * @param javaProject
	 * @throws JavaModelException
	 */
	private IJavaProject addPaths(Set classPathSet, IProject project) throws JavaModelException  {
		IJavaProject javaProject = JavaCore.create(project);
		IPath l = javaProject.getOutputLocation();
		l = l.removeFirstSegments(1);
		IFolder outputFolder = project.getFolder(javaProject.getOutputLocation().removeFirstSegments(1));
		classPathSet.add(outputFolder.getRawLocation().toOSString());
		IClasspathEntry [] cpe = javaProject.getResolvedClasspath(false);
		
		for (int i=0; i<cpe.length; i++) {
			if (IClasspathEntry.CPE_LIBRARY == cpe[i].getEntryKind()) {
				IPackageFragmentRoot[] pfrs = javaProject.findPackageFragmentRoots(cpe[i]);
				for (IPackageFragmentRoot pfr : pfrs) {
					IPath path;
					if (pfr.isExternal()) {
						path = pfr.getPath();
					} else {
						path = project.getWorkspace().getRoot().getLocation().append(pfr.getPath());
					}
					classPathSet.add(path.toOSString());
				}
			} else if (IClasspathEntry.CPE_PROJECT == cpe[i].getEntryKind()) {
				IProject child = javaProject.getProject().getWorkspace().getRoot().getProject(cpe[i].getPath().toString());
				addPaths(classPathSet, child); // recurse
			} else
				System.out.println(cpe[i]);
		}	
		return javaProject;
	}
	
	private void bindAll(IProgressMonitor monitor) throws CoreException {
		out.println("JiBX bindAll begin");
		IProject project = getProject();
		Set classPathSet = new TreeSet();
		IJavaProject javaProject = addPaths(classPathSet, project);
		
		classPathSet.add(".");
		String s = project.getPersistentProperty(new QualifiedName("", JibxPropertyPage.JIBX_VERSION_PROPERTY));
		if (null == s)
			s = JibxPropertyPage.DEFAULT_VERSION;
		classPathSet.add(jibxRuntimeJarLocation + "jibx-bind-" + s + ".jar");
		classPathSet.add(jibxRuntimeJarLocation + "jibx-run-" + s + ".jar");
		classPathSet.add(jibxRuntimeJarLocation + "bcel.jar");
		String [] classPaths = (String []) classPathSet.toArray(new String[] {});
		String cp = "JiBX bindAll classpath: ";
		for (int i=0; i<classPaths.length; i++)
			cp += "\n\t" + classPaths[i];
		
		out.println(cp);
		PrintStream sysout = System.out;
		PrintStream syserr = System.err;
		try {
			doMappings(project, javaProject);
			compile.setVerbose(verbose);
			if (verbose) {
				PrintStream po = new PrintStream(out);
				System.setOut(po);
				System.setErr(po);
			}
			compile.compile(classPaths, mappings);
			// must now inform Eclipse that files have been added/changed/deleted
			IPath l = javaProject.getOutputLocation();
			l = l.removeFirstSegments(1);
			IFolder outputFolder = project.getFolder(javaProject.getOutputLocation().removeFirstSegments(1));			
			outputFolder.refreshLocal(IProject.DEPTH_INFINITE, monitor);
		} catch (Exception e) {
			e.printStackTrace();
			out.println(e.getMessage());
			throw new CoreException(new Status(IResourceStatus.BUILD_FAILED, "", 0, "an error occurred", e));
		} finally {
			System.setOut(sysout);
			System.setErr(syserr);
		}
		out.println("JiBX bindAll end");
	}


	private void doMappings(IProject project, IJavaProject javaProject) throws CoreException {
		out.println("JiBX doMappings begin");
		IFolder mappingsFolder = project.getFolder(jibxMappingsFolder);
		FindMappingFilesVisitor visitor = new FindMappingFilesVisitor();
//			out.println("JiBX bindAll about to find mapping files");
		try {
			mappingsFolder.accept(visitor);			
		} catch (CoreException ce) {
			if (ce.getMessage().endsWith("does not exist."))
				out.println("ERROR!! Check JiBX properties on the project: Mappings Folder does not exist (" + jibxMappingsFolder + ")");
			throw ce;
		}
		mappings = visitor.getMappings();
		String mp = "JiBX doMappings using these mapping files: ";
		for (int i=0; i<mappings.length; i++)
			mp += "\n\t" + mappings[i];			
		out.println(mp);			
		if (null == mappedClasses)
			mappedClasses = parseMappedClasses(mappings, javaProject.getOutputLocation().toOSString());
		out.println("JiBX doMappings end");
	}
	
	protected void incrementalBuild(IResourceDelta delta,
			IProgressMonitor monitor) throws CoreException {
		out.println("JiBX incrementalBuild begin");
		JibxDeltaVisitor visitor = new JibxDeltaVisitor();
		delta.accept(visitor);
		out.println("JiBX incrementalBuild did JibxDeltaVisitor result=" + visitor.getNeedsBinding());
		if (visitor.getNeedsBinding())
			bindAll(monitor);
		out.println("JiBX incrementalBuild end");		
	}	
	
	/**
	 * recurse down a folder structure and keep a (flat) array of all .xml files therein
	 * @author mcmahon
	 *
	 */
	class FindMappingFilesVisitor implements IResourceVisitor {
		private ArrayList mappingFiles = new ArrayList();

		public boolean visit(IResource resource) throws CoreException {
			if (resource instanceof IFile) {				
				if  (resource.getName().endsWith(".xml")) {
					IFile file = (IFile) resource;
					mappingFiles.add(file.getRawLocation().toOSString());
				}
			}
			return true;
		}
		
		public String [] getMappings() {
//			private String jibxMappingsFolder="/home/mcmahon/phh/java/jibx/runtime-testWorkspace/connectorsWeb/src/main/jibx";
//			return new String[] { 			
//					jibxMappingsFolder + "/RetitlePrerequisitesVO-binding.xml",					
//					jibxMappingsFolder + "/RetitleVehicleVO-binding.xml",
//					jibxMappingsFolder + "/retitleCandidatesVO-binding.xml",
//					jibxMappingsFolder + "/RetitleNotesVO-binding.xml",
//					jibxMappingsFolder + "/RetitleDualAddressVO-binding.xml",
//					};			
			return (String []) mappingFiles.toArray(new String[] {});
		}		
	}
	
	
	class JibxDeltaVisitor implements IResourceDeltaVisitor {
		
		private boolean _needsBinding = false;
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.resources.IResourceDelta)
		 */
		public boolean visit(IResourceDelta delta) throws CoreException {
			// first check if we already have determined that binding is needed
			if (_needsBinding)
				return false; // stop visiting children
			IResource resource = delta.getResource();
			// we must look for 2 possible conditions:
			// 		1. a mapping file has changed
			// 		2. a mapped java class has changed
			if (resource instanceof IFile) {
				IFile file = (IFile) resource;
				if  (resource.getName().endsWith(".xml")) {
					// an XML file has changed .. now check to see if its in our mappings folder?
					IPath pr = file.getProjectRelativePath();
					if (0 == pr.toString().indexOf(jibxMappingsFolder)) {
						// in addition to doing the JiBX bind, we must also re-parse to see if our mapped classes have changed
						mappedClasses = null;
						_needsBinding = true;
					}
				} else if (resource.getName().endsWith(".class")) {
					// a class file has changed .. now check to see if this class is JiBX bound
					if (null == mappedClasses || mappedClasses.containsKey(resource.getFullPath().toOSString()))
						_needsBinding = true;
				}
			}
			if (_needsBinding)
				out.println("JiBX visit resource dirty: " + resource.getName());
			return ! _needsBinding; // TRUE means continue visiting children, FALSE means stop visiting
		}
		
		public boolean getNeedsBinding() {
			return _needsBinding;
		}
	}	
	
	protected Map parseMappedClasses(String [] files, String outputLocation) throws CoreException {
		out.println("JiBX parseMappedClasses begin");
		SAXParserFactory factory = SAXParserFactory.newInstance();
		MappingSAXHandler handler = new MappingSAXHandler();
		try {
			SAXParser saxParser = factory.newSAXParser();
			for (int i=0; i<files.length; i++)
				saxParser.parse( new File(files[i]), handler );

		} catch (Throwable err) {
		        err.printStackTrace ();
		}		
		  // now interate through and reformat from
		  //   com.phh.retitle.dal.vos.RetitleCandidatesVO
		  // becomes
		  //   /connectorsWeb/src/main/java/com/phh/retitle/dal/vos/RetitleCandidatesVO.class 
		Map mappedClasses = new HashMap();
		String mc="JiBX parseMappedClasses watching these mapped classes:";
		for (Iterator it=handler.getMappedClasses().iterator(); it.hasNext(); ) {
			String className = (String) it.next();
			String s = outputLocation + File.separator + className.replace('.', File.separatorChar) + ".class";
			if (null == mappedClasses.put(s, s))
				mc += "\n\t" + s;
		}
		out.println(mc);
		out.println("JiBX parseMappedClasses end");
        return mappedClasses;
	}	


	protected void startupOnInitialize() {
		super.startupOnInitialize();		
		console = findConsole("JiBX");
		compile = new Compile(false, false, false, false, false, false);
		compile.setSkipValidate(false);
		if (jibxRuntimeJarLocation == null) {
			try {
				
				
				Bundle bundle = Platform.getBundle("org.jibx.eclipse");
				URL entry = bundle.getEntry("/lib");
			    String path = FileLocator.toFileURL(entry).getPath();
			    String fixedPath = new File(path).getPath();
//				Path path = new Path("lib");
//				URL localdir1 = FileLocator.find(bundle, path, null);								
//				URL localdir = Platform.asLocalURL(Platform.getBundle("org.jibx.eclipse")
//						.getEntry("/"));				
				jibxRuntimeJarLocation = fixedPath + File.separator;
				out = console.newMessageStream();
				out.println("RuntimeJarLocation: " + jibxRuntimeJarLocation);				
			} catch (Exception e) {
				e.printStackTrace();
			}			
		}	
	}
		
	private MessageConsole findConsole(String name) {
	      ConsolePlugin plugin = ConsolePlugin.getDefault();
	      IConsoleManager conMan = plugin.getConsoleManager();
	      IConsole[] existing = conMan.getConsoles();
	      for (int i = 0; i < existing.length; i++)
	         if (name.equals(existing[i].getName()))
	            return (MessageConsole) existing[i];
	      //no console found, so create a new one
	      MessageConsole myConsole = new MessageConsole(name, null);
	      conMan.addConsoles(new IConsole[]{myConsole});
	      return myConsole;
	   }		
}
