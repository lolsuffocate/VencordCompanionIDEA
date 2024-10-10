package uk.suff.vencordcompanionidea.providers;

import com.intellij.codeInsight.codeVision.*;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.util.ApplicationKt;
import kotlin.Pair;
import org.jetbrains.annotations.*;
import org.json.*;
import uk.suff.vencordcompanionidea.*;
import uk.suff.vencordcompanionidea.config.AppSettings;

import java.util.*;

public class TestCodeVisionProvider implements DaemonBoundCodeVisionProvider{
	@Override
	public @NotNull String getGroupId(){
		return TestCodeVisionSettingsProvider.GROUP_ID;
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
		return TestCodeVisionSettingsProvider.GROUP_NAME;
	}

	@NotNull
	@Override
	public List<CodeVisionRelativeOrdering> getRelativeOrderings(){
		return List.of(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingLast.INSTANCE);
	}

	@NotNull
	@Override
	public String getId(){
		return TestCodeVisionSettingsProvider.GROUP_ID;
	}


	@NotNull
	@Override
	public List<Pair<TextRange, CodeVisionEntry>> computeForEditor(@NotNull Editor editor, @NotNull PsiFile file){
		ArrayList<Pair<TextRange, CodeVisionEntry>> lenses = new ArrayList<>();

		SyntaxTraverser<PsiElement> traverser = SyntaxTraverser.psiTraverser(file);
		for(PsiElement element : traverser.preOrderDfsTraversal()){
			parsePatches(this, element, lenses);
			parseFinds(this, element, lenses);
		}

		List<Pair<TextRange, CodeVisionEntry>> pairs = DaemonBoundCodeVisionProvider.super.computeForEditor(editor, file);
		lenses.addAll(pairs);

		return lenses;
	}

	private static void parsePatches(DaemonBoundCodeVisionProvider cvp, PsiElement element, ArrayList<Pair<TextRange, CodeVisionEntry>> lenses){
		ArrayList<ParsedPatch> patches = ParsedPatch.fromElement(element);
		if(patches.isEmpty()) return;

		for(ParsedPatch patch : patches){
			if(!patch.isPatchable()) continue;
			String patchEntryText = "Test Patch";
			var patchTestEntry = new ClickableCodeVisionEntry(patchEntryText, cvp.getId(), (mouseEvent, editor)->{
				try{
					if(Utils.warnWebSocketNotRunning() || Utils.warnCompanionNotConnected()){
						return;
					}

					boolean applyPatch = AppSettings.applyPatchWhenExtractingByDefault() != mouseEvent.isControlDown();
					WebSocketServer.testModuleByPatch(patch, applyPatch)
								   .thenAccept(json->{
									   try{
										   // display a tooltip with the response
										   TextRange range = patch.getRange();

										   int startOffset = range.getStartOffset();
										   int endOffset = range.getEndOffset();

										   int finalLineStart = editor.getDocument().getLineStartOffset(editor.getDocument().getLineNumber(startOffset) - 1) + (endOffset - startOffset);

										   if(json.has("ok")){
											   if(!json.getBoolean("ok")){
												   ApplicationKt.getApplication().invokeLater(()->{
													   try{
														   HintManager.getInstance().showErrorHint(editor, json.getString("error"), finalLineStart, range.getEndOffset(), HintManager.RIGHT, HintManager.HIDE_BY_TEXT_CHANGE, 5000);
													   }catch(JSONException e){
														   e.printStackTrace();
													   }
												   });
												   Utils.notify("VencordCompanion", json.getString("error"), NotificationType.ERROR);
											   }else{
												   ApplicationKt.getApplication().invokeLater(()->HintManager.getInstance().showSuccessHint(editor, "Test successful", HintManager.RIGHT));
												   Utils.notify("VencordCompanion", "Test successful", NotificationType.INFORMATION);
											   }
										   }
									   }catch(JSONException e){
										   e.printStackTrace();
									   }
								   });
				}catch(JSONException e){
					e.printStackTrace();
				}
			}, null, patchEntryText, "", Collections.emptyList());
			patchTestEntry.setShowInMorePopup(false);
			lenses.add(new Pair<>(patch.getRange(), patchTestEntry));
		}
	}

	private static void parseFinds(DaemonBoundCodeVisionProvider cvp, PsiElement element, ArrayList<Pair<TextRange, CodeVisionEntry>> lenses){
		ParsedFind parsedFind = ParsedFind.fromElement(element);
		if(parsedFind == null) return;

		var entry = new ClickableCodeVisionEntry("Test Find", cvp.getId(), (mouseEvent, editor)->{
			try{
				WebSocketServer.testModuleByFind(parsedFind)
							   .thenAccept(json->{
								   try{
									   // display a tooltip with the response
									   TextRange range = parsedFind.getRange();

									   int startOffset = range.getStartOffset();
									   int endOffset = range.getEndOffset();

									   int finalLineStart = editor.getDocument().getLineStartOffset(editor.getDocument().getLineNumber(startOffset) - 1) + (endOffset - startOffset);

									   if(json.has("ok")){
										   if(!json.getBoolean("ok")){
											   ApplicationKt.getApplication().invokeLater(()->{
												   try{
													   HintManager.getInstance().showErrorHint(editor, json.getString("error"), finalLineStart, range.getEndOffset(), HintManager.RIGHT, HintManager.HIDE_BY_TEXT_CHANGE, 5000);
												   }catch(JSONException e){
													   e.printStackTrace();
												   }
											   });
											   Utils.notify("VencordCompanion", json.getString("error"), NotificationType.ERROR);
										   }else{
											   ApplicationKt.getApplication().invokeLater(()->HintManager.getInstance().showSuccessHint(editor, "Test successful", HintManager.RIGHT));
											   Utils.notify("VencordCompanion", "Test successful", NotificationType.INFORMATION);
										   }
									   }
								   }catch(JSONException e){
									   e.printStackTrace();
								   }
							   });
			}catch(JSONException e){
				e.printStackTrace();
			}
		}, null, "Test Find", "", Collections.emptyList());
		entry.setShowInMorePopup(false);
		lenses.add(new Pair<>(parsedFind.getRange(), entry));
	}
}
