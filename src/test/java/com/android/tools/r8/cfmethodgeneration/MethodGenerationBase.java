// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.CfCodePrinter;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.JarClassFileReader;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class MethodGenerationBase extends CodeGenerationBase {

  protected abstract List<Class<?>> getMethodTemplateClasses();

  protected List<Class<?>> getClassesToGenerate() {
    return ImmutableList.of();
  }

  protected DexEncodedField getField(DexEncodedField field) {
    return field;
  }

  protected boolean includeMethod(DexEncodedMethod method) {
    return !method.isInstanceInitializer();
  }

  protected CfCode getCode(String holderName, String methodName, CfCode code) {
    return code;
  }

  protected DexEncodedMethod mapMethod(DexEncodedMethod method) {
    return method;
  }

  // Running this method will regenerate / overwrite the content of the generated class.
  protected void generateMethodsAndWriteThemToFile() throws IOException {
    FileUtils.writeToFile(getGeneratedFile(), null, generateMethods().getBytes());
  }

  // Running this method generate the content of the generated class but does not overwrite it.
  protected String generateMethods() throws IOException {
    CfCodePrinter codePrinter = new CfCodePrinter();

    File tempFile = File.createTempFile("output-", ".java");

    Map<DexEncodedMethod, String> generatedMethods = new HashMap<>();
    List<DexEncodedField> fields = new ArrayList<>();
    readMethodTemplatesInto(codePrinter, generatedMethods::put, fields::add);
    generateRawOutput(generatedMethods, fields, codePrinter, tempFile.toPath());
    String result = formatRawOutput(tempFile.toPath());

    tempFile.deleteOnExit();
    return result;
  }

  private void readMethodTemplatesInto(
      CfCodePrinter codePrinter,
      BiConsumer<DexEncodedMethod, String> generatedMethods,
      Consumer<DexEncodedField> generatedFields)
      throws IOException {
    InternalOptions options = new InternalOptions(factory, new Reporter());
    options.testing.readInputStackMaps = true;
    JarClassFileReader<DexProgramClass> reader =
        new JarClassFileReader<>(
            new JarApplicationReader(options),
            clazz -> {
              for (DexEncodedField field : clazz.fields()) {
                DexEncodedField fieldToAddToClass = getField(field);
                if (fieldToAddToClass != null) {
                  generatedFields.accept(fieldToAddToClass);
                }
              }
              for (DexEncodedMethod method : clazz.allMethodsSorted()) {
                if (!includeMethod(method)) {
                  continue;
                }
                String holderName = method.getHolderType().getName();
                String methodName = method.getReference().name.toString();
                if (methodName.equals("<init>")) {
                  methodName = "constructor_" + method.getProto().getArity();
                }
                String generatedMethodName = holderName + "_" + methodName;
                CfCode code = getCode(holderName, methodName, method.getCode().asCfCode());
                if (code != null) {
                  codePrinter.visitMethod(generatedMethodName, code);
                  generatedMethods.accept(method, generatedMethodName);
                }
              }
            },
            ClassKind.PROGRAM);
    for (Class<?> clazz : getMethodTemplateClasses()) {
      reader.read(Origin.unknown(), ToolHelper.getClassAsBytes(clazz));
    }
  }

  private String createType(DexType type) {
    if (type.isVoidType()) {
      return "factory.voidType";
    }
    if (type.isBooleanType()) {
      return "factory.booleanType";
    }
    if (type.isIntType()) {
      return "factory.intType";
    }
    if (type.isLongType()) {
      return "factory.longType";
    }
    if (type.descriptor.toString().equals("Ljava/lang/Object;")) {
      return "factory.objectType";
    }
    return "factory.createType(factory.createString(\"" + type.getDescriptor() + "\"))";
  }

  private String createField(DexEncodedField field) {
    return "factory.createField(\n"
        + "builder.getType(),\n"
        + createType(field.getType())
        + ",\n"
        + "factory.createString(\""
        + field.getName()
        + "\"))\n";
  }

  private String buildSyntheticMethod(
      DexEncodedMethod method, String codeGenerator, Set<String> requiredImports) {
    String name =
        method.isInstanceInitializer()
            ? "constructor_" + method.getProto().getArity()
            : method.getName().toString();
    requiredImports.add("com.android.tools.r8.graph.DexEncodedMethod");
    requiredImports.add("com.android.tools.r8.graph.MethodAccessFlags");
    requiredImports.add("com.android.tools.r8.dex.Constants");
    return "DexEncodedMethod.syntheticBuilder()\n"
        + "        .setMethod("
        + name
        + ")\n"
        + "        .setAccessFlags(\n"
        + "            MethodAccessFlags.fromSharedAccessFlags(\n"
        + "                Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, "
        + (method.isInstanceInitializer())
        + "))\n"
        + "        .setCode("
        + codeGenerator
        + "(factory, "
        + name
        + "))\n"
        + "        .disableAndroidApiLevelCheck()\n"
        + "        .build()";
  }

  private String generateCreateProto(DexProto proto) {
    StringBuilder builder = new StringBuilder("factory.createProto(");
    builder.append(createType(proto.returnType));
    proto.getParameters().forEach(type -> builder.append(", ").append(createType(type)));
    builder.append(")");
    return builder.toString();
  }

  private String generateCreateMethod(DexEncodedMethod method) {
    return "factory.createMethod(\n"
        + "builder.getType(), "
        + generateCreateProto(method.getProto())
        + ", factory.createString(\""
        + method.getName()
        + "\"));";
  }

  private static void forMethodEachSorted(
      Map<DexEncodedMethod, String> methods, BiConsumer<DexEncodedMethod, String> fn) {
    ArrayList<DexEncodedMethod> sorted = new ArrayList<>(methods.keySet());
    sorted.sort(Comparator.comparing(DexEncodedMember::getReference));
    sorted.forEach(m -> fn.accept(m, methods.get(m)));
  }

  private void generateDexMethodLocals(
      Map<DexEncodedMethod, String> generatedMethods, PrintStream printer, Class<?> clazz) {
    // Generate local variable for a DexMethod:
    //  DexMethod name = factory.createMethod(
    //      builder.getType(), factory.createProto(...), factory.createString(...));

    forMethodEachSorted(
        generatedMethods,
        (method, codeGenerator) -> {
          if (method.getHolderType().toSourceString().equals(clazz.getCanonicalName())) {
            String name =
                method.isInstanceInitializer()
                    ? "constructor_" + method.getProto().getArity()
                    : method.getName().toString();
            printer.println("DexMethod " + name + " = " + generateCreateMethod(mapMethod(method)));
          }
        });
  }

  private void generateSyntheticMethodsList(
      Map<DexEncodedMethod, String> generatedMethods,
      Set<String> requiredImports,
      PrintStream printer,
      Class<?> clazz,
      Predicate<DexEncodedMethod> filter) {
    printer.println("ImmutableList.of(");
    BooleanBox first = new BooleanBox(true);
    forMethodEachSorted(
        generatedMethods,
        (method, codeGenerator) -> {
          if (method.getHolderType().toSourceString().equals(clazz.getCanonicalName())
              && filter.test(method)) {
            if (!first.get()) {
              printer.println(",\n");
            }
            first.set(false);
            printer.println(buildSyntheticMethod(method, codeGenerator, requiredImports));
          }
        });
    printer.println(")");
  }

  private String buildSyntheticField(DexEncodedField field, Set<String> requiredImports) {
    requiredImports.add("com.android.tools.r8.graph.DexEncodedField");
    requiredImports.add("com.android.tools.r8.graph.FieldAccessFlags");
    return "            DexEncodedField.syntheticBuilder()\n"
        + "                .setField("
        + createField(field)
        + ")\n"
        + "                .setAccessFlags(FieldAccessFlags.createPublicFinalSynthetic())\n"
        + "                .disableAndroidApiLevelCheck()\n"
        + "                .build()\n";
  }

  private void generateFieldList(
      List<DexEncodedField> fields,
      Set<String> requiredImports,
      PrintStream printer,
      Class<?> clazz) {
    printer.println("ImmutableList.of(");
    BooleanBox first = new BooleanBox(true);
    for (DexEncodedField field : fields) {
      if (field.getHolderType().toSourceString().equals(clazz.getCanonicalName())) {
        if (!first.get()) {
          printer.println(",\n");
        }
        first.set(false);
        printer.println(buildSyntheticField(field, requiredImports));
      }
    }
    printer.println(")");
  }

  private <K, V> Map<K, V> filterMapOnKey(Map<K, V> map, Predicate<K> predicate) {
    return map.entrySet().stream()
        .filter(entry -> predicate.test(entry.getKey()))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private void generateGenerateClasses(
      Map<DexEncodedMethod, String> allMethods,
      List<DexEncodedField> allFields,
      Set<String> requiredImports,
      PrintStream printer) {
    for (Class<?> clazz : getClassesToGenerate()) {
      String simpleName =
          clazz.getSimpleName(); // "DesugarVarHandle"; //name.substring(name.lastIndexOf('.'));
      List<DexEncodedField> classFields =
          ListUtils.filter(
              allFields,
              field -> field.getHolderType().toSourceString().equals(clazz.getCanonicalName()));
      Map<DexEncodedMethod, String> classMethods =
          filterMapOnKey(
              allMethods,
              method -> method.getHolderType().toSourceString().equals(clazz.getCanonicalName()));
      requiredImports.add("com.android.tools.r8.synthesis.SyntheticProgramClassBuilder");
      printer.println(
          "public static void generate"
              + simpleName
              + "Class(SyntheticProgramClassBuilder builder, DexItemFactory factory) {");
      printer.println("builder.setInstanceFields(");
      generateFieldList(classFields, requiredImports, printer, clazz);
      printer.println(");");
      generateDexMethodLocals(classMethods, printer, clazz);
      if (clazz.getSuperclass() != Object.class) {
        printer.println(
            "    builder.setSuperType("
                + createType(factory.createType(Reference.classFromClass(clazz.getSuperclass())))
                + ");");
      }
      printer.println("builder.setDirectMethods(");
      generateSyntheticMethodsList(
          classMethods, requiredImports, printer, clazz, DexEncodedMethod::isInstanceInitializer);
      printer.println(");");
      printer.println("builder.setVirtualMethods(");
      generateSyntheticMethodsList(
          allMethods, requiredImports, printer, clazz, method -> !method.isInstanceInitializer());
      printer.println(");");
      printer.println("}");
    }
  }

  private void generateRawOutput(
      Map<DexEncodedMethod, String> generatedMethods,
      List<DexEncodedField> fields,
      CfCodePrinter codePrinter,
      Path tempFile)
      throws IOException {
    try (PrintStream printer = new PrintStream(Files.newOutputStream(tempFile))) {
      printer.print(getHeaderString());

      Set<String> imports = Sets.newHashSet();
      imports.add("com.android.tools.r8.graph.DexItemFactory");
      // TODO(b/260985726): Consider only calling generateGenerateClasses once.
      // Generate classes into an unused PrintStream to collect imports.
      generateGenerateClasses(
          generatedMethods,
          fields,
          imports,
          new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8.name()));
      imports.addAll(codePrinter.getImports());
      ArrayList<String> sortedImports = new ArrayList<>(imports);
      sortedImports.sort(String::compareTo);
      sortedImports.forEach(i -> printer.println("import " + i + ";"));

      printer.println("public final class " + getGeneratedClassName() + " {\n");
      printer.println(
          "public static void registerSynthesizedCodeReferences(DexItemFactory factory) {");
      for (String type : new TreeSet<>(codePrinter.getSynthesizedTypes())) {
        printer.println("factory.createSynthesizedType(\"" + type + "\");");
      }
      printer.println("}");
      // TODO(b/260985726): Consider only calling generateGenerateClasses once.
      // Generate classes ignoring the collected imports (they are already added to the file).
      generateGenerateClasses(generatedMethods, fields, Sets.newHashSet(), printer);
      codePrinter.getMethods().forEach(printer::println);
      printer.println("}");
    }
  }
}
