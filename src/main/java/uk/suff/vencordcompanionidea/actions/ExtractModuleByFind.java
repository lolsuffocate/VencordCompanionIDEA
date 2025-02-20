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
import java.awt.*;
import java.awt.event.*;

public class ExtractModuleByFind extends AnAction{

	// same as extractbyid but when the user types, we send the request to the server
	// if there are multiple results, orange, tell them to be more specific
	// if none, red, say no results found
	// if one, green, good
	@Override
	public void actionPerformed(@NotNull AnActionEvent e){
		if(Utils.warnWebSocketNotRunning() || Utils.warnCompanionNotConnected()){
			return;
		}

		JTextField moduleIdField = new JTextField();
		JLabel statusLabel = new JLabel();
		JCheckBox regexFind = new JCheckBox("Regex find");

		FormBuilder formBuilder = FormBuilder.createFormBuilder()
											 .addLabeledComponent("Find:", moduleIdField, true)
											 .addComponent(regexFind)
											 .addComponent(statusLabel);

		Runnable updateStatus = ()->{
			if(moduleIdField.getText().isEmpty()){
				statusLabel.setText("");

				Window window = SwingUtilities.getWindowAncestor(statusLabel);
				if(window instanceof JDialog){
					window.pack();
				}
				return;
			}
			boolean regex = regexFind.isSelected();
			WebSocketServer.extractModuleByFind(moduleIdField.getText(), regex)
						   .thenAccept(json->{
							   if(json != null && json.has("data") && json.has("moduleNumber")){
								   String moduleNumber = String.valueOf(json.getInt("moduleNumber"));
								   statusLabel.setText("Module found: " + moduleNumber);
								   statusLabel.setForeground(JBColor.GREEN);
							   }else if(json != null && json.has("ok") && !json.getBoolean("ok")){
								   String error = json.getString("error");
								   if(error.startsWith("Error: ")) error = error.substring(7);
								   statusLabel.setText(error);
								   statusLabel.setForeground(error.equals("No Matches Found") ? JBColor.RED : JBColor.ORANGE);
							   }else{
								   statusLabel.setText("No module found");
								   statusLabel.setForeground(JBColor.RED);
								   Utils.notify("Error", "Could not extract module with find: \"" + moduleIdField.getText() + "\"", NotificationType.ERROR);
							   }

							   Window window = SwingUtilities.getWindowAncestor(statusLabel);
							   if(window instanceof JDialog){
								   window.pack();
							   }
						   });
		};

		regexFind.addChangeListener(e1->{
			updateStatus.run();
		});

		moduleIdField.addKeyListener(new KeyListener(){
			@Override
			public void keyTyped(KeyEvent e){}

			@Override
			public void keyPressed(KeyEvent e){}

			@Override
			public void keyReleased(KeyEvent e){
				updateStatus.run();
			}
		});

		// use the main intellij window as the parent
		JComponent parent = e.getData(PlatformDataKeys.EDITOR) != null ? e.getData(PlatformDataKeys.EDITOR).getComponent() : null;

		SwingUtilities.invokeLater(()->{
			moduleIdField.requestFocusInWindow();
			int result = JOptionPane.showConfirmDialog(parent, formBuilder.getPanel(), "Extract Module", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

			if(result == JOptionPane.OK_OPTION){
				boolean regex = regexFind.isSelected();
				WebSocketServer.extractModuleByFind(moduleIdField.getText(), regex)
							   .thenAccept(jsonObject->{
								   if(jsonObject != null && jsonObject.has("data") && jsonObject.has("moduleNumber")){
									   String moduleNumber = String.valueOf(jsonObject.getInt("moduleNumber"));
									   String moduleStr = jsonObject.getString("data");
									   Utils.openFileInEditor(new LightVirtualFile("module" + moduleNumber + ".js", JavascriptLanguage.INSTANCE, moduleStr), TextRange.EMPTY_RANGE);
								   }else{
									   Utils.notify("Error", "Could not extract module with find: \"" + moduleIdField.getText() + "\"", NotificationType.ERROR);
								   }
							   }).exceptionally(e1->{
								   Logs.error(e1);
								   Utils.notify("Error", "An error occurred while extracting module with find: \"" + moduleIdField.getText() + "\"", NotificationType.ERROR);
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
