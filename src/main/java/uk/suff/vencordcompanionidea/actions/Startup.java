package uk.suff.vencordcompanionidea.actions;

import com.intellij.ide.plugins.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.*;
import uk.suff.vencordcompanionidea.*;

public class Startup implements ProjectActivity, DynamicPluginListener{

	// TODO: vencord-companion.runReporter - An action to run Vencord in reporter mode and display the output in a new tab
	// TODO: vencord-companion.generateFinds - Don't know what this is
	// DONE: vencord-companion.diffModule - A text command to diff a module with its patched version, takes a module id
	// DONE: vencord-companion.diffModuleSearch - The "View Diff" button on patches
	// DONE: vencord-companion.extractFind - The "View Module" button on find method calls
	// DONE: vencord-companion.extract - A text command to extract a module from a file, takes a module id
	// DONE: vencord-companion.extractSearch - The "View Module" button on patches
	// DONE: vencord-companion.testPatch - The "Test Patch" button on patches
	// DONE: vencord-companion.testFind - The "Test Find" button on find method calls
	// TODO: enable/disable plugin code vision inlay
	@Nullable
	@Override
	public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation){
		if(!Utils.isVencordProject(project)){
			return null;
		}

		Utils.project = project;
		try{
			WebSocketServer.startWebSocketServer();
		}catch(Exception e){
			Logs.error(e);
		}

		try{
			Logs.info(PluginUtil.getInstance().findPluginId(new Throwable()));
		}catch(Exception e){
			Logs.error(e);
		}

		return null;
	}

	@Override
	public void checkUnloadPlugin(@NotNull IdeaPluginDescriptor pluginDescriptor) throws CannotUnloadPluginException{
		Logs.info("Unloading plugin: " + pluginDescriptor.getName());
		try{
			WebSocketServer.stopWebSocketServer();
		}catch(Exception e){
			throw new CannotUnloadPluginException(e.getMessage());
		}
		DynamicPluginListener.super.checkUnloadPlugin(pluginDescriptor);
	}
}
