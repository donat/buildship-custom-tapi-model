package org.eclipse.buildship.sample;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.sample.plugins.toolingapi.custom.CustomModel;
import org.gradle.tooling.model.GradleProject;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.progress.UIJob;

import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;

public class ShowJavaPluginApplicationHandler extends AbstractHandler {

    @Override
    public Object execute(final ExecutionEvent event) throws ExecutionException {
        CustomModelJob job = new CustomModelJob();
        job.addJobChangeListener(new JobChangeAdapter() {
            @Override
            public void done(IJobChangeEvent ignore) {
                new UIJob("") {

                    @Override
                    public IStatus runInUIThread(IProgressMonitor monitor) {
                        try {
                            displayMessage();
                        } catch (ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                        return Status.OK_STATUS;
                    }

                    private void displayMessage() throws ExecutionException {
                        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
                        MessageDialog.openInformation(window.getShell(), "Gradle java plugin application info", job.javaPluginInfo);
                    }
                }.schedule();
            }
        });
        job.schedule();
        return null;
    }

    private class CustomModelJob extends Job {

        private String javaPluginInfo;

        public CustomModelJob() {
            super("Check if project applies the 'java' plugin");
        }

        @Override
        protected IStatus run(IProgressMonitor monitor) {
            SubMonitor progress = SubMonitor.convert(monitor, 3);
            String initScriptPath = createInitScript(Activator.getBundleLocation(), progress.newChild(1));
            Collection<GradleBuild> gradleBuilds = collectGradleBuilds(progress.newChild(1));
            progress.setWorkRemaining(gradleBuilds.size());
            this.javaPluginInfo = gradleBuilds
                .stream()
                .map(gradleBuild -> collectJavaPluginInfo(gradleBuild, initScriptPath, progress.newChild(1)))
                .flatMap(List::stream)
                .collect(Collectors.joining(System.getProperty("line.separator")));
            return Status.OK_STATUS;
        }
    }

    private static String createInitScript(File pluginLocation, IProgressMonitor monitor) {
        String initScriptContent = ""
                + "\n initscript {"
                + "\n     repositories {"
                + "\n         maven {"
                + "\n             url new File('" + pluginLocation.getAbsolutePath() + "/custom-model/repo').toURI().toURL()"
                + "\n         }"
                + "\n     }"
                + "\n"
                + "\n     dependencies {"
                + "\n         classpath 'org.gradle.sample.plugins.toolingapi:plugin:1.0'"
                + "\n     }"
                + "\n }"
                + "\n"
                + "\n allprojects {"
                + "\n    apply plugin: org.gradle.sample.plugins.toolingapi.custom.ToolingApiCustomModelPlugin"
                + "\n }";

        File initScript = new File(System.getProperty("java.io.tmpdir"), "init.gradle");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(initScript));
            writer.write(initScriptContent);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store init script");
        }

        return initScript.getAbsolutePath();
    }

    private static Collection<GradleBuild> collectGradleBuilds(IProgressMonitor monitor) {
        Set<GradleBuild> result = new HashSet<>();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            GradleCore.getWorkspace().getBuild(project).ifPresent(gradleProject -> result.add(gradleProject));
        }
        return result;
    }

    private static List<String> collectJavaPluginInfo(GradleBuild gradleBuild, String initScriptPath, IProgressMonitor monitor) {
        try {
            return gradleBuild.withConnection(connection -> {
                CustomModel model = connection.model(CustomModel.class).withArguments("--init-script", initScriptPath).get();
                return getAllGradleProjects(connection.getModel(GradleProject.class))
                    .stream()
                    .map(gradleProject -> {
                            File projectDirectory = gradleProject.getProjectDirectory();
                            return projectDirectory.getAbsolutePath()
                            +  " has java plugin: " + model.hasPlugin(projectDirectory, "org.gradle.api.plugins.JavaPlugin");
                        })
                    .collect(Collectors.toList());
            }, monitor);
        } catch (Exception e) {
            Activator.getInstance().getLog().log(new Status(IStatus.WARNING, "org.eclipse.buildship.sample.custommodel", "Failed to query custom model", e));
            return Collections.emptyList();
        }
    }

    private static Set<GradleProject> getAllGradleProjects(GradleProject root) {
        return getAllGradleProjects(root, new HashSet<>());
    }

    private static Set<GradleProject> getAllGradleProjects(GradleProject project, HashSet<GradleProject> result) {
        result.add(project);
        project.getChildren().forEach(p -> getAllGradleProjects(p, result));
        return result;
    }
}
