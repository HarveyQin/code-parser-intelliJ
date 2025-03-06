package com.example.plugin;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ModifyAllTestFilesAction extends AnAction {
    private static final String projectName = "commons-dbcp";
    private static final String modifiedResultsPath = "E:\\Files\\Mock_Project\\ML\\parserResult\\"+projectName+"\\parsed_tests_stage1";
    private static final String xlsxOutputPath = "E:\\Files\\Mock_Project\\ML\\parserResult\\"+projectName+"\\object_instantiations.xlsx";
    private static final String testFilesCsvPath = "E:\\Files\\Mock_Project\\ML\\parserResult\\"+projectName+"\\test_files_list.csv";
    private static final Logger logger = Logger.getLogger(ModifyAllTestFilesAction.class.getName());

    // 定义需要过滤的基本类型集合
    private static final Set<String> BASIC_TYPES = Set.of(
            "int", "long", "short", "float", "double", "boolean", "char", "byte",
            "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double",
            "java.lang.Boolean", "java.lang.Character", "java.lang.Byte", "java.lang.Short",
            "java.lang.String", "java.lang.Object", "java.lang.Class", "java.lang.reflect.Field",
            "java.lang.Class<?>", "java.io.IOException", "java.lang.StringBuffer", "java.util.Map<java.lang.String,java.lang.String>",
            "java.util.List<java.util.Map<java.lang.String,java.lang.String>>", "java.lang.reflect.Type[]", "java.util.List", "java.lang.Object[]",
            "java.util.Map", "T[]", "java.lang.String[]", "java.io.File", "java.io.ByteArrayInputStream",
            "java.io.ByteArrayOutputStream", "java.io.StringReader", "java.io.StringWriter", "java.util.List<java.lang.Integer>",
            "java.util.Set<java.lang.String>", "java.util.Properties", "java.lang.Throwable", "java.lang.ClassLoader",
            "java.lang.Thread", "java.lang.reflect.Method", "java.lang.reflect.Constructor", "java.lang.ref.WeakReference",
            "byte[]", "char[]", "java.io.InputStream", "java.util.Random", "java.lang.Runnable",
            "java.util.HashMap", "java.lang.Runnable[]", "boolean[]", "java.util.LinkedList<java.lang.String>",
            "java.util.Date", "java.util.UUID", "java.io.File[]", "java.io.PrintWriter", "java.util.List<java.lang.String>",
            "java.lang.StringBuilder", "java.util.List<java.lang.Long>", "java.util.Set<java.lang.Long>", "java.util.Set<java.lang.Integer>",
            "java.util.Calendar", "java.util.ArrayList<java.lang.String>", "java.util.Map<java.lang.Long,java.util.List<java.lang.String>>",
            "java.util.List<java.lang.Object[]>", "java.lang.Object[][]", "java.math.BigDecimal[]", "java.math.BigDecimal", "long[]", "int[]",
            "java.io.DataInputStream", "java.io.DataOutputStream", "java.util.Map<java.lang.String,java.lang.Object[]>", "short[]",
            "java.lang.String[][]", "long[][]", "short[][]", "boolean[][]", "int[][]", "java.lang.Long[][]", "java.io.Writer", "java.io.OutputStream",
            "Meta", "java.text.DateFormat", "java.text.SimpleDateFormat", "java.util.ArrayList", "java.io.BufferedInputStream",
            "java.io.BufferedReader", "java.lang.reflect.Constructor<?>[]", "java.nio.file.Path", "byte[][]", "java.util.Map<byte[],byte[]>",
            "java.util.Set<T>"
    );

    private final List<String[]> xlsxRecords = new ArrayList<>();

    static {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            logger.warning("No project is open.");
            return;
        }

        // 清空或重新创建 CSV 文件
        prepareCSVFile();

        // 添加 XLSX 文件的表头
        xlsxRecords.add(new String[]{"Test Suite", "Test Case", "Class Name", "Mocked"});

        // 查找项目中的所有 Java 文件
        Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project));
        logger.info("Found " + javaFiles.size() + " Java files in the project.");

        for (VirtualFile file : javaFiles) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile instanceof PsiJavaFile && containsTestAnnotation((PsiJavaFile) psiFile)) {
                // 记录测试文件信息
                recordTestFile(project, file.getName(), file.getPath());
                // 修改并保存文件
                modifyAndSaveFile(project, file, psiFile);
            }
        }

        // 保存 XLSX 文件
        saveXlsxRecords();
    }

    private boolean containsTestAnnotation(PsiJavaFile javaFile) {
        for (PsiClass psiClass : javaFile.getClasses()) {
            for (PsiMethod method : psiClass.getMethods()) {
                for (PsiAnnotation annotation : method.getAnnotations()) {
                    if ("org.junit.Test".equals(annotation.getQualifiedName()) ||
                            "org.junit.jupiter.api.Test".equals(annotation.getQualifiedName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void prepareCSVFile() {
        File csvFile = new File(testFilesCsvPath);

        // 确保目标文件所在的目录存在
        File parentDir = csvFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                logger.severe("Failed to create directory: " + parentDir.getAbsolutePath());
                return;
            }
        }

        if (csvFile.exists() && !csvFile.delete()) {
            logger.warning("Failed to delete existing CSV file: " + testFilesCsvPath);
        }

        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("\"Test File Name\",\"Test File Path\",\"CUT\",\"CUT Path\"\n");
        } catch (IOException e) {
            logger.severe("Failed to create CSV file or write header: " + e.getMessage());
        }
    }

    private void recordTestFile(Project project, String testFileName, String testFilePath) {
        String testClassName = extractTestClassName(testFileName);
        String cutClassName = getCUTClassName(testClassName);

        Optional<PsiFile> cutFileOpt = findCUTFile(project, cutClassName);
        String cutPath = cutFileOpt.map(file -> file.getVirtualFile().getPath()).orElse("Not Found");
        String cutName = cutFileOpt.map(file -> cutClassName).orElse("Not Found");

        try (PrintWriter writer = new PrintWriter(new FileWriter(testFilesCsvPath, true))) {
            writer.printf("\"%s\",\"%s\",\"%s\",\"%s\"%n", testFileName, testFilePath, cutName, cutPath);
        } catch (IOException e) {
            logger.severe("Failed to write to CSV: " + e.getMessage());
        }
    }

    private String extractTestClassName(String fileName) {
        if (fileName.endsWith("Test.java")) {
            return fileName.substring(0, fileName.length() - "Test.java".length());
        }
        return fileName.replace(".java", "");
    }

    private String getCUTClassName(String testClassName) {
        return testClassName;
    }

    private Optional<PsiFile> findCUTFile(Project project, String cutClassName) {
        Collection<VirtualFile> matchingFiles = FilenameIndex.getVirtualFilesByName(
                project, cutClassName + ".java", GlobalSearchScope.projectScope(project)
        );

        if (matchingFiles.isEmpty()) {
            return Optional.empty();
        }

        VirtualFile cutFile = matchingFiles.iterator().next();
        return Optional.ofNullable(PsiManager.getInstance(project).findFile(cutFile));
    }

    private void modifyAndSaveFile(Project project, VirtualFile originalFile, PsiFile psiFile) {
        String originalContent = psiFile.getText();
        String modifiedContent = createModifiedContent(project, originalContent, originalFile.getName());
        saveModifiedFile(originalFile, modifiedContent);
    }

    private String createModifiedContent(Project project, String originalContent, String fileName) {
        PsiFile tempFile = PsiFileFactory.getInstance(project)
                .createFileFromText("Temp.java", JavaFileType.INSTANCE, originalContent);

        if (!(tempFile instanceof PsiJavaFile)) {
            logger.warning("The file is not a valid Java file.");
            return originalContent;
        }

        PsiJavaFile javaFile = (PsiJavaFile) tempFile;

        for (PsiClass psiClass : javaFile.getClasses()) {
            modifyMockFields(psiClass, fileName);

            for (PsiMethod method : psiClass.getMethods()) {
                // 确保 @BeforeAll、@BeforeEach、@Test 方法都被遍历
                PsiCodeBlock body = method.getBody();
                if (body != null) {
                    modifyMethodBody(body, fileName, method.getName());
                }
            }
        }
        return javaFile.getText();
    }


    private void modifyMethodBody(PsiCodeBlock body, String fileName, String methodName) {
        for (PsiElement child : body.getChildren()) {
            findAndRemoveMockitoCalls(child);
            processPsiElement(child, fileName, methodName);
        }
    }


    /**
     * 判断是否是 `mock(ClassName.class)` 形式的调用
     */
    private boolean isMockedMethodCall(PsiMethodCallExpression methodCall) {
        PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
        String methodName = methodExpression.getReferenceName();
        PsiElement qualifier = methodExpression.getQualifier();

        // 日志输出，查看哪些方法被检测到
        logger.info("Checking method call: " + methodCall.getText());

        // 允许 mock(Class.class) 或 Mockito.mock(Class.class)
        boolean isMocked = "mock".equals(methodName) && (qualifier == null || "Mockito".equals(qualifier.getText()));
        if (isMocked) {
            logger.info("Detected mock() call: " + methodCall.getText());
        }
        return isMocked;
    }


    /**
     * 递归查找并移除 Mockito 调用
     */
    private void findAndRemoveMockitoCalls(PsiElement element) {
        if (element instanceof PsiMethodCallExpression methodCall) {
            // 获取方法名称
            String methodName = methodCall.getMethodExpression().getReferenceName();
            PsiElement qualifier = methodCall.getMethodExpression().getQualifier();
            String fullMethodName = qualifier != null ? qualifier.getText() + "." + methodName : methodName;

            // 判断是否为 Mockito 的调用
            if (isMockitoStubbing(fullMethodName) || isMockitoAsserting(fullMethodName)) {
                WriteCommandAction.runWriteCommandAction(element.getProject(), () -> {
                    // 删除包含方法调用的完整语句
                    PsiElement parent = methodCall.getParent();
                    if (parent instanceof PsiExpressionStatement) {
                        parent.delete();
                    }
                });
                // logger.info("Removed Mockito stubbing: " + fullMethodName);
            }
        } else {
            // 递归检查子节点
            for (PsiElement child : element.getChildren()) {
                findAndRemoveMockitoCalls(child);
            }
        }
    }

    /**
     * 判断是否为 Mockito 的 stubbing 方法
     */
    private boolean isMockitoStubbing(String fullMethodName) {
        return fullMethodName.startsWith("Mockito.when") || fullMethodName.startsWith("when");
    }

    private boolean isMockitoAsserting(String fullMethodName) {
        return fullMethodName.startsWith("Mockito.verify") || fullMethodName.startsWith("verify");
    }


    private void processPsiElement(PsiElement element, String fileName, String methodName) {
        if (element instanceof PsiDeclarationStatement declaration) {
            for (PsiElement declaredElement : declaration.getDeclaredElements()) {
                if (declaredElement instanceof PsiLocalVariable variable) {
                    logger.info("Processing local variable: " + variable.getName() + " in " + fileName);
                    processLocalVariable(variable, fileName, methodName);
                }
            }
        } else if (element instanceof PsiAssignmentExpression assignment) {
            PsiExpression initializer = assignment.getRExpression();
            if (initializer != null) {
                logger.info("Processing assignment in " + fileName);
                handleInstantiationExpression(initializer, fileName, methodName);
            }
        }

        for (PsiElement child : element.getChildren()) {
            processPsiElement(child, fileName, methodName);
        }
    }


    /**
     * 处理对象实例化表达式，包括 mock(Class.class) 形式
     */
    private void handleInstantiationExpression(PsiExpression expression, String fileName, String methodName) {
        if (expression instanceof PsiNewExpression) {
            // 处理 `new ClassName(...)`
            PsiNewExpression newExpression = (PsiNewExpression) expression;
            PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
            if (classReference != null) {
                String className = classReference.getQualifiedName();
                recordAndReplaceExpression(expression, fileName, methodName, className, false);
            }
        } else if (expression instanceof PsiMethodCallExpression) {
            // 处理 `mock(ClassName.class)`
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression) expression;
            if (isMockedMethodCall(methodCall)) {
                PsiExpressionList argumentList = methodCall.getArgumentList();
                PsiExpression[] arguments = argumentList.getExpressions();
                if (arguments.length == 1 && arguments[0] instanceof PsiClassObjectAccessExpression) {
                    PsiClassObjectAccessExpression classObject = (PsiClassObjectAccessExpression) arguments[0];
                    PsiType type = classObject.getOperand().getType();
                    String mockedType = type.getCanonicalText(); // 获取类型全限定名

                    // 记录和替换表达式
                    recordAndReplaceExpression(expression, fileName, methodName, mockedType, true);
                }
            }
        }
    }


    /**
     * 记录对象实例化信息并替换为伪代码
     */
    private void recordAndReplaceExpression(PsiExpression expression, String fileName, String methodName, String className, boolean isMocked) {
        // 跳过基本类型
        if (isBasicType(PsiType.getTypeByName(className, expression.getProject(), GlobalSearchScope.allScope(expression.getProject())))) {
            return;
        }

        // **记录实例化并确认 Mocked 标志**
        xlsxRecords.add(new String[]{fileName, methodName, className, String.valueOf(isMocked)});
        logger.info("Recorded instantiation: " + className + " in " + fileName + ", Mocked: " + isMocked);

        // 替换为伪代码
        String pseudoCode = "\"<Instantiate " + className + ">\"";
        try {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
            PsiExpression newExpression = factory.createExpressionFromText(pseudoCode, null);

            WriteCommandAction.runWriteCommandAction(expression.getProject(), (Computable<PsiElement>) () -> expression.replace(newExpression));
        } catch (Exception e) {
            logger.severe("Failed to replace expression for class: " + className + ". Error: " + e.getMessage());
        }
    }


    private void processLocalVariable(PsiLocalVariable variable, String fileName, String methodName) {
        if (variable == null || !variable.isValid()) return;

        PsiExpression initializer = variable.getInitializer();
        if (initializer != null && initializer.isValid()) {
            PsiType type = variable.getType();
            if (type == null || !type.isValid()) return;

            // 检查是否为基本类型
            if (isBasicType(type)) {
                // logger.info("Skipped basic type: " + type.getCanonicalText());
                return;
            }

            String className = type.getCanonicalText();
            boolean isMocked = initializer.getText().startsWith("Mockito.mock(") || initializer.getText().startsWith("mock(");

            // 记录到 XLSX
            xlsxRecords.add(new String[]{fileName, methodName, className, String.valueOf(isMocked)});

            // 替换为伪代码
            replaceWithPseudoCode(variable, initializer);
        }
    }

    /**
     * 查找类中的 @Mock 变量，并替换为伪代码
     */
    private void modifyMockFields(PsiClass psiClass, String fileName) {
        for (PsiField field : psiClass.getFields()) {
            if (hasMockAnnotation(field)) {
                replaceMockFieldWithPseudoCode(field, fileName);
            }
        }
    }


    /**
     * 检查 PsiField 是否被 @Mock 注解
     */
    private boolean hasMockAnnotation(PsiField field) {
        for (PsiAnnotation annotation : field.getAnnotations()) {
            if ("org.mockito.Mock".equals(annotation.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 替换 @Mock 变量声明为伪代码初始化，并记录到 instances_list
     */
    private void replaceMockFieldWithPseudoCode(PsiField field, String fileName) {
        PsiType type = field.getType();
        if (isBasicType(type)) {
            return; // 忽略基本类型
        }

        String className = type.getCanonicalText();
        String pseudoCode = "\"<Instantiate " + className + ">\"";

        try {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(field.getProject());
            PsiExpression newInitializer = factory.createExpressionFromText(pseudoCode, null);

            WriteCommandAction.runWriteCommandAction(field.getProject(), (Computable<PsiElement>) () -> {
                PsiElement replaced = field.replace(factory.createFieldFromText(className + " " + field.getName() + " = " + pseudoCode + ";", field));
                return replaced;
            });

            // logger.info("Replaced @Mock field: " + field.getName() + " -> " + pseudoCode);

            // 记录到 instances_list (xlsxRecords)
            String testMethodName = "CLASS_LEVEL";  // 由于 @Mock 变量是类级别的，没有特定的方法
            xlsxRecords.add(new String[]{fileName, testMethodName, className, "true"});
            // logger.info("Recorded @Mock instantiation: " + className + " in " + fileName);

        } catch (Exception e) {
            logger.severe("Failed to replace @Mock field: " + field.getName() + ". Error: " + e.getMessage());
        }
    }


    private boolean isBasicType(PsiType type) {
        if (type == null) return false;

        // 检查是否为原始类型（如 int, long 等）
        if (type instanceof PsiPrimitiveType) return true;

        // 检查全限定类名是否在 BASIC_TYPES 中
        String qualifiedName = type.getCanonicalText();
        return BASIC_TYPES.contains(qualifiedName);
    }

    private void replaceWithPseudoCode(PsiLocalVariable variable, PsiExpression initializer) {
        String type = variable.getType().getPresentableText();
        String sanitizedType = type.replaceAll("[^a-zA-Z0-9_.]", "").trim();
        String pseudoCode = "\"<Instantiate " + sanitizedType + ">\"";

        try {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
            PsiExpression newInitializer = factory.createExpressionFromText(pseudoCode, null);

            WriteCommandAction.runWriteCommandAction(variable.getProject(), (Computable<PsiElement>) () -> initializer.replace(newInitializer));
        } catch (Exception e) {
            logger.severe("Failed to replace initializer for variable: " + variable.getName());
        }
    }

    private void saveModifiedFile(VirtualFile originalFile, String modifiedContent) {
        String newFilePath = modifiedResultsPath + "\\" + originalFile.getName();
        File newFile = new File(newFilePath);

        // 确保目标文件所在的目录存在
        File parentDir = newFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                logger.severe("Failed to create directory: " + parentDir.getAbsolutePath());
                return;
            }
        }

        try (FileWriter writer = new FileWriter(newFile)) {
            writer.write(modifiedContent);
        } catch (IOException e) {
            logger.severe("Failed to save modified file: " + e.getMessage());
        }
    }

    private void saveXlsxRecords() {
        if (xlsxRecords.isEmpty()) {
            logger.warning("No XLSX records to save.");
            return;
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Object Instantiations");

            for (int i = 0; i < xlsxRecords.size(); i++) {
                Row row = sheet.createRow(i);
                String[] record = xlsxRecords.get(i);
                for (int j = 0; j < record.length; j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(record[j]);
                }
            }

            try (FileOutputStream outputStream = new FileOutputStream(xlsxOutputPath)) {
                workbook.write(outputStream);
                logger.info("XLSX records saved to: " + xlsxOutputPath);
            }
        } catch (IOException e) {
            logger.severe("Failed to save XLSX records: " + e.getMessage());
        }
    }
}
