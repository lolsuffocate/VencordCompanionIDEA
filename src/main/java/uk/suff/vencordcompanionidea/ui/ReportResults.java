package uk.suff.vencordcompanionidea.ui;

import com.intellij.ide.fileTemplates.impl.FileTemplateConfigurable;
import com.intellij.json.JsonLanguage;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.*;
import org.codehaus.plexus.util.StringUtils;
import org.json.*;
import uk.suff.vencordcompanionidea.Utils;
import uk.suff.vencordcompanionidea.actions.Reporter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.io.File;
import java.util.*;

public class ReportResults{
	private JTabbedPane mainTabbedPane;
	private JTabbedPane failedPatchesTabbedPane;
	private JPanel mainPanel;
	private JPanel foundNoModulePanel;
	private JPanel hadNoEffectPanel;
	private JPanel undoingPatchGroup;
	private JPanel erroredPatch;
	private JPanel failedWebpackFindsPanel;
	private JScrollPane failedWebpackFindsScrollPane;
	private JScrollPane rawJsonScrollPane;
	private JPanel rawPanel;

	public ReportResults(){
		foundNoModulePanel.addComponentListener(new ComponentAdapter(){});
	}

	public JScrollPane getRawJsonScrollPane(){
		return rawJsonScrollPane;
	}

	public JTabbedPane getMainTabbedPane(){
		return mainTabbedPane;
	}

	public JTabbedPane getFailedPatchesTabbedPane(){
		return failedPatchesTabbedPane;
	}

	public JPanel getFoundNoModulePanel(){
		return foundNoModulePanel;
	}

	public JPanel getHadNoEffectPanel(){
		return hadNoEffectPanel;
	}

	public JPanel getUndoingPatchGroup(){
		return undoingPatchGroup;
	}

	public JPanel getErroredPatch(){
		return erroredPatch;
	}

	public JPanel getFailedWebpackFindsPanel(){
		return failedWebpackFindsPanel;
	}

	public JScrollPane getFailedWebpackFindsScrollPane(){
		return failedWebpackFindsScrollPane;
	}

	public JPanel getMainPanel(){
		return mainPanel;
	}

	public void addRawJson(JSONObject json){
		// add raw json to the raw json editor pane
		// VirtualFile vf = new LightVirtualFile("report.json", FileTypeManager.getInstance().findFileTypeByName("json"), json.toString(4));
		CustomJsonEditor ltf = new CustomJsonEditor(Utils.project, json);
		getRawJsonScrollPane().setViewportView(ltf);
		getRawJsonScrollPane().getVerticalScrollBar().setUnitIncrement(16);

		WriteCommandAction.runWriteCommandAction(Utils.project, ()->{
			CodeStyleManager.getInstance(Utils.project).reformat(PsiDocumentManager.getInstance(Utils.project).getPsiFile(ltf.getDocument()));
		});
	}

