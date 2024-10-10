package uk.suff.vencordcompanionidea.providers;

import com.intellij.lang.javascript.psi.*;
import com.intellij.patterns.*;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class ExtractedModuleReferenceContributor extends PsiReferenceContributor {
	@Override
	public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar){
		registrar.registerReferenceProvider(PlatformPatterns.psiElement(), new ExtractedModuleReferenceProvider());
	}
}
