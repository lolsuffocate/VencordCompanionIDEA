package uk.suff.vencordcompanionidea.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.ShowSettingsUtil;
import uk.suff.vencordcompanionidea.config.AppSettingsConfigurable;

public class OpenSettingsAction extends AnAction{

	@Override
	public void actionPerformed(AnActionEvent e) {
		ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), AppSettingsConfigurable.class);
	}
}
