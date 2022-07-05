// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.function.Predicate;

public abstract class CfClassGenerator extends CodeGenerationBase {

  private final CfCodeGeneratorImportCollection imports = new CfCodeGeneratorImportCollection();

  public abstract Class<?> getGeneratedClass();

  @Override
  protected DexType getGeneratedType() {
    return factory.createType(descriptor(getGeneratedClass()));
  }

  public String generateClass() throws IOException {
    return formatRawOutput(generateRawOutput());
  }

  private String generateRawOutput() {
    String classDeclaration = generateClassDeclaration();
    return StringUtils.lines(getHeaderString(), imports.generateImports(), classDeclaration);
  }

  private String generateClassDeclaration() {
    JavaStringBuilder builder = new JavaStringBuilder();
    builder.append("public final class " + getGeneratedClassName() + " ").appendOpeningBrace();
    generateCreateClassMethod(builder);
    generateCreateFieldsMethod(builder, "createInstanceFields", not(FieldAccessFlags::isStatic));
    generateCreateFieldsMethod(builder, "createStaticFields", FieldAccessFlags::isStatic);
    generateCreateMethodsMethod(
        builder, "createDirectMethods", MethodAccessFlags::belongsToDirectPool);
    generateCreateMethodsMethod(
        builder, "createVirtualMethods", MethodAccessFlags::belongsToVirtualPool);
    builder.appendClosingBrace();
    return builder.toString();
  }

  private void generateCreateClassMethod(JavaStringBuilder builder) {
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
        .append(getGeneratedClassDescriptor())
        .appendLine("\"),");

    builder.startLine().append(imports.getProgramResourceKind()).appendLine(".CF,");

    builder.startLine().append(imports.getOrigin()).appendLine(".unknown(),");

    builder
        .startLine()
        .append(imports.getClassAccessFlags())
        .append(".fromCfAccessFlags(")
        .append(getGeneratedClass().getModifiers())
        .appendLine("),");

    builder.startLine().appendLine("null,");

    builder.startLine().append(imports.getDexTypeList()).appendLine(".empty(),");

    builder
        .startLine()
        .append("dexItemFactory.createString(\"")
        .append(getGeneratedClassName())
        .appendLine("\"),");

    builder.startLine().append(imports.getNestHostClassAttribute()).appendLine(".none(),");

    for (int i = 0; i < 2; i++) {
      builder.startLine().append(imports.getJavaUtilCollections()).appendLine(".emptyList(),");
    }

    builder.startLine().append(imports.getEnclosingMethodAttribute()).appendLine(".none(),");

    builder.startLine().append(imports.getJavaUtilCollections()).appendLine(".emptyList(),");

    builder.startLine().append(imports.getClassSignature()).appendLine(".noSignature(),");

    builder.startLine().append(imports.getDexAnnotationSet()).appendLine(".empty(),");

    builder.startLine().appendLine("createStaticFields(dexItemFactory),");

    builder.startLine().appendLine("createInstanceFields(dexItemFactory),");

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
      JavaStringBuilder builder, String methodName, Predicate<FieldAccessFlags> predicate) {
    builder
        .startLine()
        .append("private static ")
        .append(imports.getDexEncodedField())
        .append("[] ")
        .append(methodName)
        .append("(")
        .append(imports.getDexItemFactory())
        .append(" dexItemFactory) ")
        .appendOpeningBrace();

    builder
        .startLine()
        .append("return new ")
        .append(imports.getDexEncodedField())
        .appendLine("[0];");

    builder.appendClosingBrace();
  }

  private void generateCreateMethodsMethod(
      JavaStringBuilder builder, String methodName, Predicate<MethodAccessFlags> predicate) {
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
        .appendLine("[0];");

    builder.appendClosingBrace();
  }

  public void writeClassToFile() throws IOException {
    FileUtils.writeToFile(getGeneratedFile(), null, generateClass().getBytes());
  }
}

