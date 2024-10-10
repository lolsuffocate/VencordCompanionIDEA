package uk.suff.vencordcompanionidea.actions;

import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NotNull;
import uk.suff.vencordcompanionidea.Utils;

public class VencordActionGroup extends DefaultActionGroup{

	@Override
	public void update(@NotNull AnActionEvent e){
		e.getPresentation().setEnabledAndVisible(Utils.isVencordProject(e.getProject()));
	}

	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread(){
		return ActionUpdateThread.EDT;
	}
}
