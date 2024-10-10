package uk.suff.vencordcompanionidea.providers;

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider;
import org.jetbrains.annotations.*;

public class ExtractCodeVisionSettingsProvider implements CodeVisionGroupSettingProvider{

	public static final String GROUP_ID = "VencordCompanionExtractModule";
	public static final String GROUP_NAME = "Vencord: Extract Module";

	@Override
	public @NotNull String getGroupId(){
		return GROUP_ID;
	}

	@Override
	public @Nls @NotNull String getDescription(){
		return "Extract Modules with Vencord Companion";
	}

	@Override
	public @Nls @NotNull String getGroupName(){
		return GROUP_NAME;
	}
}
