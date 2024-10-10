package uk.suff.vencordcompanionidea.providers;

import com.intellij.codeInsight.codeVision.*;
import com.intellij.codeInsight.codeVision.ui.model.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BiConsumer;

public class ClickableCodeVisionEntry extends TextCodeVisionEntry implements CodeVisionPredefinedActionEntry{

	private final BiConsumer<? super MouseEvent, ? super Editor> onClick;

	public ClickableCodeVisionEntry(@Nls String text, String providerId, @NotNull BiConsumer<? super MouseEvent, ? super Editor> onClick, Icon icon, @Nls String longPresentation, @NlsContexts.Tooltip String tooltip, List<CodeVisionEntryExtraActionModel> extraActions){
		super(text, providerId, icon, longPresentation, tooltip, extraActions);
		this.onClick = onClick;
	}


	@Override
	public void onClick(@NotNull Editor editor){
		MouseEvent mouseEvent = this.getUserData(EditorCodeVisionContextKt.getCodeVisionEntryMouseEventKey());
		onClick.accept(mouseEvent, editor);
	}

}
