package uk.suff.vencordcompanionidea.providers;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.*;
import org.json.JSONObject;
import uk.suff.vencordcompanionidea.WebSocketServer;

import java.util.concurrent.CompletableFuture;

public class ExtractGoToDeclarationHandler implements GotoDeclarationHandler{
	@Override
	public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor){
		if(sourceElement == null || sourceElement.getParent() == null || !sourceElement.getContainingFile().getName().endsWith(".js")){
			return null;
		}

		if((sourceElement.getParent() instanceof JSLiteralExpression jsel) && (jsel.getExpressionKind(false).isNumeric())){
			if(WebSocketServer.literallyEveryWebpackModule.containsKey(Integer.parseInt(jsel.getText()))){
				System.out.println("Retrieving module: " + jsel.getText());
				PsiFile fileFromText = WebSocketServer.literallyEveryWebpackModule.get(Integer.parseInt(jsel.getText()));
				// reformat the file
				ApplicationManager.getApplication().invokeLater(()->{
					WriteCommandAction.runWriteCommandAction(sourceElement.getProject(), ()->{
						CodeStyleManager.getInstance(sourceElement.getProject()).reformat(fileFromText);
					});
				});

				return new PsiElement[]{fileFromText};
			}else{
				System.out.println("Extracting module: " + jsel.getText());
				int moduleId;
				try{
					moduleId = Integer.parseInt(jsel.getText());
				}catch(NumberFormatException e){
					return null;
				}
				// we need to extract the module from the websocket server
				CompletableFuture<JSONObject> future = WebSocketServer.extractModuleById(moduleId);
				try{
					JSONObject jsonObject = future.get();
					if(jsonObject != null && jsonObject.has("data")){
						// return the data
						String moduleStr = jsonObject.getString("data");
						moduleStr = moduleStr.replaceAll("\\(0,([^)]{1,7})\\)\\(", " $1(");
						PsiFile fileFromText = PsiFileFactory.getInstance(sourceElement.getProject()).createFileFromText("module" + jsel.getText() + ".js", JavascriptLanguage.INSTANCE, moduleStr);

						ApplicationManager.getApplication().invokeLater(()->{
							WriteCommandAction.runWriteCommandAction(sourceElement.getProject(), ()->{
								CodeStyleManager.getInstance(sourceElement.getProject()).reformat(fileFromText);
							});
						});

						return new PsiElement[]{fileFromText};
					}
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}

		return null;
	}

	@Override
	public @Nullable @Nls(capitalization = Nls.Capitalization.Title) String getActionText(@NotNull DataContext context){
		return GotoDeclarationHandler.super.getActionText(context);
	}
}
