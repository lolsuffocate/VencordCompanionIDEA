package uk.suff.vencordcompanionidea.config;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Objects;

/**
 * Provides controller functionality for application settings.
 */
public final class AppSettingsConfigurable implements Configurable{

	private AppSettingsComponent mySettingsComponent;

	// A default constructor with no arguments is required because
	// this implementation is registered as an applicationConfigurable

	@Nls(capitalization = Nls.Capitalization.Title)
	@Override
	public String getDisplayName(){
		return "Vencord Companion";
	}

	@Override
	public JComponent getPreferredFocusedComponent(){
		return mySettingsComponent.getPreferredFocusedComponent();
	}

	@Nullable
	@Override
	public JComponent createComponent(){
		mySettingsComponent = new AppSettingsComponent();
		return mySettingsComponent.getPanel();
	}

	@Override
	public boolean isModified(){
		AppSettings.State state = Objects.requireNonNull(AppSettings.getInstance().getState());
		return !mySettingsComponent.getReturnPatchedModuleByDefault() == state.applyPatchWhenExtractingByDefault ||
				!mySettingsComponent.getCacheModulesOnConnection() == state.cacheModulesOnConnection ||
			   !mySettingsComponent.getDynamicPatches() == state.dynamicPatches ||
			   !mySettingsComponent.getExtractToPath().equals(state.extractToPath);
	}

	@Override
	public void apply(){
		AppSettings.State state = Objects.requireNonNull(AppSettings.getInstance().getState());
		state.applyPatchWhenExtractingByDefault = mySettingsComponent.getReturnPatchedModuleByDefault();
		state.cacheModulesOnConnection = mySettingsComponent.getCacheModulesOnConnection();
		state.dynamicPatches = mySettingsComponent.getDynamicPatches();
		state.extractToPath = mySettingsComponent.getExtractToPath();
	}

	@Override
	public void reset(){
		AppSettings.State state = Objects.requireNonNull(AppSettings.getInstance().getState());
		mySettingsComponent.setReturnPatchedModuleByDefault(state.applyPatchWhenExtractingByDefault);
		mySettingsComponent.setCacheModulesOnConnection(state.cacheModulesOnConnection);
		mySettingsComponent.setDynamicPatches(state.dynamicPatches);
		mySettingsComponent.setExtractToPath(state.extractToPath);
	}

	@Override
	public void disposeUIResources(){
		mySettingsComponent = null;
	}

}
