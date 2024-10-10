package uk.suff.vencordcompanionidea.providers;

import com.intellij.find.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.*;
import uk.suff.vencordcompanionidea.*;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CustomFindInProject implements FindInProjectSearchEngine{

	@Override
	public @Nullable FindInProjectSearcher createSearcher(@NotNull FindModel findModel, @NotNull Project project){
		if(!Utils.isVencordProject(project)){
			return null;
		}
		return new FindInProjectSearcher(){

			@Override
			public @NotNull Collection<VirtualFile> searchForOccurrences(){ // I recommend Companion Settings -> Cache Files then Format files for easier searching, also set file filter on search to module*.js
				return WebSocketServer.literallyEveryWebpackModule
						.values()
						.stream()
						.filter(psiFile -> {
							String content = psiFile.getText();

							if(content == null){
								return false;
							}

							if(findModel.isRegularExpressions()){
								String toFind = findModel.getStringToFind();
								// toFind.replace("\\i", "[A-Za-z_$][\\w$]*"); // unfortunately intellij flags \i as invalid regex in the finder before it can get here
								if(findModel.isCaseSensitive()){
									Pattern pattern = Pattern.compile(toFind, Pattern.DOTALL);
									return pattern.matcher(content).find();
								}else{
									Pattern pattern = Pattern.compile(toFind, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
									return pattern.matcher(content).find();
								}
							}else if(findModel.isCaseSensitive()){
								return content.contains(findModel.getStringToFind());
							}else{
								return content.toLowerCase().contains(findModel.getStringToFind().toLowerCase());
							}
						})
						.map(PsiFile::getVirtualFile)
						.collect(Collectors.toList());
			}

			@Override
			public boolean isReliable(){
				return true;
			}

			@Override
			public boolean isCovered(@NotNull VirtualFile file){
				return false;
			}
		};
	}
}
