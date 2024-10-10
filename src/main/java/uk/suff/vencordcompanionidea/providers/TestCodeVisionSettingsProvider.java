package uk.suff.vencordcompanionidea.providers;

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider;
import org.jetbrains.annotations.*;

public class TestCodeVisionSettingsProvider implements CodeVisionGroupSettingProvider{

	public static final String GROUP_ID = "VencordCompanionTestPatch";
	public static final String GROUP_NAME = "Vencord: Test Patch";

	@Override
	public @NotNull String getGroupId(){
		return GROUP_ID;
	}

	@Override
	public @Nls @NotNull String getDescription(){
		return "Test Patches with Vencord Companion";
	}

	@Override
	public @Nls @NotNull String getGroupName(){
		return GROUP_NAME;
	}
}
