package uk.suff.vencordcompanionidea.actions;

import com.intellij.execution.process.*;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import uk.suff.vencordcompanionidea.*;

import java.io.File;

public class BuildDevAndHardReload extends AnAction{

	@Override
	public void actionPerformed(@NotNull AnActionEvent e){
		if(Utils.warnWebSocketNotRunning() || Utils.warnCompanionNotConnected()){
			return;
		}

		Utils.notify("Building Plugin", "Building the plugin in development mode", NotificationType.INFORMATION);
		new Task.Modal(e.getProject(), "Building Dev", true){
			@Override
			public void run(@NotNull ProgressIndicator indicator){
				indicator.checkCanceled();
				indicator.setIndeterminate(false);
				indicator.setText("Building dev build");
				indicator.setFraction(0.5);
				ProcessBuilder devProcessBuilder = new ProcessBuilder("pnpm", "build", "--dev");
				devProcessBuilder.directory(new File(e.getProject().getBasePath()));
				indicator.checkCanceled();
				try{
					Utils.runProcessInRunWindow(e.getProject(), devProcessBuilder, getTitle(), new ProcessListener(){
						@Override
						public void processTerminated(@NotNull ProcessEvent event){
							ProcessListener.super.processTerminated(event);
							if(event.getExitCode() != 0){
								Utils.alert("Error running dev build", "The dev build failed. Check the Run window for more information", Messages.getErrorIcon());
								return;
							}

							indicator.setText("Running dev build");
							indicator.setFraction(1.0);
							// build should be successful at this point, reload the app
							JSONObject message = new JSONObject();
							message.put("type", "reload");
							message.put("data", new JSONObject().put("hard", true));
							WebSocketServer.sendToSockets(message);
						}
					});
				}catch(Exception e){
					Utils.notify("Error running dev build", e.getMessage(), NotificationType.ERROR);
					Logs.error(e);
				}
			}

			@Override
			public void onThrowable(@NotNull Throwable error){
				Utils.notify("Error running dev build", error.getMessage(), NotificationType.ERROR);
				Logs.error(error);
			}
		}.queue();
	}
}
