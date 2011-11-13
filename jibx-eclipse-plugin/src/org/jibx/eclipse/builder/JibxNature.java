package org.jibx.eclipse.builder;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

/**
 * 
 * @author Michael McMahon  <michael.mcmahon@activewire.net>
 *
 */
public class JibxNature implements IProjectNature {

	/**
	 * ID of this project nature
	 */
	public static final String NATURE_ID = "org.jibx.eclipse.JibxNature";

	private IProject project;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.resources.IProjectNature#configure()
	 */
	public void configure() throws CoreException {
		IProjectDescription desc = project.getDescription();
		ICommand[] commands = desc.getBuildSpec();
		
		
		ICommand[] newCommands = new ICommand[commands.length + 1];
//		System.arraycopy(commands, 0, newCommands, 0, commands.length);
//		ICommand command = desc.newCommand();
//		command.setBuilderName(JibxBindingBuilder.BUILDER_ID);
//		newCommands[newCommands.length - 1] = command;

		// this builder must appear BEFORE MyEclipse deployment builder (otherwise the JiBX bound classes
		// wont get pushed onto the appserver)
		int myEclipseIndex = -1;
		for (int i = 0; i < commands.length; ++i) {
			if (commands[i].getBuilderName().equals(JibxBindingBuilder.BUILDER_ID)) {
				return;
			}
			if (commands[i].getBuilderName().equals("com.genuitec.eclipse.ast.deploy.core.DeploymentBuilder")) {
				myEclipseIndex = i;
				break;
			}
			newCommands[i] = commands[i];			
		}
		ICommand jibxBuilder = desc.newCommand();
		jibxBuilder.setBuilderName(JibxBindingBuilder.BUILDER_ID);
		if (-1 == myEclipseIndex) // its not a myeclipse project so add ourself last
			newCommands[newCommands.length - 1] = jibxBuilder;
		else {
			newCommands[myEclipseIndex] = jibxBuilder;
			for (int i=myEclipseIndex; i< commands.length; i++) {
				newCommands[i+1] = commands[i];
			}
		}

		desc.setBuildSpec(newCommands);
		project.setDescription(desc, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.resources.IProjectNature#deconfigure()
	 */
	public void deconfigure() throws CoreException {
		IProjectDescription description = getProject().getDescription();
		ICommand[] commands = description.getBuildSpec();
		for (int i = 0; i < commands.length; ++i) {
			if (commands[i].getBuilderName().equals(JibxBindingBuilder.BUILDER_ID)) {
				ICommand[] newCommands = new ICommand[commands.length - 1];
				System.arraycopy(commands, 0, newCommands, 0, i);
				System.arraycopy(commands, i + 1, newCommands, i,
						commands.length - i - 1);
				description.setBuildSpec(newCommands);
				return;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.resources.IProjectNature#getProject()
	 */
	public IProject getProject() {
		return project;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.resources.IProjectNature#setProject(org.eclipse.core.resources.IProject)
	 */
	public void setProject(IProject project) {
		this.project = project;
	}

}
