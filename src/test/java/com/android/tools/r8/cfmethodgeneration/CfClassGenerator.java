// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.CfCodePrinter;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.JarClassFileReader;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class CfClassGenerator extends CodeGenerationBase {

  private final CfCodeGeneratorImportCollection imports = new CfCodeGeneratorImportCollection();

  public abstract Class<?> getImplementation();

  public String generateClass() throws IOException {
    return formatRawOutput(generateRawOutput());
  }

  private String generateRawOutput() throws IOException {
    String classDeclaration = generateClassDeclaration();
    return StringUtils.lines(getHeaderString(), imports.generateImports(), classDeclaration);
  }

  private String generateClassDeclaration() throws IOException {
    JavaStringBuilder builder = new JavaStringBuilder();
    builder.append("public final class " + getGeneratedClassName() + " ").appendOpeningBrace();
    DexProgramClass clazz = readImplementationClass();
    generateCreateClassMethod(builder, clazz);
    generateCreateFieldsMethod(builder, "createInstanceFields", clazz.instanceFields());
    generateCreateFieldsMethod(builder, "createStaticFields", clazz.staticFields());
    CfCodePrinter codePrinter = new CfCodePrinter();
    Map<MethodReference, String> createCfCodeMethodNames =
        generateCreateCfCodeMethods(clazz, codePrinter);
    generateCreateMethodsMethod(
        builder, "createDirectMethods", clazz.directMethods(), createCfCodeMethodNames);
    generateCreateMethodsMethod(
        builder, "createVirtualMethods", clazz.virtualMethods(), createCfCodeMethodNames);
    codePrinter.getMethods().forEach(builder::appendLine);
    builder.appendClosingBrace();
    return builder.toString();
  }

  private DexProgramClass readImplementationClass() throws IOException {
    InternalOptions options = new InternalOptions(factory, new Reporter());
    options.testing.readInputStackMaps = true;
    Box<DexProgramClass> result = new Box<>();
    JarClassFileReader<DexProgramClass> reader =
        new JarClassFileReader<>(new JarApplicationReader(options), result::set, ClassKind.PROGRAM);
    reader.read(Origin.unknown(), ToolHelper.getClassAsBytes(getImplementation()));
    return result.get();
  }

  private void generateCreateClassMethod(JavaStringBuilder builder, DexProgramClass clazz) {
    builder
        .startLine()
        .append("public static ")
        .append(imports.getDexProgramClass())
        .append(" createClass(")
        .append(imports.getDexItemFactory())
        .append(" dexItemFactory) ")
        .appendOpeningBrace();

    builder
        .startLine()
        .append("return new ")
        .append(imports.getDexProgramClass())
        .appendOpeningMultiLineParenthesis();

    builder
        .startLine()
        .append("dexItemFactory.createType(\"")
        .append(clazz.getType().toDescriptorString())
        .appendLine("\"),");

    builder.startLine().append(imports.getProgramResourceKind()).appendLine(".CF,");

    builder.startLine().append(imports.getOrigin()).appendLine(".unknown(),");

    builder
        .startLine()
        .append(imports.getClassAccessFlags())
        .append(".fromCfAccessFlags(")
        .append(clazz.getAccessFlags().getAsCfAccessFlags())
        .appendLine("),");

    builder
        .startLine()
        .append("dexItemFactory.createType(\"")
        .append(clazz.getSuperType().toDescriptorString())
        .appendLine("\"),");

    builder.startLine().append(imports.getDexTypeList()).appendLine(".empty(),");

    builder
        .startLine()
        .append("dexItemFactory.createString(\"")
        .append(clazz.getSourceFile().toString())
        .appendLine("\"),");

    builder.startLine().append(imports.getNestHostClassAttribute()).appendLine(".none(),");

    for (int i = 0; i < 3; i++) {
      builder.startLine().append(imports.getJavaUtilCollections()).appendLine(".emptyList(),");
    }

    builder.startLine().append(imports.getEnclosingMethodAttribute()).appendLine(".none(),");

    builder.startLine().append(imports.getJavaUtilCollections()).appendLine(".emptyList(),");

    builder.startLine().append(imports.getClassSignature()).appendLine(".noSignature(),");

    builder.startLine().append(imports.getDexAnnotationSet()).appendLine(".empty(),");

    if (clazz.hasStaticFields()) {
      builder.startLine().appendLine("createStaticFields(dexItemFactory),");
    } else {
      builder.startLine().appendLine("createStaticFields(),");
    }

    if (clazz.hasInstanceFields()) {
      builder.startLine().appendLine("createInstanceFields(dexItemFactory),");
    } else {
      builder.startLine().appendLine("createInstanceFields(),");
    }

    builder
        .startLine()
        .append(imports.getMethodCollectionFactory())
        .appendLine(
            ".fromMethods(createDirectMethods(dexItemFactory),"
                + " createVirtualMethods(dexItemFactory)),");

    builder.startLine().appendLine("dexItemFactory.getSkipNameValidationForTesting(),");

    builder.startLine().append(imports.getDexProgramClass()).append("::invalidChecksumRequest");

    builder.appendClosingMultiLineParenthesis().appendLine(';');
    builder.appendClosingBrace();
  }

  private void generateCreateFieldsMethod(
      JavaStringBuilder builder, String methodName, List<DexEncodedField> fields) {
    builder
        .startLine()
        .append("private static ")
        .append(imports.getDexEncodedField())
        .append("[] ")
        .append(methodName)
        .append("(");
    if (!fields.isEmpty()) {
      builder.append(imports.getDexItemFactory()).append(" dexItemFactory");
    }
    builder.append(")").appendOpeningBrace();

    builder
        .startLine()
        .append("return new ")
        .append(imports.getDexEncodedField())
        .append("[] ")
        .appendOpeningArrayBrace();

    Iterator<DexEncodedField> fieldIterator = fields.iterator();
    while (fieldIterator.hasNext()) {
      DexEncodedField field = fieldIterator.next();
      assert !field.isStatic() || !field.hasExplicitStaticValue();

      builder
          .startLine()
          .append(imports.getDexEncodedField())
          .appendLine(".syntheticBuilder()")
          .indent(4);

      builder.startLine().append(".setField").appendOpeningMultiLineParenthesis();

      builder.startLine().append("dexItemFactory.createField").appendOpeningMultiLineParenthesis();

      builder
          .startLine()
          .append("dexItemFactory.createType(\"")
          .append(field.getHolderType().toDescriptorString())
          .appendLine("\"),");

      builder
          .startLine()
          .append("dexItemFactory.createType(\"")
          .append(field.getType().toDescriptorString())
          .appendLine("\"),");

      builder
          .startLine()
          .append("dexItemFactory.createString(\"")
          .append(field.getName().toString())
          .append("\")")
          .appendClosingMultiLineParenthesis()
          .appendClosingMultiLineParenthesis()
          .appendLine();

      builder
          .startLine()
          .append(".setAccessFlags(")
          .append(imports.getFieldAccessFlags())
          .append(".fromCfAccessFlags(")
          .append(field.getAccessFlags().getAsCfAccessFlags())
          .appendLine("))");

      builder
          .startLine()
          .append(".setApiLevel(")
          .append(imports.getComputedApiLevel())
          .appendLine(".unknown())");

      builder.startLine().append(".build()").indent(-4);
      if (fieldIterator.hasNext()) {
        builder.appendLine(',');
      } else {
        builder.appendLine();
      }
    }

    builder.appendClosingArrayBrace();
    builder.appendClosingBrace();
  }

  private void generateCreateMethodsMethod(
      JavaStringBuilder builder,
      String methodName,
      Iterable<DexEncodedMethod> methods,
      Map<MethodReference, String> createCfCodeMethodNames) {
    builder
        .startLine()
        .append("private static ")
        .append(imports.getDexEncodedMethod())
        .append("[] ")
        .append(methodName)
        .append("(")
        .append(imports.getDexItemFactory())
        .append(" dexItemFactory) ")
        .appendOpeningBrace();

    builder
        .startLine()
        .append("return new ")
        .append(imports.getDexEncodedMethod())
        .append("[] ")
        .appendOpeningArrayBrace();

    Iterator<DexEncodedMethod> methodIterator = methods.iterator();
    while (methodIterator.hasNext()) {
      DexEncodedMethod method = methodIterator.next();
      builder
          .startLine()
          .append(imports.getDexEncodedMethod())
          .appendLine(".syntheticBuilder()")
          .indent(4);

      builder
          .startLine()
          .append(".setAccessFlags(")
          .append(imports.getMethodAccessFlags())
          .append(".fromCfAccessFlags(")
          .append(method.getAccessFlags().getAsCfAccessFlags())
          .append(", ")
          .append(method.isInitializer())
          .appendLine("))");

      builder
          .startLine()
          .append(".setApiLevelForCode(")
          .append(imports.getComputedApiLevel())
          .appendLine(".unknown())");

      builder
          .startLine()
          .append(".setApiLevelForDefinition(")
          .append(imports.getComputedApiLevel())
          .appendLine(".unknown())");

      builder
          .startLine()
          .append(".setClassFileVersion(")
          .append(imports.getCfVersion())
          .appendLine(".V1_8)");

      builder.startLine().append(".setMethod").appendOpeningMultiLineParenthesis();

      builder.startLine().append("dexItemFactory.createMethod").appendOpeningMultiLineParenthesis();

      builder
          .startLine()
          .append("dexItemFactory.createType(\"")
          .append(method.getHolderType().toDescriptorString())
          .appendLine("\"),");

      builder.startLine().append("dexItemFactory.createProto").appendOpeningMultiLineParenthesis();

      builder
          .startLine()
          .append("dexItemFactory.createType(\"")
          .append(method.getReturnType().toDescriptorString())
          .append("\")");

      for (DexType parameter : method.getParameters()) {
        builder
            .appendLine(",")
            .startLine()
            .append("dexItemFactory.createType(\"")
            .append(parameter.toDescriptorString())
            .append("\")");
      }
      builder.appendClosingMultiLineParenthesis().appendLine(',');

      builder
          .startLine()
          .append("dexItemFactory.createString(\"")
          .append(method.getName().toString())
          .append("\")");

      builder.appendClosingMultiLineParenthesis().appendClosingMultiLineParenthesis().appendLine();

      String createCfCodeMethodName =
          createCfCodeMethodNames.get(method.getReference().asMethodReference());
      if (createCfCodeMethodName != null) {
        builder
            .startLine()
            .append(".setCode(method -> ")
            .append(createCfCodeMethodName)
            .appendLine("(dexItemFactory, method))");
      }

      builder.startLine().append(".build()").indent(-4);
      if (methodIterator.hasNext()) {
        builder.appendLine(',');
      } else {
        builder.appendLine();
      }
    }

    builder.appendClosingArrayBrace();
    builder.appendClosingBrace();
  }

  private Map<MethodReference, String> generateCreateCfCodeMethods(
      DexProgramClass clazz, CfCodePrinter codePrinter) {
    Map<MethodReference, String> createCfCodeMethodNames = new HashMap<>();
    int index = 0;
    for (DexEncodedMethod method : clazz.allMethodsSorted()) {
      if (!method.hasCode()) {
        continue;
      }
      String generatedMethodName = getCreateCfCodeMethodName(method, index);
      createCfCodeMethodNames.put(method.getReference().asMethodReference(), generatedMethodName);
      codePrinter.visitMethod(generatedMethodName, method.getCode().asCfCode());
      index++;
    }
    codePrinter.getImportsSorted().forEach(imports::addImport);
    return createCfCodeMethodNames;
  }

  private String getCreateCfCodeMethodName(DexEncodedMethod method, int index) {
    if (method.isClassInitializer()) {
      return "createClassInitializerCfCode";
    }
    if (method.isInstanceInitializer()) {
      return "createInstanceInitializerCfCode" + index;
    }
    return "createCfCode" + index + "_" + method.getName().toString();
  }

  public void writeClassToFile() throws IOException {
    FileUtils.writeToFile(getGeneratedFile(), null, generateClass().getBytes());
  }
}

