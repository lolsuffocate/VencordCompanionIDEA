package uk.suff.vencordcompanionidea.actions;

import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import uk.suff.vencordcompanionidea.*;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.*;

public class ExtractModuleById extends AnAction{

	@Override
	public void actionPerformed(@NotNull AnActionEvent e){
		if(Utils.warnWebSocketNotRunning() || Utils.warnCompanionNotConnected()){
			return;
		}

		JTextField moduleIdField = new JTextField();


		//display a dialog to get the module id
		FormBuilder formBuilder = FormBuilder.createFormBuilder()
											 .addLabeledComponent("Module ID:", moduleIdField, true);

		moduleIdField.addKeyListener(new KeyListener(){
			@Override
			public void keyTyped(KeyEvent e){}

			@Override
			public void keyPressed(KeyEvent e){}

			@Override
			public void keyReleased(KeyEvent e){
				if(WebSocketServer.literallyEveryWebpackModule.containsKey(Integer.parseInt(moduleIdField.getText()))){
					Border greenOutline = BorderFactory.createLineBorder(JBColor.GREEN);
					moduleIdField.setBorder(greenOutline);
				}else{
					Border redOutline = BorderFactory.createLineBorder(JBColor.RED);
					moduleIdField.setBorder(redOutline);
				}
			}
		});

		// use the main intellij window as the parent
		JComponent parent = e.getData(PlatformDataKeys.EDITOR) != null ? e.getData(PlatformDataKeys.EDITOR).getComponent() : null;

		SwingUtilities.invokeLater(()->{
			moduleIdField.requestFocusInWindow();
			int result = JOptionPane.showConfirmDialog(parent, formBuilder.getPanel(), "Extract Module", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

			if(result == JOptionPane.OK_OPTION){
				int moduleId;
				try{
					moduleId = Integer.parseInt(moduleIdField.getText());
				}catch(NumberFormatException ex){
					Utils.notify("Error", "Invalid module ID", NotificationType.ERROR);
					return;
				}

				WebSocketServer.extractModuleById(moduleId).thenAccept(jsonObject->{
					if(jsonObject != null && jsonObject.has("data")){
						String moduleStr = jsonObject.getString("data");
						Utils.openFileInEditor(new LightVirtualFile("module" + moduleId + ".js", JavascriptLanguage.INSTANCE, moduleStr), TextRange.EMPTY_RANGE);
					}else{
						Utils.notify("Error", "Could not extract module with ID: " + moduleId, NotificationType.ERROR);
					}
				}).exceptionally(e1->{
					e1.printStackTrace();
					Utils.notify("Error", "An error occurred while extracting module with ID: " + moduleId, NotificationType.ERROR);
					return null;
				});
			}
		});
	}

	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread(){
		return ActionUpdateThread.BGT;
	}
}