	/*{
      "foundNoModule": [
        {
          "find": ".NITRO_BANNER,",
          "replacement": [
            {
              "match": "/(?<=hasProfileEffect.+?)children:\\[/",
              "replace": "$&$self.renderProfileTimezone(arguments[0]),"
            }
          ],
          "plugin": "Timezone"
        },
        {
          "find": "=!1,canUsePremiumCustomization:",
          "replacement": [
            {
              "match": "/(?<=hasProfileEffect.+?)children:\\[/",
              "replace": "$&$self.renderProfileTimezone(arguments[0]),"
            }
          ],
          "plugin": "Timezone"
        }
      ],
      "hadNoEffect": [
        {
          "find": "Messages.ACTIVITY_SETTINGS",
          "replacement": [
            {
              "match": "/(?<=section:(.{0,50})\\.DIVIDER\\}\\))([,;])(?=.{0,200}([A-Za-z_$][\\w$]*)\\.push.{0,100}label:([A-Za-z_$][\\w$]*)\\.header)/"
            },
            {
              "match": "/({(?=.+?function ([A-Za-z_$][\\w$]*).{0,120}([A-Za-z_$][\\w$]*)=[A-Za-z_$][\\w$]*\\.useMemo.{0,30}return [A-Za-z_$][\\w$]*\\.useMemo\\(\\(\\)=>[A-Za-z_$][\\w$]*\\(\\3).+?function\\(\\){return )\\2(?=})/"
            }
          ],
          "plugin": "Settings",
          "id": "394644"
        }
      ],
      "undoingPatchGroup": [],
      "erroredPatch": []
}*/
	public void addFailedPatches(JSONObject failedPatches){
		// add failed patches to the failed patches tabbed pane
		JSONArray foundNoModule = failedPatches.getJSONArray("foundNoModule");
		JSONArray hadNoEffect = failedPatches.getJSONArray("hadNoEffect");
		JSONArray undoingPatchGroup = failedPatches.getJSONArray("undoingPatchGroup");
		JSONArray erroredPatch = failedPatches.getJSONArray("erroredPatch");

		JPanel foundNoModulePanel = getFoundNoModulePanel();
		JPanel hadNoEffectPanel = getHadNoEffectPanel();
		JPanel undoingPatchGroupPanel = getUndoingPatchGroup();
		JPanel erroredPatchPanel = getErroredPatch();

		foundNoModulePanel.setLayout(new BoxLayout(foundNoModulePanel, BoxLayout.Y_AXIS));
		hadNoEffectPanel.setLayout(new BoxLayout(hadNoEffectPanel, BoxLayout.Y_AXIS));
		undoingPatchGroupPanel.setLayout(new BoxLayout(undoingPatchGroupPanel, BoxLayout.Y_AXIS));
		erroredPatchPanel.setLayout(new BoxLayout(erroredPatchPanel, BoxLayout.Y_AXIS));

		populatePanel(foundNoModulePanel, foundNoModule);
		populatePanel(hadNoEffectPanel, hadNoEffect);
		populatePanel(undoingPatchGroupPanel, undoingPatchGroup);
		populatePanel(erroredPatchPanel, erroredPatch);
	}

	public void populatePanel(JPanel panel, JSONArray json){
		int count = json.length();
		// getParent = viewport, viewport.getParent = scrollpane, scrollpane.getParent = panel
		Container scrollView = panel.getParent().getParent();
		JTabbedPane tabPane = ((JTabbedPane) scrollView.getParent());

		if(json.isEmpty()){
			panel.add(new JLabel("No patches found"));
		}else{
			HashMap<String, JSONArray> pluginMap = new HashMap<>();
			for(int i = 0; i < json.length(); i++){
				JSONObject patch = json.getJSONObject(i);
				String plugin = patch.getString("plugin");
				if(pluginMap.containsKey(plugin)){
					pluginMap.get(plugin).put(patch);
				}else{
					JSONArray pluginPatches = new JSONArray();
					pluginPatches.put(patch);
					pluginMap.put(plugin, pluginPatches);
				}
			}

			pluginMap.forEach((plugin, patches)->{
				JPanel pluginPanel = new JPanel();
				pluginPanel.setLayout(new BoxLayout(pluginPanel, BoxLayout.Y_AXIS));
				pluginPanel.setBorder(BorderFactory.createTitledBorder(plugin));

				patches.forEach(o->{
					JSONObject patch = (JSONObject) o;
					JPanel patchPanel = new JPanel();
					patchPanel.setLayout(new BoxLayout(patchPanel, BoxLayout.Y_AXIS));
					JButton viewDiff = null;
					for(Iterator<String> it = patch.keys(); it.hasNext(); ){
						String key = it.next();
						if(key == null) continue;
						if(key.equals("plugin")) continue;
						if(key.equals("find")){
							String find = patch.getString(key);
							String jsEscapedFind = StringUtils.escape(find);
							patchPanel.add(new JLabel(key + ": " + jsEscapedFind));
						}else if(key.equals("replacement")){
							if(patch.get(key) instanceof JSONArray){
								JSONArray replacement = patch.getJSONArray(key);
								JPanel replacementPanel = new JPanel();
								replacementPanel.setLayout(new BoxLayout(replacementPanel, BoxLayout.Y_AXIS));
								replacementPanel.setBorder(BorderFactory.createTitledBorder("Replacement"+(replacement.length() > 1 ? "s" : "")));
								replacement.forEach(o1->{
									JSONObject replacementObj = (JSONObject) o1;
									String match = replacementObj.getString("match");
									String replace = replacementObj.getString("replace");
									String jsEscapedMatch = Reporter.decanoniseRegex(match);
									String jsEscapedReplace = Reporter.decanoniseReplace(replace, plugin);

									JPanel replacementObjPanel = new JPanel();
									replacementObjPanel.setLayout(new BoxLayout(replacementObjPanel, BoxLayout.Y_AXIS));
									if(replacement.length() > 1){
										replacementObjPanel.setBorder(BorderFactory.createTitledBorder("Replacement"));
									}
									replacementObjPanel.add(new JLabel("Match: " + jsEscapedMatch));
									replacementObjPanel.add(new JLabel("Replace: " + jsEscapedReplace));
									replacementPanel.add(replacementObjPanel);
								});
								patchPanel.add(replacementPanel);
							}else{
								JSONObject replacementObj = patch.getJSONObject(key);
								JPanel replacementPanel = new JPanel();
								replacementPanel.setLayout(new BoxLayout(replacementPanel, BoxLayout.Y_AXIS));
								replacementObj.keys().forEachRemaining(replacementKey->{
									replacementPanel.add(new JLabel(replacementKey + ": " + replacementObj.get(replacementKey)));
								});
								patchPanel.add(replacementPanel);
							}
						}else if(key.equals("oldModule") && patch.has("newModule") && patch.has("id")){
							String oldModule = patch.getString("oldModule");
							String newModule = patch.getString("newModule");

							viewDiff = new JButton("View Diff");
							viewDiff.addActionListener((actionEvent)->{
								// open diff view
								Utils.openNewDiffTab("Diff: " + plugin + " - module " + patch.getString("id") + ".js",
													 oldModule, newModule, Utils.project);
							});
						}else if(key.equals("newModule")){
							// skip
						}else{
							patchPanel.add(new JLabel(key + ": " + patch.get(key)));
						}
					}
					JButton viewPatch = new JButton("View Patch");
					viewPatch.addActionListener(Reporter.getActionEvent(patch, tabPane.getTitleAt(tabPane.indexOfComponent(scrollView))));
					patchPanel.add(viewPatch);
					if(viewDiff != null) patchPanel.add(viewDiff);
					pluginPanel.add(patchPanel);
				});
				panel.add(pluginPanel);
			});
		}
		tabPane.setTitleAt(tabPane.indexOfComponent(scrollView), tabPane.getTitleAt(tabPane.indexOfComponent(scrollView)) + " (" + count + ")");
		// if the tab has 0 patches, disable it
		tabPane.setEnabledAt(tabPane.indexOfComponent(scrollView), count > 0);
	}

