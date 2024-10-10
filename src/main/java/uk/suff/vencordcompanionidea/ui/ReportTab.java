package uk.suff.vencordcompanionidea.ui;

import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.*;
import uk.suff.vencordcompanionidea.Utils;
import uk.suff.vencordcompanionidea.actions.Reporter;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.HashMap;

public class ReportTab implements FileEditor{
	private JComponent component;
	private VirtualFile file;

	public ReportTab(VirtualFile file){
		this.component =  file.getUserData(Reporter.reportPanelKey);
		this.file = file;
	}

	@Override
	public VirtualFile getFile(){
		return file;
	}

	@NotNull
	@Override
	public JComponent getComponent(){
		return component;
	}

	@Nullable
	@Override
	public JComponent getPreferredFocusedComponent(){
		return component;
	}

	@NotNull
	@Override
	public String getName(){
		return "Reporter Results";
	}

	@Override
	public void setState(@NotNull FileEditorState state){
		// Handle state if needed
	}

	@Override
	public boolean isModified(){
		return false;
	}

	@Override
	public boolean isValid(){
		return true;
	}

	@Override
	public void addPropertyChangeListener(@NotNull PropertyChangeListener listener){
		// Add property change listener if needed
	}

	@Override
	public void removePropertyChangeListener(@NotNull PropertyChangeListener listener){
		// Remove property change listener if needed
	}

	@Override
	public void dispose(){
		// Dispose resources if needed
		userData.clear();
		userData = null;
		file = null;
		component = null;
	}

	private HashMap<Key<?>, Object> userData = new HashMap<>();

	@Override
	@SuppressWarnings("unchecked")
	public <T> @Nullable T getUserData(@NotNull Key<T> key){
		Object value = userData.get(key);

		return (T) value;
	}

	@Override
	public <T> void putUserData(@NotNull Key<T> key, @Nullable T value){
		userData.put(key, value);
	}
}
