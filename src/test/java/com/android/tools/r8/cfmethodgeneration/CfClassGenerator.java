// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.CfCodePrinter;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.JarClassFileReader;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.FieldReferenceUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;

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
    generateCreateClassMethod(builder);
    generateCreateFieldsMethod(builder, "createInstanceFields", not(FieldAccessFlags::isStatic));
    generateCreateFieldsMethod(builder, "createStaticFields", FieldAccessFlags::isStatic);
    CfCodePrinter codePrinter = new CfCodePrinter();
    Map<MethodReference, String> createCfCodeMethodNames = generateCreateCfCodeMethods(codePrinter);
    generateCreateMethodsMethod(
        builder,
        "createDirectMethods",
        MethodAccessFlags::belongsToDirectPool,
        createCfCodeMethodNames);
    generateCreateMethodsMethod(
        builder,
        "createVirtualMethods",
        MethodAccessFlags::belongsToVirtualPool,
        createCfCodeMethodNames);
    codePrinter.getMethods().forEach(builder::appendLine);
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
        .append(getImplementation().getModifiers())
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
        .append("[] ")
        .appendOpeningArrayBrace();

    Iterator<Field> fieldIterator =
        Arrays.stream(getImplementation().getDeclaredFields())
            .filter(
                field -> predicate.test(FieldAccessFlags.fromCfAccessFlags(field.getModifiers())))
            .sorted(
                (x, y) ->
                    FieldReferenceUtils.compare(
                        Reference.fieldFromField(x), Reference.fieldFromField(y)))
            .iterator();
    while (fieldIterator.hasNext()) {
      Field field = fieldIterator.next();
      FieldAccessFlags flags = FieldAccessFlags.fromCfAccessFlags(field.getModifiers());
      if (predicate.test(flags)) {
        builder
            .startLine()
            .append(imports.getDexEncodedField())
            .appendLine(".syntheticBuilder()")
            .indent(4);

        builder.startLine().append(".setField").appendOpeningMultiLineParenthesis();

        builder
            .startLine()
            .append("dexItemFactory.createField")
            .appendOpeningMultiLineParenthesis();

        builder
            .startLine()
            .append("dexItemFactory.createType(\"")
            .append(descriptor(field.getDeclaringClass()))
            .appendLine("\"),");

        builder
            .startLine()
            .append("dexItemFactory.createType(\"")
            .append(descriptor(field.getType()))
            .appendLine("\"),");

        builder
            .startLine()
            .append("dexItemFactory.createString(\"")
            .append(field.getName())
            .append("\")")
            .appendClosingMultiLineParenthesis()
            .appendClosingMultiLineParenthesis()
            .appendLine();

        builder
            .startLine()
            .append(".setAccessFlags(")
            .append(imports.getFieldAccessFlags())
            .append(".fromCfAccessFlags(")
            .append(field.getModifiers())
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
    }

    builder.appendClosingArrayBrace();
    builder.appendClosingBrace();
  }

  private void generateCreateMethodsMethod(
      JavaStringBuilder builder,
      String methodName,
      Predicate<MethodAccessFlags> predicate,
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

    getImplementation().getDeclaredConstructors();

    Iterator<Executable> executableIterator =
        Streams.concat(
                Arrays.stream(getImplementation().getDeclaredConstructors()),
                Arrays.stream(getImplementation().getDeclaredMethods()))
            .filter(
                executable ->
                    predicate.test(
                        MethodAccessFlags.fromCfAccessFlags(executable.getModifiers(), false)))
            .sorted(
                (x, y) ->
                    MethodReferenceUtils.compare(
                        Reference.methodFromMethod(x), Reference.methodFromMethod(y)))
            .iterator();
    while (executableIterator.hasNext()) {
      Executable executable = executableIterator.next();
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
          .append(executable.getModifiers())
          .append(", false")
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

      if (executable instanceof Constructor<?>) {
        Constructor<?> constructor = (Constructor<?>) executable;
        builder
            .startLine()
            .append("dexItemFactory.createInstanceInitializer")
            .appendOpeningMultiLineParenthesis();

        builder
            .startLine()
            .append("dexItemFactory.createType(\"")
            .append(descriptor(constructor.getDeclaringClass()))
            .append("\")");

        for (Class<?> parameter : constructor.getParameterTypes()) {
          builder
              .appendLine(",")
              .startLine()
              .append("dexItemFactory.createType(\"")
              .append(descriptor(parameter))
              .append("\")");
        }
      } else {
        assert executable instanceof Method;
        Method method = (Method) executable;

        builder
            .startLine()
            .append("dexItemFactory.createMethod")
            .appendOpeningMultiLineParenthesis();

        builder
            .startLine()
            .append("dexItemFactory.createType(\"")
            .append(descriptor(method.getDeclaringClass()))
            .appendLine("\"),");

        builder
            .startLine()
            .append("dexItemFactory.createProto")
            .appendOpeningMultiLineParenthesis();

        builder
            .startLine()
            .append("dexItemFactory.createType(\"")
            .append(descriptor(method.getReturnType()))
            .append("\")");

        for (Class<?> parameter : method.getParameterTypes()) {
          builder
              .appendLine(",")
              .startLine()
              .append("dexItemFactory.createType(\"")
              .append(descriptor(parameter))
              .append("\")");
        }
        builder.appendClosingMultiLineParenthesis().appendLine(',');

        builder
            .startLine()
            .append("dexItemFactory.createString(\"")
            .append(method.getName())
            .append("\")");
      }

      builder.appendClosingMultiLineParenthesis().appendClosingMultiLineParenthesis().appendLine();

      String createCfCodeMethodName =
          createCfCodeMethodNames.get(Reference.methodFromMethod(executable));
      if (createCfCodeMethodName != null) {
        builder
            .startLine()
            .append(".setCode(method -> ")
            .append(createCfCodeMethodName)
            .appendLine("(dexItemFactory, method))");
      }

      builder.startLine().append(".build()").indent(-4);
      if (executableIterator.hasNext()) {
        builder.appendLine(',');
      } else {
        builder.appendLine();
      }
    }

    builder.appendClosingArrayBrace();
    builder.appendClosingBrace();
  }

  private Map<MethodReference, String> generateCreateCfCodeMethods(CfCodePrinter codePrinter)
      throws IOException {
    Map<MethodReference, String> createCfCodeMethodNames = new HashMap<>();
    InternalOptions options = new InternalOptions(factory, new Reporter());
    options.testing.readInputStackMaps = true;
    JarClassFileReader<DexProgramClass> reader =
        new JarClassFileReader<>(
            new JarApplicationReader(options),
            clazz -> {
              int index = 0;
              for (DexEncodedMethod method : clazz.allMethodsSorted()) {
                if (!method.hasCode()) {
                  continue;
                }
                String generatedMethodName = getCreateCfCodeMethodName(method, index);
                createCfCodeMethodNames.put(
                    method.getReference().asMethodReference(), generatedMethodName);
                codePrinter.visitMethod(generatedMethodName, method.getCode().asCfCode());
                index++;
              }
            },
            ClassKind.PROGRAM);
    reader.read(Origin.unknown(), ToolHelper.getClassAsBytes(getImplementation()));
    codePrinter.getImports().forEach(imports::addImport);
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

