package uk.suff.vencordcompanionidea.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.*;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import uk.suff.vencordcompanionidea.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Supports creating and managing a {@link JPanel} for the Settings Dialog.
 */
public class AppSettingsComponent{

	private final JPanel myMainPanel;

	private final CheckboxWithTitleAndDescription returnPatchedModuleByDefault = new CheckboxWithTitleAndDescription("Return Patched Module by Default", getLabelText(AppSettings.applyPatchWhenExtractingByDefault()), AppSettings.applyPatchWhenExtractingByDefault());
	private final CheckboxWithTitleAndDescription dynamicPatches = new CheckboxWithTitleAndDescription("Dynamic Patches", "When extracting modules from patches, apply the current patch in the editor rather than the patch in the current build", AppSettings.dynamicPatches());
	private final CheckboxWithTitleAndDescription cacheModulesOnConnection = new CheckboxWithTitleAndDescription("Cache Modules on Connection", "Cache webpack modules when Vencord Companion connects", AppSettings.cacheModulesOnConnection());


	private final JBLabel cachedModulesCount = new JBLabel(WebSocketServer.getCachedInfo());

	private final JButton refreshCache = new JButton(new AbstractAction("Refresh Cache"){
		@Override
		public void actionPerformed(ActionEvent e){
			if(Utils.warnWebSocketNotRunning() || Utils.warnCompanionNotConnected()) return;

			WebSocketServer.refreshCache(cachedModulesCount);
		}
	});
	private final JPanel refreshCachePanel = new JPanel();
	private final JButton clearCache = new JButton(new AbstractAction("Clear Cache"){
		@Override
		public void actionPerformed(ActionEvent e){
			System.out.println("Clearing cache");
			WebSocketServer.clearCache();
			cachedModulesCount.setText(WebSocketServer.getCachedInfo());
		}
	});
	private final JButton formatCachedFiles = new JButton(new AbstractAction("Format Cached Files"){
		@Override
		public void actionPerformed(ActionEvent e){
			int warning = JOptionPane.showConfirmDialog(myMainPanel, """
					This will format all the currently cached modules extracted from Vencord.
					This will likely take a while and will make it so that the contents of the file
					do not necessarily match the original module, so keep this in mind when writing patches based on this.
					
					E.g. var l={a:1,b:2} will be formatted to var l = { a: 1, b: 2 }""", "Warning", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

			if(warning == JOptionPane.OK_OPTION){
				//close settings dialog so the invokeLater can start
				Window window = SwingUtilities.getWindowAncestor(myMainPanel);
				if(window instanceof JDialog){
					window.dispose();
				}
				ProgressManager.getInstance().run(new Task.Backgroundable(Utils.project, "Formatting Cached Files", true, Task.Backgroundable.DEAF){
					@Override
					public void run(@NotNull ProgressIndicator indicator){
						indicator.setIndeterminate(false);
						int totalFiles = WebSocketServer.literallyEveryWebpackModule.size();
						int processedFiles = 0;
						long startTime = System.currentTimeMillis();

						for(PsiFile psiFile : WebSocketServer.literallyEveryWebpackModule.values()){
							if(indicator.isCanceled()){
								break;
							}

							updateIndicator(indicator, processedFiles, totalFiles, startTime, psiFile.getName());

							AtomicBoolean done = new AtomicBoolean(false);

							ApplicationManager.getApplication().invokeLater(()->{
								WriteCommandAction.runWriteCommandAction(Utils.project, ()->{
									CodeStyleManager.getInstance(Utils.project).reformat(psiFile);
									System.out.println("Reformatted " + psiFile.getName());
									done.set(true);
								});
							});

							while(!done.get()){
								try{
									Thread.sleep(1);
								}catch(InterruptedException e){
									e.printStackTrace();
								}
							}

							processedFiles++;
						}
					}
				});
			}
		}
	});

	private static final int AVERAGE_WINDOW_SIZE = 10; // Number of recent files to average
	private final Queue<Long> recentProcessingTimes = new LinkedList<>();

	private void updateIndicator(ProgressIndicator indicator, int processedFiles, int totalFiles, long startTime, String fileName) {
		long currentTime = System.currentTimeMillis();
		long elapsedTime = currentTime - startTime;

		// Add the latest processing time to the queue
		if (processedFiles > 0) {
			long lastProcessingTime = elapsedTime / processedFiles;
			recentProcessingTimes.add(lastProcessingTime);
			if (recentProcessingTimes.size() > AVERAGE_WINDOW_SIZE) {
				recentProcessingTimes.poll();
			}
		}

		// Calculate the average processing time
		long averageProcessingTime = !recentProcessingTimes.isEmpty() ? recentProcessingTimes.stream().mapToLong(Long::longValue).sum() / recentProcessingTimes.size() : -1;
		long timeRemaining = averageProcessingTime * (totalFiles - processedFiles);

		String timeRemainingStr = "Calculating...";
		if (timeRemaining > 0) {
			int hours = (int) (timeRemaining / 3600000);
			int minutes = (int) (timeRemaining % 3600000 / 60000);
			int seconds = (int) (timeRemaining % 60000 / 1000);

			if (hours > 0) {
				timeRemainingStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);
			} else if (minutes > 0) {
				timeRemainingStr = String.format("%02d:%02d", minutes, seconds);
			} else {
				timeRemainingStr = String.format("%02d", seconds);
			}
		}

		indicator.setFraction((double) processedFiles / totalFiles);
		indicator.setText(String.format("%0" + String.valueOf(totalFiles).length() + "d", processedFiles) + "/" + totalFiles + ": "+fileName);
		indicator.setText2("Time remaining: " + timeRemainingStr);
	}

