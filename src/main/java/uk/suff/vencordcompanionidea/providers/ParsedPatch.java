package uk.suff.vencordcompanionidea.providers;

import com.intellij.codeInsight.hints.InlayHintsUtils;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.impl.JSArrayLiteralExpressionImpl;
import com.intellij.lang.javascript.psi.types.primitives.JSStringType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.json.*;
import uk.suff.vencordcompanionidea.Utils;
import uk.suff.vencordcompanionidea.config.AppSettings;

import java.util.*;

public class ParsedPatch{

	private String find;

	private String findFlags = "";
	private String findType;
	private ArrayList<PatchReplacement> replacements;
	private TextRange range;
	private boolean isPatchable = true; // if the replacement is a function, it is not locally patchable since we're not in TS in this plugin, plus could have side effects
	private String pluginName = null;


	public String getPluginName(){
		return pluginName;
	}

	public ParsedPatch setPluginName(String pluginName){
		this.pluginName = pluginName;
		return this;
	}

	public TextRange getRange(){
		return range;
	}

	public ParsedPatch setRange(TextRange range){
		this.range = range;
		return this;
	}

	public ArrayList<PatchReplacement> getReplacements(){
		return replacements;
	}

	public ParsedPatch setReplacements(ArrayList<PatchReplacement> replacements){
		this.replacements = replacements;
		return this;
	}

	public ParsedPatch addReplacement(PatchReplacement replacement){
		if(replacements == null){
			replacements = new ArrayList<>();
		}
		replacements.add(replacement);
		return this;
	}

	public String getFind(){
		return find;
	}

	public ParsedPatch setFind(String find){
		this.find = find;
		return this;
	}

	public String getFindFlags(){
		return findFlags;
	}

	public ParsedPatch setFindFlags(String findFlags){
		this.findFlags = findFlags;
		return this;
	}

	public String getFindType(){
		return findType;
	}

	public ParsedPatch setFindType(String findType){
		this.findType = findType;
		return this;
	}

	public boolean isPatchable(){
		return isPatchable;
	}

	public ParsedPatch setPatchable(boolean isLocallyPatchable){
		this.isPatchable = isLocallyPatchable;
		return this;
	}