	/*"failedWebpack": {
      "find": [],
      "findByProps": [],
      "findByCode": [],
      "findStore": [],
      "findComponent": [],
      "findComponentByCode": [],
      "findExportedComponent": [],
      "waitFor": [],
      "waitForComponent": [],
      "waitForStore": [],
      "proxyLazyWebpack": [],
      "LazyComponentWebpack": [],
      "extractAndLoadChunks": [],
      "mapMangledModule": []
    }*/
	public void addFailedWebpackFinds(JSONObject failedWebpackFinds){
		// add failed webpack finds to the failed webpack finds tabbed pane
		JPanel failedWebpackFindsPanel = getFailedWebpackFindsPanel();
		failedWebpackFindsPanel.setLayout(new BoxLayout(failedWebpackFindsPanel, BoxLayout.Y_AXIS));
		boolean noneFound = true;
		for(String key : failedWebpackFinds.keySet()){
			JSONArray finds = failedWebpackFinds.getJSONArray(key);
			if(finds.isEmpty()){
				//failedWebpackFindsPanel.add(new JLabel("No " + key + " failures found"));
			}else{
				noneFound = false;
				JPanel findPanel = new JPanel();
				findPanel.setLayout(new BoxLayout(findPanel, BoxLayout.Y_AXIS));
				findPanel.setBorder(BorderFactory.createTitledBorder(key));

				// each find is an array of strings
				finds.forEach(o->{
					JSONArray find = (JSONArray) o;
					JPanel findArrayPanel = new JPanel();
					findArrayPanel.setLayout(new BoxLayout(findArrayPanel, BoxLayout.Y_AXIS));
					find.forEach(o1->{
						findArrayPanel.add(new JLabel(o1.toString()));
					});
					findPanel.add(findArrayPanel);
				});
				failedWebpackFindsPanel.add(findPanel);
			}
		}

		if(noneFound){
			failedWebpackFindsPanel.add(new JLabel("No failed webpack finds"));
		}
	}

}
