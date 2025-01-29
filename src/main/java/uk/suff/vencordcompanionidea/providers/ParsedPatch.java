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

import java.util.ArrayList;

// todo: tidy entire thing
public class ParsedPatch{

	private String find;

	private String findFlags = "";
	private String findType;
	private ArrayList<PatchReplacement> replacements;
	private TextRange range;
	private boolean isPatchable = true; // if the replacement is a function, it is not locally patchable since we're not in TS in this plugin, plus could have side effects
	private String pluginName = null;
	private TextRange findRange;
	public JSProperty findObject;
	private TextRange replacementRange;
	public JSProperty replacementObject;
	private ArrayList<TextRange> replaceRange;
	public ArrayList<JSObjectLiteralExpression> replaceObjects;
	private ArrayList<TextRange> matchRange;
	public ArrayList<JSObjectLiteralExpression> matchObjects;


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

	public TextRange getFindRange(){
		return findRange;
	}

	public ParsedPatch setFindRange(TextRange findRange){
		this.findRange = findRange;
		return this;
	}

	public TextRange getReplacementRange(){
		return replacementRange;
	}

	public ParsedPatch setReplacementRange(TextRange replacementRange){
		this.replacementRange = replacementRange;
		return this;
	}

	public ArrayList<TextRange> getReplaceRange(){
		return replaceRange;
	}

	public ParsedPatch setReplaceRange(ArrayList<TextRange> replaceRange){
		this.replaceRange = replaceRange;
		return this;
	}

	public ParsedPatch addReplaceRange(TextRange replaceRange){
		if(this.replaceRange == null){
			this.replaceRange = new ArrayList<>();
		}
		this.replaceRange.add(replaceRange);
		return this;
	}

	public ArrayList<TextRange> getMatchRange(){
		return matchRange;
	}

	public ParsedPatch setMatchRange(ArrayList<TextRange> matchRange){
		this.matchRange = matchRange;
		return this;
	}