	// todo: tidy this mess
	public static ArrayList<ParsedPatch> fromElement(PsiElement element){
		ArrayList<ParsedPatch> patches = new ArrayList<>();

		// check if this is a JSProperty
		if(!(element instanceof JSProperty jsProperty)) return patches;

		// check if the property name is "patches"
		if(!"patches".equals(jsProperty.getName())) return patches;

		// check if the value of the property is an array
		if(!(jsProperty.getValue() instanceof JSArrayLiteralExpression patchesArray)) return patches;

		// iterate through the elements of the array
		for(JSExpression expression : patchesArray.getExpressions()){
			ParsedPatch parsedPatch = new ParsedPatch();
			// check if the element is an object
			if(!(expression instanceof JSObjectLiteralExpression patchObject)) continue;

			// check if the object has a property named "find"
			if(patchObject.findProperty("find") == null) continue;

			// check if the object has a property named "replacement"
			if(patchObject.findProperty("replacement") == null) continue;

			// get the value of the "find" property
			JSProperty findProperty = patchObject.findProperty("find");
			if(findProperty == null) continue;
			if(!(findProperty.getValue() instanceof JSLiteralExpression findLiteral)) continue;

			// check if the "find" property is a string or a regex
			if(!(findLiteral.isRegExpLiteral() || findLiteral.isQuotedLiteral())) continue;

			// get the value of the "replacement" property
			JSProperty replacementProperty = patchObject.findProperty("replacement");
			if(replacementProperty == null) continue;

			// check if the "replacement" property is an object or an array
			if(!(replacementProperty.getValue() instanceof JSObjectLiteralExpression || replacementProperty.getValue() instanceof JSArrayLiteralExpression))
				continue;

			ArrayList<JSObjectLiteralExpression> replacementArray = new ArrayList<>();
			// if the "replacement" property is not an array, convert it to one
			if(replacementProperty.getValue() instanceof JSObjectLiteralExpression replacementObject){
				replacementArray.add(replacementObject);
			}else{
				for(JSExpression replacementExpression : ((JSArrayLiteralExpressionImpl) replacementProperty.getValue()).getExpressions()){
					if(replacementExpression instanceof JSObjectLiteralExpression replacementObject){
						replacementArray.add(replacementObject);
					}
				}
			}

			if(replacementArray.isEmpty()) continue;

			// iterate through the elements of the replacement array
			for(JSObjectLiteralExpression replacementObject : replacementArray){
				// get the value of the "match" property
				JSProperty matchProperty = replacementObject.findProperty("match");
				if(matchProperty == null) continue;

				// check if the "match" property is a string or a regex
				if(!(matchProperty.getValue() instanceof JSLiteralExpression matchLiteral)) continue;
				if(!(matchLiteral.isRegExpLiteral() || matchLiteral.isQuotedLiteral())) continue;

				// get the value of the "replace" property
				JSProperty replaceProperty = replacementObject.findProperty("replace");
				if(replaceProperty == null) continue;

				// check if the "replace" property is a string or a function
				if(!(replaceProperty.getValue() instanceof JSLiteralExpression ||
					 replaceProperty.getValue() instanceof JSFunctionExpression ||
					 replaceProperty.getValue() instanceof JSReferenceExpression
				))
					continue;


				String replaceString = "";
				String replaceType = "string";

				// if the "replace" property is a string, check if it is a string
				if(replaceProperty.getValue() instanceof JSLiteralExpression replaceLiteral){
					if(!replaceLiteral.isQuotedLiteral()) continue;
					replaceString = replaceLiteral.getStringValue();
				}else if(replaceProperty.getValue() instanceof JSFunctionExpression replaceFnExp){
					String parameterList = replaceFnExp.getParameterList().getText();
					JSBlockStatement block = replaceFnExp.getBlock();
					if(block != null){
						String body = block.getText();
						replaceString = parameterList + " => " + body;
					}else{
						// function body is a single line expression
						replaceString = replaceFnExp.getText();
					}
					replaceType = "function";
					parsedPatch.isPatchable = true;
				}else{
					JSFunction replaceFn = null;
					replaceType = "function";
					replaceString = "";
					parsedPatch.isPatchable = false;
					// if the "replace" property is a function reference, resolve the function
					if(replaceProperty.getValue() instanceof JSReferenceExpression replaceFnRef){
						PsiReference reference = replaceFnRef.getReference();
						if(reference == null) continue;
						PsiElement resolve = reference.resolve();
						if(resolve == null) continue;
						if(resolve instanceof JSFunction replaceJsFn){
							String parameterList = replaceJsFn.getParameterList().getText();
							String body = replaceJsFn.getBlock().getText();
							replaceString = parameterList + " => " + body;
							parsedPatch.isPatchable = true;
						}else if(resolve instanceof JSVariable replaceJsVar){
							JSVarStatement statement = replaceJsVar.getStatement();
							if(statement != null){
								JSVariable[] variables = statement.getVariables();
								if(variables.length == 1){
									JSVariable variable = variables[0];
									if(variable.getInitializer() != null && variable.getInitializer() instanceof JSFunctionExpression replaceFnExp){
										String parameterList = replaceFnExp.getParameterList().getText();
										String body = replaceFnExp.getBlock().getText();
										replaceString = parameterList + " => " + body;
										parsedPatch.isPatchable = true;
									}
								}
							}
						}
					}else if(replaceProperty.getValue() instanceof JSFunctionExpression replaceFnExp){
						replaceFn = replaceFnExp;
					}

					// check if the function is a valid replace function (match: string, ...groups: string[]) => string;
					if(replaceFn != null && replaceFn.getReturnType() != null && replaceFn.getReturnType() instanceof JSStringType){
						JSParameterList parameterList = replaceFn.getParameterList();
						if(parameterList != null){
							replaceString = replaceFn.getText();
							parsedPatch.isPatchable = true;
						}
					}
				}

				String matchString = matchLiteral.getStringValue();
				String matchFlags = "";
				String matchType = "string";
				if(matchLiteral.isRegExpLiteral()){
					String regexString = matchLiteral.getText();
					matchString = regexString.substring(1, regexString.lastIndexOf("/"));
					matchFlags = regexString.substring(matchString.length() + 2);
					matchType = "regex";
				}

				// create a new PatchReplacement object
				PatchReplacement patchReplacement = new PatchReplacement()
						.setMatch(matchString)
						.setMatchFlags(matchFlags)
						.setMatchType(matchType)
						.setReplace(replaceString)
						.setReplaceType(replaceType);

				parsedPatch.addReplacement(patchReplacement);
			}

			if(parsedPatch.getReplacements() == null || parsedPatch.getReplacements().isEmpty()) continue;

			var textRange = InlayHintsUtils.INSTANCE.getTextRangeWithoutLeadingCommentsAndWhitespaces(expression);
			var length = expression.getContainingFile().getTextLength();
			var adjustedRange = new TextRange(Integer.min(textRange.getStartOffset(), length), Integer.min(textRange.getStartOffset(), length));


			String findString = findLiteral.getStringValue();
			String findFlags = "";
			String findType = "string";
			if(findLiteral.isRegExpLiteral()){
				String regexString = findLiteral.getText();
				findString = regexString.substring(1, regexString.lastIndexOf("/"));
				findFlags = regexString.substring(findString.length() + 2);
				findType = "regex";
			}

			parsedPatch
					.setFind(findString)
					.setFindFlags(findFlags)
					.setFindType(findType)
					.setRange(adjustedRange);

			patches.add(parsedPatch);

			try{
				// get the plugin name
				PsiElement parent = element.getParent();
				if(parent instanceof JSObjectLiteralExpression parentObject){
					JSProperty pluginProperty = parentObject.findProperty("name");
					if(pluginProperty != null && pluginProperty.getValue() instanceof JSLiteralExpression pluginLiteral){
						patches.forEach(patch->patch.setPluginName(pluginLiteral.getStringValue()));
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}

		return patches;
	}

	public JSONObject toTestData(boolean applyPatch){
		JSONObject json = new JSONObject();
		json.put("type", "testPatch");
		json.put("applyPatch", applyPatch);

		JSONObject data = new JSONObject();

		data.put("findType", getFindType().equals("regex") ? Utils.FindType.REGEX.ordinal() : Utils.FindType.STRING.ordinal());
		data.put("find", getFindType().equals("regex") ? "/" + getFind() + "/" + getFindFlags() : getFind());
		data.put("pluginName", getPluginName());

		JSONArray replacements = new JSONArray();

		for(PatchReplacement patchReplacement : getReplacements()){
			JSONObject replacement = new JSONObject();
			JSONObject match = new JSONObject();
			if(patchReplacement.getMatchType().equals("regex")){
				match.put("type", "regex");
				JSONObject matchValue = new JSONObject();
				matchValue.put("pattern", patchReplacement.getMatch());
				matchValue.put("flags", patchReplacement.getMatchFlags());
				match.put("value", matchValue);
			}else{
				match.put("type", "string");
				match.put("value", patchReplacement.getMatch());
			}

			JSONObject replace = new JSONObject();
			replace.put("type", patchReplacement.getReplaceType());
			replace.put("value", patchReplacement.getReplace());

			replacement.put("match", match);
			replacement.put("replace", replace);
			replacements.put(replacement);
		}

		data.put("replacement", replacements);
		json.put("data", data);

		return json;
	}

	public JSONObject toExtractData(boolean applyPatch){
		JSONObject outerData = new JSONObject();
		outerData.put("type", "extract");
		JSONObject data = new JSONObject();
		data.put("extractType", "search");
		data.put("applyPatch", applyPatch);

		data.put("findType", getFindType().equals("regex") ? Utils.FindType.REGEX.ordinal() : Utils.FindType.STRING.ordinal());
		data.put("idOrSearch", getFindType().equals("regex") ? "/" + getFind() + "/" + getFindFlags() : getFind());
		data.put("pluginName", getPluginName());

		JSONArray replacements = new JSONArray();

		if(AppSettings.dynamicPatches()){
			for(PatchReplacement patchReplacement : getReplacements()){
				JSONObject replacement = new JSONObject();
				JSONObject match = new JSONObject();
				if(patchReplacement.getMatchType().equals("regex")){
					match.put("type", "regex");
					JSONObject matchValue = new JSONObject();
					matchValue.put("pattern", patchReplacement.getMatch());
					matchValue.put("flags", patchReplacement.getMatchFlags());
					match.put("value", matchValue);
				}else{
					match.put("type", "string");
					match.put("value", patchReplacement.getMatch());
				}

				JSONObject replace = new JSONObject();
				replace.put("type", patchReplacement.getReplaceType());
				replace.put("value", patchReplacement.getReplace());

				replacement.put("match", match);
				replacement.put("replace", replace);
				replacements.put(replacement);
			}
		}

		data.put("replacement", replacements);

		outerData.put("data", data);

		return outerData;
	}

	public JSONObject toDiffData(boolean applyPatch){
		JSONObject outerData = new JSONObject();
		outerData.put("type", "diff");
		JSONObject data = new JSONObject();
		data.put("extractType", "search");
		data.put("applyPatch", applyPatch);

		data.put("findType", getFindType().equals("regex") ? Utils.FindType.REGEX.ordinal() : Utils.FindType.STRING.ordinal());
		data.put("idOrSearch", getFindType().equals("regex") ? "/" + getFind() + "/" + getFindFlags() : getFind());
		data.put("pluginName", getPluginName());

		JSONArray replacements = new JSONArray();

		if(AppSettings.dynamicPatches()){
			for(PatchReplacement patchReplacement : getReplacements()){
				JSONObject replacement = new JSONObject();
				JSONObject match = new JSONObject();
				if(patchReplacement.getMatchType().equals("regex")){
					match.put("type", "regex");
					JSONObject matchValue = new JSONObject();
					matchValue.put("pattern", patchReplacement.getMatch());
					matchValue.put("flags", patchReplacement.getMatchFlags());
					match.put("value", matchValue);
				}else{
					match.put("type", "string");
					match.put("value", patchReplacement.getMatch());
				}

				JSONObject replace = new JSONObject();
				replace.put("type", patchReplacement.getReplaceType());
				replace.put("value", patchReplacement.getReplace());

				replacement.put("match", match);
				replacement.put("replace", replace);
				replacements.put(replacement);
			}
		}

		data.put("replacement", replacements);

		outerData.put("data", data);

		return outerData;
	}

	public static class PatchReplacement{

		private String match;
		private String matchFlags = "";
		private String matchType;
		private String replace;
		private String replaceType;

		public PatchReplacement(){}

		public PatchReplacement setMatch(String match){
			this.match = match;
			return this;
		}

		public PatchReplacement setMatchFlags(String matchFlags){
			this.matchFlags = matchFlags;
			return this;
		}

		public PatchReplacement setMatchType(String matchType){
			this.matchType = matchType;
			return this;
		}

		public PatchReplacement setReplace(String replace){
			this.replace = replace;
			return this;
		}

		public PatchReplacement setReplaceType(String replaceType){
			this.replaceType = replaceType;
			return this;
		}

		public String getMatch(){
			return match;
		}

		public String getMatchFlags(){
			return matchFlags;
		}

		public String getMatchType(){
			return matchType;
		}

		public String getReplace(){
			return replace;
		}

		public String getReplaceType(){
			return replaceType;
		}
	}
}
