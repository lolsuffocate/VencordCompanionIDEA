package uk.suff.vencordcompanionidea.actions;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NotNull;
import uk.suff.vencordcompanionidea.*;

public class StartStopWebSocketServer extends AnAction{

	@Override
	public void actionPerformed(@NotNull AnActionEvent e){
		if(WebSocketServer.server == null || WebSocketServer.server.isStopped()){
			try{
				WebSocketServer.startWebSocketServer();
				Utils.notify("Vencord Companion", "WebSocket server started", NotificationType.INFORMATION);
			}catch(Exception ex){
				ex.printStackTrace();
			}
		}else{
			try{
				WebSocketServer.stopWebSocketServer();
				Utils.notify("Vencord Companion", "WebSocket server stopped", NotificationType.INFORMATION);
			}catch(Exception ex){
				ex.printStackTrace();
			}
		}
	}

	@Override
	public void update(@NotNull AnActionEvent e){
		if(WebSocketServer.server == null || WebSocketServer.server.isStopped()){
			e.getPresentation().setText("Start WebSocket Server");
			e.getPresentation().setIcon(AllIcons.Actions.Execute);
		}else{
			e.getPresentation().setText("Stop WebSocket Server");
			e.getPresentation().setIcon(AllIcons.Actions.Suspend);
		}
	}

	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread(){
		return ActionUpdateThread.BGT;
	}
}
