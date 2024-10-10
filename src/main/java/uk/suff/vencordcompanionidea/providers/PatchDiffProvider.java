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

// we have to have separate providers for the "View Module" and "Test Find/Patch" entries because one provider can't put two lenses in the same position
public class PatchDiffProvider implements DaemonBoundCodeVisionProvider{

	@Override
	public @NotNull String getGroupId(){
		return DiffCodeVisionSettingsProvider.GROUP_ID;
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
		return DiffCodeVisionSettingsProvider.GROUP_NAME;
	}

	@NotNull
	@Override
	public List<CodeVisionRelativeOrdering> getRelativeOrderings(){
		return List.of(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingLast.INSTANCE);
	}

	@NotNull
	@Override
	public String getId(){
		return DiffCodeVisionSettingsProvider.GROUP_ID;
	}

	@NotNull
	@Override
	public List<Pair<TextRange, CodeVisionEntry>> computeForEditor(@NotNull Editor editor, @NotNull PsiFile file){
		ArrayList<Pair<TextRange, CodeVisionEntry>> lenses = new ArrayList<>();

		SyntaxTraverser<PsiElement> traverser = SyntaxTraverser.psiTraverser(file);
		for(PsiElement element : traverser.preOrderDfsTraversal()){
			parsePatches(element, lenses);
		}

		List<Pair<TextRange, CodeVisionEntry>> pairs = DaemonBoundCodeVisionProvider.super.computeForEditor(editor, file);
		lenses.addAll(pairs);

		return lenses;
	}

	private void parsePatches(PsiElement element, ArrayList<Pair<TextRange, CodeVisionEntry>> lenses){
		ArrayList<ParsedPatch> patches = ParsedPatch.fromElement(element);
		if(patches.isEmpty()) return;

		for(ParsedPatch patch : patches){
			if(!patch.isPatchable()) continue;
			String patchEntryText = "View Diff";
			var viewModuleEntry = new ClickableCodeVisionEntry(patchEntryText, getId(), (mouseEvent, editor)->{
				try{
					if(Utils.warnWebSocketNotRunning() || Utils.warnCompanionNotConnected()){
						return;
					}

					boolean applyPatch = AppSettings.applyPatchWhenExtractingByDefault() != mouseEvent.isControlDown();
					WebSocketServer.diffModuleByPatch(patch, applyPatch)
								   .thenAccept(json->{
									   // display a tooltip with the response
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
											   JSONObject resData = json.getJSONObject("data");
											   String originalContents = resData.getString("source").replaceAll("\\(0,([^)]{1,7})\\)\\(", " $1(");
											   String patchedContents = resData.getString("patched").replaceAll("\\(0,([^)]{1,7})\\)\\(", " $1(");
											   int moduleNum = json.getInt("moduleNumber");

											   ApplicationKt.getApplication().invokeLater(()->{
												   // open a new tab in the editor with the diff
												   Utils.openNewDiffTab("DIFF: module-" + moduleNum + ".js", originalContents, patchedContents, editor.getProject());
											   });
										   }
									   }catch(JSONException e){
										   e.printStackTrace();
									   }
								   });
				}catch(Exception e){
					e.printStackTrace();
				};
			}, null, patchEntryText, "", Collections.emptyList());
			viewModuleEntry.setShowInMorePopup(true);
			lenses.add(new Pair<>(patch.getRange(), viewModuleEntry));
		}
	}


}
