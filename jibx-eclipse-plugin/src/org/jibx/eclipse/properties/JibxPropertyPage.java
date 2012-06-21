package org.jibx.eclipse.properties;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PropertyPage;

/**
 * 
 * @author Michael McMahon  <michael.mcmahon@activewire.net>
 *
 */
public class JibxPropertyPage extends PropertyPage {

	private static final String JIBX_SOURCE_FOLDER_TITLE = "&JiBX Mappings Folder:";
	public static final String JIBX_SOURCE_FOLDER_PROPERTY = "JIBX_SOURCE_FOLDER";
	public static final String DEFAULT_FOLDER = "src/main/config";
	private static final int TEXT_FIELD_WIDTH = 50;
	private Text jibxSourceFolder;
	private Button verboseButton;
	
	private static final String JIBX_VERBOSE_TITLE = "&Verbose Compilation";
	public static final String JIBX_VERBOSE_PROPERTY = "JIBX_VERBOSE";

	private Button [] versionButton;
	private static final String [] JIBX_VERSION_TITLES = new String [] {"1.2.4"}; // {"1.2.2"}; // {"1.2.1"}; // "1.2", "1.1.6.a"};
	public static final String DEFAULT_VERSION =  JIBX_VERSION_TITLES[0];
	public static final String JIBX_VERSION_PROPERTY = "JIBX_VERSION";
	private String jibxVersion;


	/**
	 * Constructor for JibxPropertyPage.
	 */
	public JibxPropertyPage() {
		super();
	}

	private void addSecondSection(Composite parent) {
		Composite composite = createDefaultComposite(parent);

		Label folderLabel = new Label(composite, SWT.NONE);
		folderLabel.setText(JIBX_SOURCE_FOLDER_TITLE);

		jibxSourceFolder = new Text(composite, SWT.SINGLE | SWT.BORDER);
		GridData gd = new GridData();
		gd.widthHint = convertWidthInCharsToPixels(TEXT_FIELD_WIDTH);
		jibxSourceFolder.setLayoutData(gd);
		
		
		
		try {
			IAdaptable iadapt = getElement();
			IResource proj = (IResource) iadapt.getAdapter(IResource.class);
			String folder = proj.getPersistentProperty(
					new QualifiedName("", JIBX_SOURCE_FOLDER_PROPERTY));
			jibxSourceFolder.setText((folder != null) ? folder : DEFAULT_FOLDER);
		} catch (CoreException e) {
			jibxSourceFolder.setText(DEFAULT_FOLDER);
		}
		
		verboseButton = new Button(composite, SWT.CHECK);				
		verboseButton.setText(JIBX_VERBOSE_TITLE);
		
		
		try {
			IAdaptable iadapt = getElement();
			IResource proj = (IResource) iadapt.getAdapter(IResource.class);			
			String verbose = proj.getPersistentProperty(
					new QualifiedName("", JIBX_VERBOSE_PROPERTY));
			verboseButton.setSelection("true".equals(verbose));
		} catch (CoreException e) {
			verboseButton.setSelection(false);
		}		
		// just pad the right cell
		new Composite(composite, SWT.NONE);
		
		
		Label versionLabel = new Label(composite, SWT.NONE | SWT.RIGHT);
		versionLabel.setText("JiBX Version:");

		String jibxVersion = null;
		try {
			IAdaptable iadapt = getElement();
			IResource proj = (IResource) iadapt.getAdapter(IResource.class);			
			jibxVersion = proj.getPersistentProperty(
					new QualifiedName("", JIBX_VERSION_PROPERTY));
		} catch (CoreException e) {
			verboseButton.setSelection(false);
		}		
		
		Composite versionComposite = new Composite(composite, SWT.BORDER);
		GridLayout versionLayout = new GridLayout();
		versionLayout.numColumns = 1;
		versionComposite.setLayout(versionLayout);
		GridData data = new GridData();
		data.verticalAlignment = GridData.FILL;
		data.horizontalAlignment = GridData.FILL;
		versionComposite.setLayoutData(data);

		if (null == jibxVersion)
			jibxVersion = DEFAULT_VERSION;
		versionButton = new Button[JIBX_VERSION_TITLES.length];
		for (int i=0; i<JIBX_VERSION_TITLES.length; i++) {
			versionButton[i] = new Button(versionComposite, SWT.RADIO);				
			versionButton[i].setText(JIBX_VERSION_TITLES[i]);			
			versionButton[i].setSelection(jibxVersion.equals(JIBX_VERSION_TITLES[i]));
		}

	}

	/**
	 * @see PreferencePage#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		composite.setLayout(layout);
		GridData data = new GridData(GridData.FILL);
		data.grabExcessHorizontalSpace = true;
		composite.setLayoutData(data);
		addSecondSection(composite);
		return composite;
	}

	private Composite createDefaultComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		composite.setLayout(layout);

		GridData data = new GridData();
		data.verticalAlignment = GridData.FILL;
		data.horizontalAlignment = GridData.FILL;
		composite.setLayoutData(data);

		return composite;
	}

	protected void performDefaults() {
		jibxSourceFolder.setText(DEFAULT_FOLDER);
		verboseButton.setSelection(false);
	}
	
	public boolean performOk() {
		// store the value in the momento text field
		try {
			IAdaptable iadapt = getElement();
			IResource proj = (IResource) iadapt.getAdapter(IResource.class);
			proj.setPersistentProperty(new QualifiedName("", JIBX_SOURCE_FOLDER_PROPERTY),
				jibxSourceFolder.getText());
			proj.setPersistentProperty(new QualifiedName("", JIBX_VERBOSE_PROPERTY),
					verboseButton.getSelection() ? "true" : "false");
			proj.setPersistentProperty(new QualifiedName("", JIBX_VERSION_PROPERTY),
					jibxVersion);					
			
// TODO rebuild			resource.getProject().build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
		} catch (CoreException e) {
			return false;
		}
		return true;
	}

}