	public AppSettingsComponent(){
		refreshCachePanel.setLayout(new BoxLayout(refreshCachePanel, BoxLayout.X_AXIS));
		refreshCachePanel.add(refreshCache);
		cachedModulesCount.withBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
		refreshCachePanel.add(cachedModulesCount);
		cachedModulesCount.addMouseListener(new MouseAdapter(){
			@Override
			public void mouseClicked(MouseEvent e){
				cachedModulesCount.setText(WebSocketServer.getCachedInfo());
			}
		});

		myMainPanel = FormBuilder.createFormBuilder()
								 .addComponent(returnPatchedModuleByDefault, 1)
								 .addComponent(dynamicPatches, 1)
								 .addComponent(cacheModulesOnConnection, 1)
								 .addComponent(refreshCachePanel, 1)
								 .addComponent(clearCache, 1)
								 .addComponent(formatCachedFiles, 1)
								 .addComponentFillVertically(new JPanel(), 0)
								 .getPanel();

		returnPatchedModuleByDefault.addChangeListener(e->returnPatchedModuleByDefault.setDescription(getLabelText(returnPatchedModuleByDefault.isSelected())));
	}

	private String getLabelText(boolean patchByDefault){
		return patchByDefault ?
				("""
						Return patched module when View Module is clicked
						\tClick - View Module with patch applied
						\tCtrl+Click - View Module without applying""")
				:
				("""
						Return patched module when View Module is clicked
						\tClick - View Module without applying
						\tCtrl+Click - View Module with patch applied""");
	}

	public JPanel getPanel(){
		return myMainPanel;
	}

	public JPanel getRefreshCachePanel(){
		return refreshCachePanel;
	}

	public JComponent getPreferredFocusedComponent(){
		return myMainPanel;
	}

	public boolean getReturnPatchedModuleByDefault(){
		return returnPatchedModuleByDefault.isSelected();
	}

	public void setReturnPatchedModuleByDefault(boolean value){
		returnPatchedModuleByDefault.setSelected(value);
	}

	public boolean getCacheModulesOnConnection(){
		return cacheModulesOnConnection.isSelected();
	}

	public void setCacheModulesOnConnection(boolean value){
		cacheModulesOnConnection.setSelected(value);
	}

	public boolean getDynamicPatches(){
		return dynamicPatches.isSelected();
	}

	public void setDynamicPatches(boolean value){
		dynamicPatches.setSelected(value);
	}

	public JButton getRefreshCacheButton(){
		return refreshCache;
	}

	public JButton getClearCacheButton(){
		return clearCache;
	}

	public static class CheckboxWithTitleAndDescription extends JPanel{
		JBCheckBox checkBox;
		JBLabel titleComponent;
		JBTextArea descriptionComponent;

		public CheckboxWithTitleAndDescription(String title, String description){
			this(title, description, false);
		}

		public CheckboxWithTitleAndDescription(String title, String description, boolean selected){
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			setAlignmentX(Component.LEFT_ALIGNMENT);

			JPanel textPanel = new JPanel();
			textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
			textPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

			titleComponent = new JBLabel(title);
			titleComponent.setAlignmentX(Component.LEFT_ALIGNMENT);

			Font base = titleComponent.getFont();
			descriptionComponent = new JBTextArea(description);
			descriptionComponent.setLineWrap(true);
			descriptionComponent.setWrapStyleWord(true);
			descriptionComponent.setEditable(false);
			descriptionComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
			descriptionComponent.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 0));
			descriptionComponent.setFocusable(false);
			descriptionComponent.setFont(base.deriveFont(base.getSize() - 3f));
			descriptionComponent.setForeground(JBColor.gray);
			descriptionComponent.setMaximumSize(new Dimension(300, 100));

			titleComponent.setFont(base.deriveFont(Font.BOLD));

			checkBox = new JBCheckBox(null, selected);
			checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);

			textPanel.add(titleComponent);
			textPanel.add(descriptionComponent);

			add(textPanel);
			add(checkBox);
			setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
		}

		public void setSelected(boolean selected){
			checkBox.setSelected(selected);
		}

		public boolean isSelected(){
			return checkBox.isSelected();
		}

		public void addChangeListener(ActionListener listener){
			checkBox.addActionListener(listener);
		}

		public void setTitle(String title){
			checkBox.setText(title);
		}

		public void setDescription(String description){
			descriptionComponent.setText(description);
		}

	}

}
