package uk.suff.vencordcompanionidea.providers;

import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import uk.suff.vencordcompanionidea.WebSocketServer;

import java.util.concurrent.ExecutionException;

public class ExtractedModuleReferenceProvider extends PsiReferenceProvider{

	@Override
	public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context){
		if(!element.getContainingFile().getName().endsWith(".js")) return PsiReference.EMPTY_ARRAY;
		System.out.println("ExtractedModuleReferenceProvider - checking: " + element.getContainingFile().getName());

		if(element instanceof JSReferenceExpression jsRefExpr){
			JSExpression qualifier = jsRefExpr.getQualifier();
			if(qualifier instanceof JSReferenceExpression qualifierRef){
				String moduleVarName = qualifierRef.getReferencedName();
				String propertyName = jsRefExpr.getReferencedName();

				if(moduleVarName != null && propertyName != null){
					// resolve the moduleVarName to the n(123456) call so we can pull the module id
					if(qualifierRef.resolve() instanceof JSVariable variable){
						for(@NotNull PsiElement child : variable.getChildren()){
							if(child instanceof JSCallExpression call){
								if(call.getArgumentList() != null && call.getArgumentList().getArguments().length == 1){
									JSExpression argument = call.getArgumentList().getArguments()[0];
									String moduleName = argument.getText();
									if(moduleName.matches("\\d+")){
										int moduleId = Integer.parseInt(moduleName);
										if(WebSocketServer.literallyEveryWebpackModule.containsKey(moduleId)){
											PsiFile fileFromText = WebSocketServer.literallyEveryWebpackModule.get(moduleId);

											// Reformat the file
											ApplicationManager.getApplication().invokeLater(()->{
												WriteCommandAction.runWriteCommandAction(element.getProject(), ()->{
													CodeStyleManager.getInstance(element.getProject()).reformat(fileFromText);
												});
											});

											PsiElement propertyElement = findPropertyInModule(fileFromText, propertyName);
											if(propertyElement != null){
												return new PsiReference[]{new PsiReferenceBase.Immediate<>(element, propertyElement)};
											}
										}else{
											JSONObject json;
											try{
												json = WebSocketServer.extractModuleById(moduleId).get();
												if(json != null && json.has("data")){
													String moduleStr = json.getString("data");
													moduleStr = moduleStr.replaceAll("\\(0,([^)]{1,7})\\)\\(", " $1("); // todo undo
													PsiFile fileFromText = PsiFileFactory.getInstance(element.getProject()).createFileFromText("module" + moduleId + ".js", JavascriptLanguage.INSTANCE, moduleStr);

													ApplicationManager.getApplication().invokeLater(()->{
														WriteCommandAction.runWriteCommandAction(element.getProject(), ()->{
															CodeStyleManager.getInstance(element.getProject()).reformat(fileFromText);
														});
													});

													WebSocketServer.literallyEveryWebpackModule.put(moduleId, fileFromText);
												}
											}catch(InterruptedException | ExecutionException e){
												e.printStackTrace();
											}
										}
									}
								}
							}
						}

					}
				}
			}
		}
		return PsiReference.EMPTY_ARRAY;
	}

	// modules are a single function, so we can just find the property by name
	// function(module, exports, require) { ... }
	// so we need to find wherever there are instances of require.something(exports, {property: value}) or exports.property = value
	//exports and require will not actually be named exports and require, but they will always be the last two arguments of the function
	//usually in the form of function (e, t, n) { ... }
	// we are looking for either something like t.property = value or n.d(t, {property: value})
	// todo: cover all cases
	private PsiElement findPropertyInModule(PsiFile moduleFile, String searchPropertyName){
		SyntaxTraverser<PsiElement> traverser = SyntaxTraverser.psiTraverser(moduleFile);
		String requireName = null;
		String exportsName = null;

		int i = 0;
		System.out.println("'''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''");
		for(PsiElement element : traverser.preOrderDfsTraversal()){
			System.out.println("findPropertyInModule2 - element: " + element + ", i: " + i++);
			if(requireName == null && exportsName == null){
				if(element instanceof JSFunction function){
					JSParameterListElement[] parameters = function.getParameterList().getParameters();
					if(parameters.length >= 2){
						exportsName = parameters[parameters.length - 2].getName();
						requireName = parameters[parameters.length - 1].getName();
					}
				}
			}else{
				// traverse the elements, looking for usages of require

				// looking for the pattern require.something(exports, {property: value})
				if(element instanceof JSCallExpression call){
					JSExpression callee = call.getMethodExpression();
					if(callee instanceof JSReferenceExpression calleeRef){
						// check if the callee is a reference to require
						if(calleeRef.getQualifier() != null && calleeRef.getQualifier().getText().equals(requireName) && call.getArgumentList() != null){
							JSExpression[] arguments = call.getArgumentList().getArguments();
							if(arguments.length >= 2){
								JSExpression exportsArg = arguments[arguments.length - 2];
								JSExpression objectArg = arguments[arguments.length - 1];
								if(exportsArg instanceof JSReferenceExpression && objectArg instanceof JSObjectLiteralExpression objectLiteral){
									for(JSProperty property : objectLiteral.getProperties()){
										if(property.getName() != null && property.getName().equals(searchPropertyName)){
											return property;
										}
									}
								}
							}
						}
					}

					// looking for the pattern exports.property = value
				}else if(element instanceof JSAssignmentExpression assignment){
					JSExpression left = assignment.getLOperand();
					if(left instanceof JSDefinitionExpression propertyAccess){
						if(propertyAccess.getExpression() instanceof JSReferenceExpression propertyRef && searchPropertyName.equals(propertyRef.getReferencedName())){
							if(propertyRef.getQualifier() instanceof JSReferenceExpression exportsRef && exportsName.equals(exportsRef.getReferencedName())){
								return propertyRef;
							}
						}
					}
				}
			}
		}
		return moduleFile;
	}
}
