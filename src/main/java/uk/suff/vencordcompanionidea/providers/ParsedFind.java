package uk.suff.vencordcompanionidea.providers;

import com.intellij.codeInsight.hints.InlayHintsUtils;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.impl.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.json.JSONObject;

import java.util.ArrayList;

public class ParsedFind{

	private TextRange range;
	private String findString;
	private String findFlags; // if regex
	private String findType; // the type of find being searched (findByCode, findByProps, findStore, findModuleId, findComponentByCode)
	private ArrayList<FindArg> findArgs = new ArrayList<>();

	public ParsedFind(){}

	public static ParsedFind fromExpression(JSCallExpression callExpression){
		ParsedFind parsedFind = new ParsedFind();
		parsedFind.addFind(callExpression);
		return parsedFind;
	}

	// todo tidy this mess
	public static ParsedFind fromElement(PsiElement element){
		if(!(element instanceof JSCallExpressionImpl callExpression) || // make sure it's a method call
		   !(callExpression.getMethodExpression() instanceof JSReferenceExpressionImpl referenceExpression) || // i can't be bothered to test if this is necessary
		   !referenceExpression.getReferenceName().startsWith("find") || // make sure it's a find method
		   referenceExpression.resolve() == null || // make sure the method reference resolves
		   referenceExpression.resolve().getContainingFile() == null || // make sure the method reference resolves to a file
		   !referenceExpression.resolve().getContainingFile().getName().equals("webpack.ts") // make sure it's in the webpack file
		)
			return null;

		var textRange = InlayHintsUtils.INSTANCE.getTextRangeWithoutLeadingCommentsAndWhitespaces(element);
		var length = element.getContainingFile().getTextLength();
		var adjustedRange = new TextRange(Integer.min(textRange.getStartOffset(), length), Integer.min(textRange.getEndOffset(), length));

		return ParsedFind.fromExpression(callExpression).addRange(adjustedRange);
	}

	public ParsedFind addRange(TextRange range){
		this.range = range;
		return this;
	}

	public TextRange getRange(){
		return range;
	}

	public ParsedFind setRange(TextRange range){
		this.range = range;
		return this;
	}

	public ParsedFind addFindString(String findString){
		this.findString = findString;
		return this;
	}

	public String getFindString(){
		return findString;
	}

	public ParsedFind setFindString(String findString){
		this.findString = findString;
		return this;
	}

	public ParsedFind addFindRegex(String findString, String findFlags){
		this.findString = findString;
		this.findFlags = findFlags;
		return this;
	}

	public String getFindFlags(){
		return findFlags;
	}

	public ParsedFind setFindFlags(String findFlags){
		this.findFlags = findFlags;
		return this;
	}

	public ParsedFind addFindFunction(String functionArgs){
		this.findString = functionArgs;
		return this;
	}

	public String getFindType(){
		return findType;
	}

	public ParsedFind setFindType(String findType){
		this.findType = findType;
		return this;
	}

	public ParsedFind addFind(JSCallExpression findCall){
		this.findType = findCall.getMethodExpression().getText();
		this.findArgs = FindArg.fromJSExpression(findCall);
		return this;
	}

	public ArrayList<FindArg> getFindArgs(){
		return findArgs;
	}


	public JSONObject toFindData(){
		JSONObject data = new JSONObject();
		data.put("type", findType);

		ArrayList<JSONObject> args = new ArrayList<>();
		for(FindArg findArg : findArgs){
			JSONObject arg = new JSONObject();
			arg.put("type", findArg.getType());
			if(findArg.getType().equals("regex")){
				JSONObject value = new JSONObject();
				value.put("pattern", findArg.getValue());
				arg.put("flags", findArg.getFlags());
				arg.put("value", value);
				data.put("findType", "regex");
			}else{
				data.put("findType", "string");
				arg.put("value", findArg.getValue());
			}
			args.add(arg);
		}
		data.put("args", args);
		return new JSONObject().put("type", "testFind").put("data", data);
	}


	/*type: "extract",
				data: {
					extractType: "find",
					findType: args.data.type,
					findArgs: args.data.args
				}*/
	public JSONObject toExtractData(){
		JSONObject outerData = new JSONObject();
		outerData.put("type", "extract");
		JSONObject data = new JSONObject();
		data.put("extractType", "find");
		data.put("findType", findType);
		ArrayList<JSONObject> args = new ArrayList<>();
		for(FindArg findArg : findArgs){
			JSONObject arg = new JSONObject();
			arg.put("type", findArg.getType());
			if(findArg.getType().equals("regex")){
				JSONObject value = new JSONObject();
				value.put("pattern", findArg.getValue());
				arg.put("flags", findArg.getFlags());
				arg.put("value", value);
			}else{
				arg.put("value", findArg.getValue());
			}
			args.add(arg);
		}
		data.put("findArgs", args);

		outerData.put("data", data);

		return outerData;
	}

	public static class FindArg{
		private final String type; // string, regex, function
		private final String value; // string representation of the value
		private String flags = ""; // only if regex

		public FindArg(String type, String value){
			this.type = type;
			this.value = value;
		}

		public FindArg(String type, String value, String flags){
			this.type = type;
			this.value = value;
			this.flags = flags;
		}

		public String getType(){
			return type;
		}

		public String getValue(){
			return value;
		}

		public String getFlags(){
			return flags;
		}

		public static FindArg fromJSExpression(JSExpression expression){
			if(expression instanceof JSLiteralExpression literalExpression){
				JSLiteralExpressionKind expressionKind = literalExpression.getExpressionKind(false);
				if(expressionKind == JSLiteralExpressionKind.QUOTED || expressionKind == JSLiteralExpressionKind.TEMPLATE_NO_ARGS){
					return new FindArg("string", literalExpression.getStringValue());
				}else if(expressionKind == JSLiteralExpressionKind.REGEXP){
					String wholeRegex = literalExpression.getText();
					String pattern = wholeRegex.substring(1, wholeRegex.lastIndexOf("/"));
					String flags = wholeRegex.substring(wholeRegex.lastIndexOf("/") + 1);
					return new FindArg("regex", pattern, flags);
				}
				return new FindArg("string", literalExpression.getStringValue());
			}else if(expression instanceof JSReferenceExpression){
				return new FindArg("string", ((JSReferenceExpression) expression).getReferenceName());
			}else if(expression instanceof JSFunctionExpression){
				return new FindArg("function", expression.getText());
			}
			return null;
		}

		public static ArrayList<FindArg> fromJSExpression(JSCallExpression callExpression){
			ArrayList<FindArg> findArgs = new ArrayList<>();
			for(JSExpression expression : callExpression.getArgumentList().getArguments()){
				findArgs.add(FindArg.fromJSExpression(expression));
			}
			return findArgs;
		}
	}

}
