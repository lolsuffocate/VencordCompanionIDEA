package uk.suff.vencordcompanionidea.providers;

import com.intellij.lang.annotation.*;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import uk.suff.vencordcompanionidea.*;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class PatchUnderliner implements Annotator{

	@Override
	public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder){
		try{
			if(!Utils.isVencordProject(element.getProject()) || !Utils.isCompanionConnected()) return;
			ParsedPatch parsedPatch = ParsedPatch.fromFindLiteral(element);
			if(parsedPatch != null){
				parsedPatch.setReplacements(new ArrayList<>()); // we only want to test the find part here
				System.out.println("Found patch find:" + element.getText());
				JSONObject json = null;
				try{
					json = WebSocketServer.testModuleByPatch(parsedPatch, true).get();
				}catch(InterruptedException | ExecutionException e){
					e.printStackTrace();
				}
				System.out.println("json = " + json);
				System.out.println("Invoking later");
				if(json != null && json.has("error")){
					String error = json.getString("error");
					Utils.ErrorType errorType = Utils.getErrorType(error);
					if(errorType == Utils.ErrorType.FIND_NO_MATCH){
						System.out.println("[" + element.getContainingFile().getName() + "] No matches found for: " + element.getText());
						holder.newAnnotation(HighlightSeverity.ERROR, "No matches found")
							  .create();
					}else if(errorType == Utils.ErrorType.FIND_MULTIPLE_MATCHES){
						int errorCount = Utils.getErrorCount(error);
						System.out.println("[" + element.getContainingFile().getName() + "] Multiple matches found (" + errorCount + ") for: " + element.getText());
						holder.newAnnotation(HighlightSeverity.ERROR, errorCount + " matches found, make your find more unique")
							  .create();
					}
				}
				return;
			}

			parsedPatch = ParsedPatch.fromMatchLiteral(element);
			if(parsedPatch != null){
				System.out.println("Found patch match:" + element.getText());
				ArrayList<ParsedPatch.PatchReplacement> replacements = parsedPatch.getReplacements();
				JSLiteralExpression matchLiteral = (JSLiteralExpression) element;
				String matchString = matchLiteral.getStringValue();
				String matchFlags;
				String matchType;
				if(matchLiteral.isRegExpLiteral()){
					String regexString = matchLiteral.getText();
					matchString = regexString.substring(1, regexString.lastIndexOf("/"));
					matchFlags = regexString.substring(matchString.length() + 2);
					matchType = "regex";
				}else{
					matchFlags = "";
					matchType = "string";
				}
				// filter down the list to only the match we're testing
				String finalMatchString = matchString;
				replacements.removeIf(replacement->!replacement.getMatch().equals(finalMatchString) || !replacement.getMatchType().equals(matchType) || !replacement.getMatchFlags().equals(matchFlags));

				for(ParsedPatch.PatchReplacement replacement : parsedPatch.getReplacements()){
					// intentionally make it so the match has to have an effect if it matches, this way we know if it says the patch had no effect it's because the match didn't match
					replacement.setReplace("some random string nobody would ever put in a patch because why would they");
					replacement.setReplaceType("string");
				}
				JSONObject json = null;
				try{
					json = WebSocketServer.testModuleByPatch(parsedPatch, true).get();
				}catch(InterruptedException | ExecutionException e){
					e.printStackTrace();
				}
				System.out.println("json = " + json);
				System.out.println("Invoking later");
				if(json != null && json.has("error")){
					String error = json.getString("error");
					Utils.ErrorType errorType = Utils.getErrorType(error);
					if(errorType == Utils.ErrorType.REPLACEMENT_NO_EFFECT){
						System.out.println("[" + element.getContainingFile().getName() + "] Match had no effect for: " + element.getText());
						holder.newAnnotation(HighlightSeverity.ERROR, "The found module has no matches for the match property")
							  .create();
					}
				}
				return;
			}

			parsedPatch = ParsedPatch.fromReplaceLiteral(element);
			if(parsedPatch != null){
				String replaceString = ((JSLiteralExpression) element).getStringValue();
				System.out.println("Found patch replace:" + replaceString);
				ArrayList<ParsedPatch.PatchReplacement> replacements = parsedPatch.getReplacements();

				replacements.removeIf(replacement->!replacement.getReplace().equals(replaceString) || !replacement.getReplaceType().equals("string"));
				System.out.println("replacements = " + replacements.stream().map(ParsedPatch.PatchReplacement::getReplace).reduce((a, b)->a + ", " + b).orElse("none"));
				// don't bother forcing the match to match, as if the match doesn't match then it should be obvious the replacement won't have an effect either
				JSONObject json = null;
				try{
					json = WebSocketServer.testModuleByPatch(parsedPatch, true).get();
				}catch(InterruptedException | ExecutionException e){
					e.printStackTrace();
				}
				System.out.println("json = " + json);
				System.out.println("Invoking later");
				if(json != null && json.has("error")){
					String error = json.getString("error");
					Utils.ErrorType errorType = Utils.getErrorType(error);
					if(errorType == Utils.ErrorType.REPLACEMENT_NO_EFFECT){
						System.out.println("[" + element.getContainingFile().getName() + "] Replacement had no effect for: " + element.getText());
						holder.newAnnotation(HighlightSeverity.ERROR, "Replacement has no effect")
							  .create();
					}
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}

}
