package com.example.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import com.intellij.ide.highlighter.JavaFileType;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

public class ModifyJavaTestASTAction extends AnAction {
    private static final Logger logger = Logger.getLogger(ModifyJavaTestASTAction.class.getName());

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            logger.warning("No project is open.");
            return;
        }

        // 获取当前选中的文件
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null || !"java".equals(file.getExtension())) {
            logger.warning("Selected file is not a Java file.");
            return;
        }

        // 获取 PsiFile
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile == null) {
            logger.warning("Cannot find PsiFile for the selected file.");
            return;
        }

        // 读取原始代码内容
        String originalContent = psiFile.getText();

        // 使用 PSI 修改代码的副本
        String modifiedContent = createModifiedContent(project, originalContent);

        // 保存修改后的内容到新文件
        saveModifiedFile(file, modifiedContent);
    }

    /**
     * 根据原始内容创建修改后的内容
     */
    private String createModifiedContent(Project project, String originalContent) {
        // 创建一个临时的 PsiFile，用于分析和修改代码
        PsiFile tempFile = PsiFileFactory.getInstance(project)
                .createFileFromText("Temp.java", JavaFileType.INSTANCE, originalContent);

        if (!(tempFile instanceof PsiJavaFile)) {
            logger.warning("The file is not a valid Java file.");
            return originalContent;
        }

        PsiJavaFile javaFile = (PsiJavaFile) tempFile;

        // 遍历文件中的类，修改方法体
        for (PsiClass psiClass : javaFile.getClasses()) {
            for (PsiMethod method : psiClass.getMethods()) {
                PsiCodeBlock body = method.getBody();
                if (body != null) {
                    modifyMethodBody(body);
                }
            }
        }

        // 返回修改后的代码内容
        return javaFile.getText();
    }

    /**
     * 修改方法体中的变量初始化
     */
    private void modifyMethodBody(PsiCodeBlock body) {
        // 遍历方法体中的所有变量声明
        PsiDeclarationStatement[] declarations = PsiTreeUtil.getChildrenOfType(body, PsiDeclarationStatement.class);
        if (declarations == null) return;

        for (PsiDeclarationStatement declaration : declarations) {
            for (PsiElement element : declaration.getDeclaredElements()) {
                if (element instanceof PsiLocalVariable) {
                    PsiLocalVariable variable = (PsiLocalVariable) element;

                    // 检查初始化表达式
                    PsiExpression initializer = variable.getInitializer();
                    if (initializer != null) {
                        replaceWithPseudoCode(variable, initializer);
                    }
                }
            }
        }
    }

    /**
     * 替换变量初始化为 <Instantiate ClassName>
     */
    private void replaceWithPseudoCode(PsiLocalVariable variable, PsiExpression initializer) {
        String type = variable.getType().getPresentableText();

        // 使用合法的伪代码替换
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
        PsiExpression newInitializer = factory.createExpressionFromText("\"<Instantiate " + type + ">\"", null);

        initializer.replace(newInitializer); // 仅替换临时文件中的初始化
    }

    /**
     * 保存修改后的内容到新文件
     */
    private void saveModifiedFile(VirtualFile originalFile, String modifiedContent) {
        // 在原文件目录中创建新文件路径
        String newFilePath = originalFile.getParent().getPath() + "/Modified_" + originalFile.getName();
        File newFile = new File(newFilePath);

        // 写入新文件
        try (FileWriter writer = new FileWriter(newFile)) {
            writer.write(modifiedContent);
            logger.info("Modified file saved to: " + newFilePath);

            // 刷新虚拟文件系统以显示新文件
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(newFile);
        } catch (IOException e) {
            logger.severe("Failed to save modified file: " + e.getMessage());
        }
    }
}
