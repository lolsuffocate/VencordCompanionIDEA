package uk.suff.vencordcompanionidea.providers;

import com.intellij.lang.annotation.*;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import uk.suff.vencordcompanionidea.*;

import java.util.ArrayList;
import java.util.concurrent.*;

public class PatchUnderliner implements Annotator{

	@Override
	public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder){
		try{
			if(!Utils.isVencordProject(element.getProject()) || !Utils.isCompanionConnected()) return;

			ParsedPatch patchFind = ParsedPatch.fromFindLiteral(element);
			if(patchFind != null){
				prepFindTest(patchFind);
				testAndAnnotate(holder, patchFind, "find");
				return;
			}

			ParsedPatch patchMatch = ParsedPatch.fromMatchLiteral(element);
			if(patchMatch != null){
				prepMatchTest(patchMatch, element);
				testAndAnnotate(holder, patchMatch, "match");
				return;
			}

			ParsedPatch patchReplace = ParsedPatch.fromReplaceLiteral(element);
			if(patchReplace != null){
				prepReplaceTest(patchReplace, element);
				testAndAnnotate(holder, patchReplace, "replace");
			}
		}catch(Exception e){
			Logs.error(e);
		}
	}

	public void prepFindTest(ParsedPatch parsedPatch){
		parsedPatch.setReplacements(new ArrayList<>());
	}

	public void prepMatchTest(ParsedPatch parsedPatch, PsiElement element){
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
	}

	public void prepReplaceTest(ParsedPatch parsedPatch, PsiElement element){
		String replaceString = ((JSLiteralExpression) element).getStringValue();
		ArrayList<ParsedPatch.PatchReplacement> replacements = parsedPatch.getReplacements();
		replacements.removeIf(replacement->!replacement.getReplace().equals(replaceString) || !replacement.getReplaceType().equals("string"));
		// don't bother forcing the match to match, as if the match doesn't match then it should be obvious the replacement won't have an effect either
	}

	public void testAndAnnotate(AnnotationHolder holder, ParsedPatch parsedPatch, String type){
		try{
			JSONObject json = WebSocketServer.testModuleByPatch(parsedPatch, true).get(500, TimeUnit.MILLISECONDS);
			if(json != null && json.has("error")){
				String error = json.getString("error");
				Utils.ErrorType errorType = Utils.getErrorType(error);
				switch(errorType){
					case FIND_NO_MATCH -> {
						if(type.equals("find")) holder.newAnnotation(HighlightSeverity.ERROR, "No matches found").create();
					}
					case FIND_MULTIPLE_MATCHES -> {
						if(!parsedPatch.isFindAll()){
							int errorCount = Utils.getErrorCount(error);
							holder.newAnnotation(HighlightSeverity.ERROR, errorCount + " matches found, make your find more unique").create();
						}
					}
					case REPLACEMENT_NO_EFFECT ->
							holder.newAnnotation(HighlightSeverity.ERROR, type.equals("match") ? "The found module has no matches for the match property" : "Replacement has no effect").create();
					case null, default -> {
					}
				}
			}
		}catch(Exception e){
			Logs.error(e);
		}
	}

}