	public ParsedPatch addMatchRange(TextRange matchRange){
		if(this.matchRange == null){
			this.matchRange = new ArrayList<>();
		}
		this.matchRange.add(matchRange);
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
	public static ArrayList<ParsedPatch> fromPatchesProperty(PsiElement element){
		ArrayList<ParsedPatch> patches = new ArrayList<>();

		// check if this is a JSProperty
		if(!(element instanceof JSProperty jsProperty)) return patches;

		// check if the property name is "patches"
		if(!"patches".equals(jsProperty.getName())) return patches;

		// check if the value of the property is an array
		if(!(jsProperty.getValue() instanceof JSArrayLiteralExpression patchesArray)) return patches;

		// iterate through the elements of the array
		for(JSExpression expression : patchesArray.getExpressions()){
			ParsedPatch parsedPatch = fromPatchObject(expression);
			if(parsedPatch != null) patches.add(parsedPatch);

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

	public static ParsedPatch fromPatchObject(PsiElement element){
		ParsedPatch parsedPatch = new ParsedPatch();

		// check if the element is an object
		if(!(element instanceof JSObjectLiteralExpression patchObject)) return null;

		// check if the object has a property named "find"
		if(patchObject.findProperty("find") == null) return null;

		// check if the object has a property named "replacement"
		if(patchObject.findProperty("replacement") == null) return null;

		// get the value of the "find" property
		JSProperty findProperty = patchObject.findProperty("find");
		if(findProperty == null) return null;
		if(!(findProperty.getValue() instanceof JSLiteralExpression findLiteral)) return null;

		// check if the "find" property is a string or a regex
		if(!(findLiteral.isRegExpLiteral() || findLiteral.isQuotedLiteral())) return null;

		// get the value of the "replacement" property
		JSProperty replacementProperty = patchObject.findProperty("replacement");
		if(replacementProperty == null) return null;

		// check if the "replacement" property is an object or an array
		if(!(replacementProperty.getValue() instanceof JSObjectLiteralExpression || replacementProperty.getValue() instanceof JSArrayLiteralExpression))
			return null;

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

		if(replacementArray.isEmpty()) return null;

		// iterate through the elements of the replacement array
		for(JSObjectLiteralExpression replacementObject : replacementArray){
			// get the value of the "match" property
			JSProperty matchProperty = replacementObject.findProperty("match");
			if(matchProperty == null) return null;

			// check if the "match" property is a string or a regex
			if(!(matchProperty.getValue() instanceof JSLiteralExpression matchLiteral)) return null;
			if(!(matchLiteral.isRegExpLiteral() || matchLiteral.isQuotedLiteral())) return null;

			// get the value of the "replace" property
			JSProperty replaceProperty = replacementObject.findProperty("replace");
			if(replaceProperty == null) return null;

			// check if the "replace" property is a string or a function
			if(!(replaceProperty.getValue() instanceof JSLiteralExpression ||
				 replaceProperty.getValue() instanceof JSFunctionExpression ||
				 replaceProperty.getValue() instanceof JSReferenceExpression
			))
				return null;


			String replaceString = "";
			String replaceType = "string";

			// if the "replace" property is a string, check if it is a string
			if(replaceProperty.getValue() instanceof JSLiteralExpression replaceLiteral){
				if(!replaceLiteral.isQuotedLiteral()) return null;
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
					if(reference == null) return null;
					PsiElement resolve = reference.resolve();
					if(resolve == null) return null;
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
				int start = regexString.indexOf("/") == 0 ? 1 : 0;
				int end = regexString.lastIndexOf("/") == regexString.indexOf("/") ? regexString.length() : regexString.lastIndexOf("/");
				matchString = regexString.substring(start, end);
				matchFlags = end + 1 < regexString.length() ? regexString.substring(end + 1) : "";
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

		if(parsedPatch.getReplacements() == null || parsedPatch.getReplacements().isEmpty()) return null;

		TextRange textRange = InlayHintsUtils.INSTANCE.getTextRangeWithoutLeadingCommentsAndWhitespaces(element);
		int length = element.getContainingFile().getTextLength();
		TextRange adjustedRange = new TextRange(Integer.min(textRange.getStartOffset(), length), Integer.min(textRange.getStartOffset(), length));

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

		parsedPatch.setFindRange(findLiteral.getTextRange());
		parsedPatch.findObject = findProperty;

		parsedPatch.setReplacementRange(replacementProperty.getTextRange());
		parsedPatch.replacementObject = replacementProperty;

		for(JSObjectLiteralExpression replacementObject : replacementArray){
			if(replacementObject.getTextRange() != null){
				parsedPatch.addReplaceRange(replacementObject.getTextRange());
			}
		}

		for(JSObjectLiteralExpression replacementObject : replacementArray){
			JSProperty matchProperty = replacementObject.findProperty("match");
			if(matchProperty != null && matchProperty.getTextRange() != null){
				parsedPatch.addMatchRange(matchProperty.getTextRange());
			}
		}


		return parsedPatch;
	}

	// todo: update, very lazy check for testing
	public static ParsedPatch fromFindLiteral(PsiElement element){
		// check if the element is a literal string or regex
		if(!(element instanceof JSLiteralExpression findLiteral)) return null;

		// check if the element is associated with a property
		if(!(element.getParent() instanceof JSProperty findProperty)) return null;

		// check if the property is named "find"
		if(!"find".equals(findProperty.getName())) return null;

		return fromPatchObject(findProperty.getParent());
	}

	public static ParsedPatch fromMatchLiteral(PsiElement element){
		// check if the element is a literal string or regex
		if(!(element instanceof JSLiteralExpression matchLiteral)) return null;

		// check if the element is associated with a property
		if(!(element.getParent() instanceof JSProperty matchProperty)) return null;

		// check if the property is named "match"
		if(!"match".equals(matchProperty.getName())) return null;

		// check if match is in an object
		if(!(matchProperty.getParent() instanceof JSObjectLiteralExpression replacementObject)) return null;
		// check if the is object in an array
		PsiElement replacementValue = replacementObject;
		if((replacementObject.getParent() instanceof JSArrayLiteralExpression replacementArray)){
			replacementValue = replacementArray;
		}

		// check the parent name is "replacement"
		if(!(replacementValue.getParent() instanceof JSProperty replacementProperty) || !"replacement".equals(replacementProperty.getName())) return null;

		// check if the replacement is in an object
		if(!(replacementProperty.getParent() instanceof JSObjectLiteralExpression patchObject)) return null;

		return fromPatchObject(patchObject);
	}

	public static ParsedPatch fromReplaceLiteral(PsiElement element){
		// check if the element is a literal string or regex
		if(!(element instanceof JSLiteralExpression replaceLiteral)) return null;

		// check if the element is associated with a property
		if(!(element.getParent() instanceof JSProperty replaceProperty)) return null;

		// check if the property is named "replace"
		if(!"replace".equals(replaceProperty.getName())) return null;

		// check if replace is in an object
		if(!(replaceProperty.getParent() instanceof JSObjectLiteralExpression replacementObject)) return null;

		// check if the is object in an array
		PsiElement replacementValue = replacementObject;
		if((replacementObject.getParent() instanceof JSArrayLiteralExpression replacementArray)){
			replacementValue = replacementArray;
		}

		// check the parent name is "replacement"
		if(!(replacementValue.getParent() instanceof JSProperty replacementProperty) || !"replacement".equals(replacementProperty.getName())) return null;

		// check if the replacement is in an object
		if(!(replacementProperty.getParent() instanceof JSObjectLiteralExpression patchObject)) return null;

		return fromPatchObject(patchObject);
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
