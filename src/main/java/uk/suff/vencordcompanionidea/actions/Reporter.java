package uk.suff.vencordcompanionidea.actions;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.*;
import com.intellij.util.ApplicationKt;
import com.intellij.util.ui.EDT;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.*;
import uk.suff.vencordcompanionidea.*;
import uk.suff.vencordcompanionidea.ui.ReportResults;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class Reporter extends AnAction{

	public static boolean debug = java.lang.management.ManagementFactory.
			getRuntimeMXBean().
			getInputArguments().toString().contains("jdwp");

	@Override
	public void actionPerformed(@NotNull AnActionEvent e){
		if(Utils.project == null) Utils.project = e.getProject();
		if(debug){
			Utils.notify("Reporter", "Running reporter", NotificationType.INFORMATION);
			handleReporterResults(new JSONObject("{\"data\":{\"failedWebpack\": {\"findComponent\": [],\"proxyLazyWebpack\": [],\"LazyComponentWebpack\": [],\"extractAndLoadChunks\": [],\"mapMangledModule\": [],\"findExportedComponent\": [],\"findByProps\": [],\"findStore\": [],\"findByCode\": [[\".getMessageReminders()).length\"],[\"adfgv\"]],\"waitForComponent\": [],\"find\": [],\"findComponentByCode\": [],\"waitFor\": [],\"waitForStore\": []},\"failedPatches\": {\"erroredPatch\": [],\"foundNoModule\": [{\"plugin\": \"SomeBrokenPlugin\",\"find\": \"imagineNotFindingAModule\",\"replacement\": [{\"match\": \"/Audio/\",\"replace\": \"$&\"}]},{\"plugin\": \"SomeOtherBrokenPlugin\",\"find\": \"imagineNotFindingAModuleHereEither\",\"replacement\": [{\"match\": \"/Audio/\",\"replace\": \"$&\"}]},{\"plugin\": \"YetAnotherBrokenPlugin\",\"find\": \"imagineNotFindingAModule\",\"replacement\": [{\"match\": \"/Audio/\",\"replace\": \"$&\"}]}],\"undoingPatchGroup\": [],\"hadNoEffect\": [{\"plugin\": \"SomeBrokenPlugin\",\"find\": \",\\\".mp3\\\"\",\"id\": \"902653\",\"replacement\": [{\"match\": \"/Audio;([A-Za-z_$][\\\\w$]*)\\\\.src=/\",\"replace\": \"$&\"}]},{\"plugin\": \"SomeBrokenPlugin\",\"find\": \",\\\".mp3\\\"\",\"id\": \"902653\",\"replacement\": [{\"match\": \"/Audio;([A-Za-z_$][\\\\w$]*)\\\\.src=/\",\"replace\": \"$&\"}]},{\"plugin\": \"SomeBrokenPlugin\",\"find\": \",\\\".mp3\\\"\",\"id\": \"902653\",\"replacement\": [{\"match\": \"/Audio;([A-Za-z_$][\\\\w$]*)\\\\.src=/\",\"replace\": \"$&\"}]},{\"plugin\": \"SomeOtherBrokenPlugin\",\"find\": \",\\\".mp3\\\"\",\"id\": \"902653\",\"replacement\": [{\"match\": \"/Audio;([A-Za-z_$][\\\\w$]*)\\\\.src=/\",\"replace\": \"blahblah erroring patch Vencord.Plugins.plugins[\\\"SomeOtherBrokenPlugin\\\"] idk\"}]},{\"plugin\": \"SomeThirdBrokenPlugin\",\"find\": \",\\\".mp3\\\"\",\"id\": \"902653\",\"replacement\": [{\"match\": \"/Audio;([A-Za-z_$][\\\\w$]*)\\\\.src=/\",\"replace\": \"$&\"}]}]}}}"));
		}else{
			if(Utils.warnWebSocketNotRunning() || Utils.warnCompanionNotConnected()){
				return;
			}

			runReporter(e.getProject());
		}
	}

	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread(){
		return ActionUpdateThread.BGT;
	}

	public static boolean running = false;
	public static ArrayList<Task> runningTasks = new ArrayList<>();
	public static Task buildContainer;

	public static void runReporter(Project project){
		if(Utils.warnWebSocketNotRunning() || Utils.warnCompanionNotConnected()){
			return;
		}

		if(running){
			Utils.notify("Reporter is currently running", "Please wait for the current reporter task to finish before running another one.", NotificationType.ERROR);
			running = false; // let the user try again, their own loss if it goes weird
			return;
		}

		running = true;
		initContainer(project);

		if(!runningTasks.isEmpty()){
			Utils.notify("Building reporter", "Please wait for the current reporter task to finish before running another one.", NotificationType.ERROR);
			return;
		}

		buildContainer.queue(); // start the reporter build. Once it completes, it will send a reload command to the browser and await results back from vencord
	}

	public static void initContainer(Project project){
		if(buildContainer != null) return;

		buildContainer = new Task.Modal(project, "Building reporter", true){
			@Override
			public void run(@NotNull ProgressIndicator indicator){
				indicator.checkCanceled();
				indicator.setIndeterminate(false);
				// run shell command in vencord dir - pnpm build --dev --reporter --companion-test
				runningTasks.add(this);
				indicator.setText("Building reporter build");
				indicator.setFraction(0.3);
				indicator.checkCanceled();
				ProcessBuilder reporterProcessBuilder = new ProcessBuilder("pnpm", "build", "--dev", "--reporter", "--companion-test");
				indicator.checkCanceled();
				reporterProcessBuilder.directory(new File(project.getBasePath()));
				indicator.checkCanceled();
				try{
					Logs.info("############# Running reporter");
					Utils.lockComplete = false;
					synchronized(Utils.lock){
						Utils.runProcessInRunWindow(project, reporterProcessBuilder, getTitle());
						for(int i = 0; i < 30; i++){
							Utils.lock.wait(2000);
							if(Utils.lockComplete) break;
							indicator.checkCanceled();
						}
					}
				}catch(Exception e){
					Utils.notify("Error running reporter", e.getMessage(), NotificationType.ERROR);
					Logs.error(e);
					running = false;
					runningTasks.remove(this);
					return;
				}
				indicator.setText("Running reporter");
				indicator.setFraction(0.6);
				// build should be successful at this point, reload the app
				WebSocketServer.sendToSockets(new JSONObject().put("type", "reload"));
				indicator.checkCanceled();

				// wait for the results to come back from vencord
				Utils.lockComplete = false;
				synchronized(Utils.lock){
					try{
						for(int i = 0; i < 30; i++){
							Utils.lock.wait(2000);
							if(Utils.lockComplete) break;
							indicator.checkCanceled();
						}
					}catch(InterruptedException e){
						Logs.error(e);
					}
				}
				indicator.checkCanceled();

				indicator.setText("Building dev build");
				indicator.setFraction(0.9);
				ProcessBuilder devProcessBuilder = new ProcessBuilder("pnpm", "build", "--dev");
				devProcessBuilder.directory(new File(project.getBasePath()));
				indicator.checkCanceled();
				try{
					Logs.info("############# Running dev");
					/*Process process = processBuilder.start();
					process.waitFor();*/
					Utils.lockComplete = false;
					synchronized(Utils.lock){
						Utils.runProcessInRunWindow(project, devProcessBuilder, getTitle());
						for(int i = 0; i < 30; i++){
							Utils.lock.wait(2000);
							if(Utils.lockComplete) break;
							indicator.checkCanceled();
						}
					}
				}catch(Exception e){
					Utils.notify("Error running reporter", e.getMessage(), NotificationType.ERROR);
					Logs.error(e);
					running = false;
					runningTasks.remove(this);
					return;
				}
				indicator.setText("Running dev build");
				indicator.setFraction(1.0);
				// build should be successful at this point, reload the app
				WebSocketServer.sendToSockets(new JSONObject().put("type", "reload"));
				runningTasks.remove(this);
				running = false;
			}

			@Override
			public void onSuccess(){
				// send reload command
				Utils.notify("Vencord Companion", "Reporter build successful", NotificationType.INFORMATION);
				runningTasks.remove(this);
				running = false;
			}

			@Override
			public void onThrowable(@NotNull Throwable error){
				Utils.notify("Error running reporter", error.getMessage(), NotificationType.ERROR);
				Logs.error(error);
				running = false;
				runningTasks.remove(this);
			}

			@Override
			public void onCancel(){
				running = false;
				runningTasks.remove(this);
			}
		};
	}

	/**
	 * {@link WebSocketServer} will call this method when it receives the results from vencord
	 */
	/*{
  "type": "report",
  "data": {
    "failedPatches": {
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
    },
    "failedWebpack": {
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
    }
  },
  "ok": true
}*/
	public static void handleReporterResults(JSONObject json){
		// canonicalise the results
		JSONObject results = new JSONObject(decanoniseRegex(json.toString()));
		// received report, now release the lock so the dev build can continue
		synchronized(Utils.lock){
			Utils.lockComplete = true; // probably a better way to do this
			Utils.lock.notifyAll();
		}

		// parse report
		JSONObject data = results.getJSONObject("data");
		JSONObject failedPatches = data.getJSONObject("failedPatches");
		JSONObject failedWebpack = data.getJSONObject("failedWebpack");

		// build the report UI
		JBPanel<?> resultsPanel = new JBPanel<>();
		resultsPanel.setName("resultsPanel");
		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.add(new JBLabel("Reporter results"));

		ReportResults reportResults = new ReportResults();
		resultsPanel.add(reportResults.getMainPanel());

		reportResults.addFailedPatches(failedPatches);
		reportResults.addFailedWebpackFinds(failedWebpack);
		reportResults.addRawJson(data);

		// show the report tab
		showReportTab(resultsPanel, Utils.project);
	}

	public static Key<JComponent> reportPanelKey = Key.create("reportPanelJson");

	public static void showReportTab(JComponent panel, Project project){

		if(!EDT.isCurrentThreadEdt()){
			ApplicationKt.getApplication().invokeLater(()->{
				showReportTab(panel, project);
			});
			return;
		}
		//if a Reporter Results tab is already open, add a number to the end of the tab name
		int count = 1;
		AtomicReference<String> name = new AtomicReference<>("Reporter Results");
		while(Arrays.stream(FileEditorManager.getInstance(project).getOpenFiles()).anyMatch(vf->vf.getName().equals(name.get()))){
			name.set("Reporter Results (" + count + ")");
			count++;
		}

		ApplicationKt.getApplication().runWriteAction(()->{
			// create virtual file
			VirtualFile virtualFile = new LightVirtualFile(name.get());
			virtualFile.putUserData(reportPanelKey, panel);
			FileEditorManager.getInstance(project).openFile(virtualFile, true);
		});
	}


	public static String decanoniseRegex(String regex){
		return regex.replace("[A-Za-z_$][\\w$]*", "\\i");
	}

	public static String decanoniseReplace(String replace, String plugin){
		return replace.replace("Vencord.Plugins.plugins[\"" + plugin + "\"]", "$self");
	}

	// todo: finish this
	public static ActionListener getActionEvent(JSONObject patch, String title){
		return e->{
			// search through the src/plugins and src/userplugins directories for the plugin
			/*File pluginsDir = new File(Utils.project.getBasePath() + "/src/plugins");
			File userPluginsDir = new File(Utils.project.getBasePath() + "/src/userplugins");
			File apiDir = new File(Utils.project.getBasePath() + "/src/plugins/_api");
			File corePluginsDir = new File(Utils.project.getBasePath() + "/src/plugins/_core");*/

			String patchPlugin = patch.getString("plugin");
			String patchFind = patch.getString("find");
			patchFind = decanoniseRegex(patchFind);
			patchFind = StringUtils.escape(patchFind);
			String patchMatch = "";
			String patchReplace = "";


			if(patch.has("replacement")){
				JSONArray replacements = patch.getJSONArray("replacement");
				for(Object replacementObj : replacements){
					JSONObject replacementJson = (JSONObject) replacementObj;
					if(replacementJson.has("match")){
						patchMatch = replacementJson.getString("match");
						patchMatch = decanoniseRegex(patchMatch);
					}
					if(replacementJson.has("replace")){
						patchReplace = replacementJson.getString("replace");
						patchReplace = decanoniseReplace(patchReplace, patchPlugin);
					}
				}
			}

			Logs.info("Checking for plugin: " + patchPlugin);

			//Utils.findStringInAllFiles(Utils.project, patchMatch.equals("") ? patchFind.equals("") ? patchPlugin : patchFind : patchMatch);
			String finalPatchFind = patchFind;
			String finalPatchReplace = patchReplace;
			String finalPatchMatch = patchMatch;
			Utils.findAllStringsInAllFiles(Utils.project, (psiFile, results)->{ // seems to work okay
				String strToFind = patchPlugin;
				if(title.startsWith("Found No Module")){
					// if this is a patch where the module couldn't be found, the "find" is the culprit, so go to that
					strToFind = finalPatchFind;
				}else if(title.startsWith("Errored Patch")){
					// if this is a patch that errored, the "replace" is the culprit, so go to that
					strToFind = finalPatchReplace;
				}else if(!finalPatchMatch.isEmpty()){
					strToFind = finalPatchMatch;
				}
				TextRange range = results.get(strToFind);
				if(range == null){
					Logs.info("Couldn't find range for: " + strToFind);
					range = results.get(results.keySet().iterator().next());
				}
				Utils.openFileInEditor(psiFile.getVirtualFile(), range);
				return null;
			}, patchPlugin, patchFind, patchMatch, patchReplace);
		};
	}

}
