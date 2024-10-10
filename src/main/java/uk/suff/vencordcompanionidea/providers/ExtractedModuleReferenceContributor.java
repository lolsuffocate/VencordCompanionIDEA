package uk.suff.vencordcompanionidea.providers;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class ExtractedModuleReferenceContributor extends PsiReferenceContributor {
	@Override
	public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar){
		registrar.registerReferenceProvider(PlatformPatterns.psiElement(), new ExtractedModuleReferenceProvider());
	}
}
