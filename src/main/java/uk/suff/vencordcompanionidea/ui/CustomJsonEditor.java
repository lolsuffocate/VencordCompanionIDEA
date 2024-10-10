package uk.suff.vencordcompanionidea.ui;


import com.intellij.json.JsonLanguage;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LanguageTextField;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class CustomJsonEditor extends LanguageTextField{

	public CustomJsonEditor(Project project, JSONObject jsonObject){
		this(project, jsonObject.toString(4));
	}

	public CustomJsonEditor(Project project, String text){
		super(JsonLanguage.INSTANCE, project, text);
	}

	@Override
	protected @NotNull EditorEx createEditor(){
		EditorEx editor = super.createEditor();
		editor.setCaretEnabled(true);
		editor.setOneLineMode(false);
		editor.setViewer(true);

		EditorSettings settings = editor.getSettings();
		settings.setLineNumbersShown(true);
		settings.setGutterIconsShown(true);
		settings.setFoldingOutlineShown(true);
		settings.setAutoCodeFoldingEnabled(true);
		settings.setLineMarkerAreaShown(true);
		settings.setIndentGuidesShown(true);
		settings.setVerticalScrollJump(10);
		settings.setRefrainFromScrolling(false);

		return editor;
	}
}
