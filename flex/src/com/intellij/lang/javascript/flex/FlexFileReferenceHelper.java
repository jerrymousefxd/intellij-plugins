package com.intellij.lang.javascript.flex;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.javascript.flex.FlexAnnotationNames;
import com.intellij.lang.ASTNode;
import com.intellij.lang.javascript.psi.ecmal4.JSAttribute;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeNameValuePair;
import com.intellij.lang.javascript.psi.impl.JSChangeUtil;
import com.intellij.lang.javascript.validation.fixes.FixAndIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FlexFileReferenceHelper extends FileReferenceHelper {
  @NotNull
  public Collection<PsiFileSystemItem> getContexts(final Project project, @NotNull final VirtualFile file) {
    return Collections.emptyList();
  }

  public boolean isMine(final Project project, @NotNull final VirtualFile file) {
    return false;
  }

  @NotNull
  public List<? extends LocalQuickFix> registerFixes(final FileReference reference) {
    final PsiElement element = reference.getElement();
    if (!(reference instanceof JSFlexFileReference) || !(element instanceof JSAttributeNameValuePair)) return Collections.emptyList();

    final PsiElement parent = element.getParent();
    if (!(parent instanceof JSAttribute) || !FlexAnnotationNames.EMBED.equals(((JSAttribute)parent).getName())) {
      return Collections.emptyList();
    }

    final String value = ((JSAttributeNameValuePair)element).getSimpleValue();
    if (value.startsWith("/")) return Collections.emptyList();

    final Module module = ModuleUtil.findModuleForPsiElement(element);
    if (module == null) return Collections.emptyList();

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
    final boolean testSourceRoot = virtualFile != null && rootManager.getFileIndex().isInTestSourceContent(virtualFile);

    for (VirtualFile sourceRoot : rootManager.getSourceRoots(testSourceRoot)) {
      if (sourceRoot.findFileByRelativePath(value) != null) {
        return Collections.singletonList(new AddLeadingSlashFix((JSAttributeNameValuePair)element));
      }
    }

    return Collections.emptyList();
  }

  private static class AddLeadingSlashFix extends FixAndIntentionAction {
    private AddLeadingSlashFix(final JSAttributeNameValuePair element) {
      registerElementRefForFix(element, null);
    }

    @NotNull
    public String getName() {
      return "Add leading slash";
    }

    public boolean startInWriteAction() {
      return true;
    }

    protected void applyFix(final Project project, final PsiElement psiElement, final PsiFile file, final Editor editor) {
      final ASTNode oldValueNode = ((JSAttributeNameValuePair)psiElement).getValueNode();
      final String oldText = oldValueNode.getText();
      char quoteChar = oldText.length() > 0 ? oldText.charAt(0) : '"';
      if (quoteChar != '\'' && quoteChar != '"') {
        quoteChar = '"';
      }

      final String newText = quoteChar + "/" + StringUtil.stripQuotesAroundValue(oldText) + quoteChar;
      final ASTNode newNode = JSChangeUtil.createExpressionFromText(project, newText);
      psiElement.getNode().replaceChild(oldValueNode, newNode.getFirstChildNode());
    }
  }
}
