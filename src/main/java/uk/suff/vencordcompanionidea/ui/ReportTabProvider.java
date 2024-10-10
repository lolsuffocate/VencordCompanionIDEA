package uk.suff.vencordcompanionidea.ui;

import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class ReportTabProvider implements FileEditorProvider, DumbAware{

	@Override
	public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
		// Define the condition to accept the file
		return file.getName().matches("Reporter Results( \\(\\d+\\))?");
	}

	@NotNull
	@Override
	public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
		return new ReportTab(file);
	}

	@NotNull
	@Override
	public String getEditorTypeId() {
		return "custom-editor-tab";
	}

	@NotNull
	@Override
	public FileEditorPolicy getPolicy() {
		return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
	}
}
