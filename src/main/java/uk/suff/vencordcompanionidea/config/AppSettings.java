package uk.suff.vencordcompanionidea.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import org.jetbrains.annotations.*;

@State(
		name = "org.intellij.sdk.settings.AppSettings",
		storages = @Storage("VencordCompanionsSettings.xml")
)
public final class AppSettings
		implements PersistentStateComponent<AppSettings.State>{

	public static class State{
		@NonNls
		public boolean applyPatchWhenExtractingByDefault = false;
		public boolean cacheModulesOnConnection = false;
		public boolean dynamicPatches = false;
	}

	private State myState = new State();

	public static AppSettings getInstance(){
		return ApplicationManager.getApplication()
								 .getService(AppSettings.class);
	}

	public static boolean applyPatchWhenExtractingByDefault(){
		State state = getInstance().getState();
		return state != null && state.applyPatchWhenExtractingByDefault;
	}

	public static boolean cacheModulesOnConnection(){
		State state = getInstance().getState();
		return state != null && state.cacheModulesOnConnection;
	}

	public static boolean dynamicPatches(){
		State state = getInstance().getState();
		return state != null && state.dynamicPatches;
	}

	@Override
	public State getState(){
		return myState;
	}

	@Override
	public void loadState(@NotNull State state){
		myState = state;
	}

}
