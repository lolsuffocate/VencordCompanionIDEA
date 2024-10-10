package uk.suff.vencordcompanionidea;

import com.intellij.diff.*;
import com.intellij.diff.impl.DiffSettingsHolder;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.execution.*;
import com.intellij.execution.actions.ActivateRunToolWindowAction;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.*;
import com.intellij.execution.ui.*;
import com.intellij.find.*;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.content.*;
import com.intellij.util.ApplicationKt;
import com.intellij.util.ui.EDT;
import kotlin.jvm.functions.Function2;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.*;
import org.json.JSONObject;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class Utils{

	public static Project project;
	public static final Object lock = new Object();
	public static boolean lockComplete = false;

	public static boolean isVencordProject(Project project){
		return project != null && new File(project.getBasePath() + "/src/Vencord.ts").exists() && new File(project.getBasePath() + "/src/VencordNative.ts").exists();
	}

	public static boolean warnWebSocketNotRunning(){
		if(WebSocketServer.server == null || !WebSocketServer.server.isRunning()){
			Utils.alert("WebSocket server is not running", "Please start the WebSocket server before running this action.");
			return true;
		}
		return false;
	}

	public static boolean warnCompanionNotConnected(){
		if(WebSocketServer.sockets.isEmpty()){
			Utils.alert("No Companion Clients", "Please reconnect the Companion in the Vencord Toolbox.");
			return true;
		}
		return false;
	}

	public static void notify(String title, String content, NotificationType... type){
		NotificationType notificationType = NotificationType.INFORMATION;
		if(type.length > 0){
			notificationType = type[0];
		}
		NotificationGroupManager.getInstance()
								.getNotificationGroup("Companion Notification Group")
								.createNotification(title, content, notificationType)
								.notify(project);
	}

	public static void alert(String title, String content, Icon... icon){
		ApplicationKt.getApplication().invokeLater(()->{
			Messages.showMessageDialog(content, title, icon.length > 0 ? icon[0] : Messages.getInformationIcon());
		});
	}

	public static void openNewEditorTab(String title, String content, Project project){
		if(!EDT.isCurrentThreadEdt()){
			ApplicationKt.getApplication().invokeLater(()->{
				openNewEditorTab(title, content, project);
			});
			return;
		}

		ApplicationKt.getApplication().runWriteAction(()->{
			// create virtual file
			VirtualFile virtualFile = new LightVirtualFile(title, content);
			FileEditorManager.getInstance(project).openFile(virtualFile, true);

			PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
			if(psiFile != null){
				// reformat the code
				WriteCommandAction.runWriteCommandAction(project, ()->{
					CodeStyleManager.getInstance(project).reformat(psiFile);
				});
			}
		});
	}

	public static void openFileInEditor(VirtualFile file, TextRange range){
		if(!EDT.isCurrentThreadEdt()){
			ApplicationKt.getApplication().invokeLater(()->{
				openFileInEditor(file, range);
			});
			return;
		}

		ApplicationKt.getApplication().runWriteAction(()->{
			// create virtual file
			FileEditorManager.getInstance(project).openFile(file, true);
			// navigate to line number
			Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
			if(editor != null){
				editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
				editor.getCaretModel().moveToOffset(range.getEndOffset());
				editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
			}

			PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
			if(psiFile != null){
				// reformat the code
				WriteCommandAction.runWriteCommandAction(project, ()->{
					CodeStyleManager.getInstance(project).reformat(psiFile);
				});
			}
		});
	}

	public static void findAllStringsInAllFiles(Project project, Function2<PsiFile, HashMap<String, FindResult>, Void> callback, String... searchStrings){
		if(EDT.isCurrentThreadEdt()){
			new Thread(()->{
				ReadAction.run(()->{
					findAllStringsInAllFiles(project, callback, searchStrings);
				});
			}).start();
			return;
		}
		System.out.println("#### Searching for strings: " + Arrays.toString(searchStrings));
		FindManager findManager = FindManager.getInstance(project);
		FindModel findModel = new FindModel();
		findModel.setCaseSensitive(true);
		findModel.setWholeWordsOnly(false);
		findModel.setRegularExpressions(false);

		GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
		PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);


		searchHelper.processAllFilesWithWordInLiterals(searchStrings[0], searchScope, (psiFile)->{
			if(psiFile == null){
				return true;
			}

			if(!psiFile.getName().endsWith(".ts") && !psiFile.getName().endsWith(".tsx")){
				return true;
			}

			System.out.println("#### Searching in file: " + psiFile.getName());
			try{
				VirtualFile file = psiFile.getVirtualFile();
				if(file != null){
					String fileContent = new String(file.contentsToByteArray());
					boolean isInFile = true;
					HashMap<String, FindResult> results = new HashMap<>();
					for(String searchString : searchStrings){
						if(searchString == null || searchString.isEmpty()){
							continue;
						}
						System.out.println("\t#### Searching for string: " + searchString);
						findModel.setStringToFind(searchString);
						FindResult result = findManager.findString(fileContent, 0, findModel, file);
						if(result.isStringFound()){
							System.out.println("\t\t#### Found");
							results.put(searchString, result);
						}else{
							System.out.println("\t\t#### Not found");
							isInFile = false;
							break;
						}
					}
					if(isInFile){
						System.out.println("#### Found all strings in file: " + file.getPath());
						callback.invoke(psiFile, results);
						return false;
					}
				}
			}catch(IOException e){
				e.printStackTrace();
			}
			return true; // continue searching
		});
	}

	public static void runProcessInRunWindow(Project project, ProcessBuilder processBuilder, String title){
		if(!EDT.isCurrentThreadEdt()){
			ApplicationKt.getApplication().invokeLater(()->{
				runProcessInRunWindow(project, processBuilder, title);
			});
			return;
		}

		ApplicationKt.getApplication().runWriteAction(()->{
			ExecutionEnvironmentBuilder builder = new ExecutionEnvironmentBuilder(project, DefaultRunExecutor.getRunExecutorInstance());
			builder.contentToReuse(null);
			builder.runProfile(new RunProfile(){
				@Override
				public @NotNull RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException{
					return new CommandLineState(environment){
						@Override
						protected @NotNull OSProcessHandler startProcess() throws ExecutionException{
							try{
								Process process = processBuilder.start();
								OSProcessHandler osProcessHandler = new OSProcessHandler(process, processBuilder.command().get(0));
								osProcessHandler.addProcessListener(new ProcessAdapter(){
									@Override
									public void processTerminated(@NotNull ProcessEvent event){
										synchronized(lock){
											lockComplete = true;
											lock.notifyAll();
										}
									}
								});
								return osProcessHandler;
							}catch(IOException e){
								throw new ExecutionException(e);
							}
						}
					};
				}

				@Override
				public @NotNull String getName(){
					return title;
				}

				@Override
				public @Nullable Icon getIcon(){
					return null;
				}

			});
			ExecutionManager.getInstance(project).restartRunProfile(builder.build());
		});
	}

	public static void runProcessInRunWindow(Project project, ProcessBuilder processBuilder, String title, ProcessListener listener){
		if(!EDT.isCurrentThreadEdt()){
			ApplicationKt.getApplication().invokeLater(()->{
				runProcessInRunWindow(project, processBuilder, title, listener);
			});
			return;
		}

		ApplicationKt.getApplication().runWriteAction(()->{
			ExecutionEnvironmentBuilder builder = new ExecutionEnvironmentBuilder(project, DefaultRunExecutor.getRunExecutorInstance());
			builder.contentToReuse(null);
			builder.runProfile(new RunProfile(){
				@Override
				public @NotNull RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException{
					return new CommandLineState(environment){
						@Override
						protected @NotNull OSProcessHandler startProcess() throws ExecutionException{
							try{
								Process process = processBuilder.start();
								OSProcessHandler osProcessHandler = new OSProcessHandler(process, processBuilder.command().get(0));
								osProcessHandler.addProcessListener(new ProcessAdapter(){
									@Override
									public void processTerminated(@NotNull ProcessEvent event){
										synchronized(lock){
											lockComplete = true;
											lock.notifyAll();
										}
									}
								});

								if(listener != null){
									osProcessHandler.addProcessListener(listener);
								}

								return osProcessHandler;
							}catch(IOException e){
								throw new ExecutionException(e);
							}
						}
					};
				}

				@Override
				public @NotNull String getName(){
					return title;
				}

				@Override
				public @Nullable Icon getIcon(){
					return null;
				}

			});
			ExecutionManager.getInstance(project).restartRunProfile(builder.build());
		});
	}

	public static void openNewDiffTab(String title, String origContent, String newContent, Project project){
		if(!EDT.isCurrentThreadEdt()){
			ApplicationKt.getApplication().invokeLater(()->{
				openNewDiffTab(title, origContent, newContent, project);
			});
			return;
		}
		ApplicationKt.getApplication().runWriteAction(()->{
			// create virtual file
			VirtualFile virtualFile = new LightVirtualFile(title, origContent);
			VirtualFile virtualFile2 = new LightVirtualFile(title, newContent);

			PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
			PsiFile psiFile2 = PsiManager.getInstance(project).findFile(virtualFile2);
			if(psiFile != null && psiFile2 != null){
				// reformat the code
				WriteCommandAction.runWriteCommandAction(project, ()->{
					CodeStyleManager.getInstance(project).reformat(psiFile);
					CodeStyleManager.getInstance(project).reformat(psiFile2);
				});
			}
			// Create diff request
			DiffRequest diffRequest = DiffRequestFactory.getInstance().createFromFiles(project, virtualFile, virtualFile2);
			DiffSettingsHolder.DiffSettings diffSettings = DiffSettingsHolder.DiffSettings.getSettings();

			// Show diff
			DiffManager.getInstance().showDiff(project, diffRequest, DiffDialogHints.FRAME);
		});
	}

	public enum FindType{
		STRING,
		REGEX
	}
}
