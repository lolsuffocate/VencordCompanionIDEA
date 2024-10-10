package uk.suff.vencordcompanionidea.providers;

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider;
import org.jetbrains.annotations.*;

public class DiffCodeVisionSettingsProvider implements CodeVisionGroupSettingProvider{

	public static final String GROUP_ID = "VencordCompanionDiffModule";
	public static final String GROUP_NAME = "Vencord: Diff Module";

	@Override
	public @NotNull String getGroupId(){
		return GROUP_ID;
	}

	@Override
	public @Nls @NotNull String getDescription(){
		return "Diff Modules with Vencord Companion";
	}

	@Override
	public @Nls @NotNull String getGroupName(){
		return GROUP_NAME;
	}
}
