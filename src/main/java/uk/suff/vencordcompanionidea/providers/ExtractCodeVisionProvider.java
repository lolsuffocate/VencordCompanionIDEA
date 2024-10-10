package uk.suff.vencordcompanionidea.providers;

import com.intellij.codeInsight.codeVision.*;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ApplicationKt;
import kotlin.Pair;
import org.jetbrains.annotations.*;
import org.json.JSONException;
import uk.suff.vencordcompanionidea.*;
import uk.suff.vencordcompanionidea.config.AppSettings;

import java.util.*;

// we have to have separate providers for the "View Module" and "Test Find/Patch" entries because one provider can't put two lenses in the same position
public class ExtractCodeVisionProvider implements DaemonBoundCodeVisionProvider{
	@Override
	public @NotNull String getGroupId(){
		return ExtractCodeVisionSettingsProvider.GROUP_ID;
	}

	@NotNull
	@Override
	public CodeVisionAnchorKind getDefaultAnchor(){
		return CodeVisionAnchorKind.Top;
	}

	@Nls
	@NotNull
	@Override
	public String getName(){
		return ExtractCodeVisionSettingsProvider.GROUP_NAME;
	}

	@NotNull
	@Override
	public List<CodeVisionRelativeOrdering> getRelativeOrderings(){
		return List.of(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingLast.INSTANCE);
	}

	@NotNull
	@Override
	public String getId(){
		return ExtractCodeVisionSettingsProvider.GROUP_ID;
	}

	@NotNull
	@Override
	public List<Pair<TextRange, CodeVisionEntry>> computeForEditor(@NotNull Editor editor, @NotNull PsiFile file){
		if(!file.getName().endsWith(".ts") && !file.getName().endsWith(".tsx")){
			return Collections.emptyList();
		}
		ArrayList<Pair<TextRange, CodeVisionEntry>> lenses = new ArrayList<>();

		SyntaxTraverser<PsiElement> traverser = SyntaxTraverser.psiTraverser(file);
		for(PsiElement element : traverser.preOrderDfsTraversal()){
			parsePatches(element, lenses);
			parseFinds(element, lenses);
		}

		List<Pair<TextRange, CodeVisionEntry>> pairs = DaemonBoundCodeVisionProvider.super.computeForEditor(editor, file);
		lenses.addAll(pairs);

		return lenses;
	}

	private void parsePatches(PsiElement element, ArrayList<Pair<TextRange, CodeVisionEntry>> lenses){
		ArrayList<ParsedPatch> patches = ParsedPatch.fromElement(element);
		if(patches.isEmpty()) return;

		for(ParsedPatch patch : patches){
			String patchEntryText = "View Module";
			var viewModuleEntry = new ClickableCodeVisionEntry(patchEntryText, getId(), (mouseEvent, editor)->{
				try{
					if(Utils.warnWebSocketNotRunning() || Utils.warnCompanionNotConnected()){
						return;
					}

					boolean applyPatch = AppSettings.applyPatchWhenExtractingByDefault() != mouseEvent.isControlDown();
					WebSocketServer.extractModuleByPatch(patch, applyPatch)
								   .thenAccept(json->{
									   TextRange range = patch.getRange();

									   int startOffset = range.getStartOffset();
									   int endOffset = range.getEndOffset();

									   int finalLineStart = editor.getDocument().getLineStartOffset(editor.getDocument().getLineNumber(startOffset) - 1) + (endOffset - startOffset);

									   try{
										   if(json.has("ok") && !json.getBoolean("ok")){
											   ApplicationKt.getApplication().invokeLater(()->{
												   try{
													   HintManager.getInstance().showErrorHint(editor, json.getString("error"), finalLineStart, range.getEndOffset(), HintManager.RIGHT, HintManager.HIDE_BY_TEXT_CHANGE, 5000);
												   }catch(JSONException e){
													   e.printStackTrace();
												   }
											   });
											   Utils.notify("VencordCompanion", json.getString("error"), NotificationType.ERROR);
										   }else if(json.has("ok") && json.getBoolean("ok") && json.has("data")){
											   String contents = json.getString("data").replaceAll("\\(0,([^)]{1,7})\\)\\(", " $1(");
											   int moduleNum = json.getInt("moduleNumber");

											   ApplicationKt.getApplication().invokeLater(()->{
												   Utils.openNewEditorTab("module" + moduleNum + ".js", contents, editor.getProject());
											   });
										   }

									   }catch(JSONException e){
										   e.printStackTrace();
									   }
								   });
				}catch(Exception e){
					e.printStackTrace();
				}
			}, null, patchEntryText, "", Collections.emptyList());
			viewModuleEntry.setShowInMorePopup(false);
			lenses.add(new Pair<>(patch.getRange(), viewModuleEntry));
		}
	}

	private void parseFinds(PsiElement element, ArrayList<Pair<TextRange, CodeVisionEntry>> lenses){
		ParsedFind parsedFind = ParsedFind.fromElement(element);
		if(parsedFind == null) return;

		var file = element.getContainingFile();

		String viewModuleEntryText = "View Module";
		var viewModuleEntry = new ClickableCodeVisionEntry(viewModuleEntryText, getId(), (mouseEvent, editor)->{
			try{
				WebSocketServer.extractModuleByFind(parsedFind)
							   .thenAccept(json->{
								   TextRange range = parsedFind.getRange();

								   int startOffset = range.getStartOffset();
								   int endOffset = range.getEndOffset();

								   int finalLineStart = editor.getDocument().getLineStartOffset(editor.getDocument().getLineNumber(startOffset) - 1) + (endOffset - startOffset);

								   try{
									   if(json.has("ok") && !json.getBoolean("ok")){
										   ApplicationKt.getApplication().invokeLater(()->{
											   try{
												   HintManager.getInstance().showErrorHint(editor, json.getString("error"), finalLineStart, range.getEndOffset(), HintManager.RIGHT, HintManager.HIDE_BY_TEXT_CHANGE, 5000);
											   }catch(JSONException e){
												   e.printStackTrace();
											   }
										   });
										   Utils.notify("VencordCompanion", json.getString("error"), NotificationType.ERROR);
									   }else if(json.has("ok") && json.getBoolean("ok") && json.has("data")){
										   String contents = json.getString("data");
										   int moduleNum = json.getInt("moduleNumber");

										   ApplicationKt.getApplication().invokeLater(()->{
											   Utils.openNewEditorTab("module" + moduleNum + ".js", contents, file.getProject());
										   });
									   }

								   }catch(JSONException e){
									   e.printStackTrace();
								   }
							   });
			}catch(Exception e){
				e.printStackTrace();
			}
		}, null, viewModuleEntryText, "", Collections.emptyList());
		viewModuleEntry.setShowInMorePopup(false);
		lenses.add(new Pair<>(parsedFind.getRange(), viewModuleEntry));
	}

}
