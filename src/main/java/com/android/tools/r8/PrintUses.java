// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.shaking.MainDexClasses;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PrintUses prints the classes, interfaces, methods and fields used by a given program
 * &lt;sample.jar&gt;, restricted to classes and interfaces in a given library &lt;r8.jar&gt; that
 * are not in &lt;sample.jar&gt;.
 *
 * <p>The output is in the same format as what is printed when specifying {@code -printseeds} in a
 * ProGuard configuration file. See also the {@link PrintSeeds} program in R8.
 *
 * <p>Note that this tool is not related to the {@code -printusage} option of ProGuard configuration
 * files.
 */
public class PrintUses {

  private static final String USAGE =
      "Arguments: [--keeprules, --keeprules-allowobfuscation] <rt.jar> <r8.jar> <sample.jar>\n"
          + "\n"
          + "PrintUses prints the classes, interfaces, methods and fields used by <sample.jar>,\n"
          + "restricted to classes and interfaces in <r8.jar> that are not in <sample.jar>.\n"
          + "<rt.jar> and <r8.jar> should point to libraries used by <sample.jar>.\n"
          + "\n"
          + "The output is in the same format as what is printed when specifying -printseeds in\n"
          + "a ProGuard configuration file. Use --keeprules or --keeprules-allowobfuscation for "
          + "outputting proguard keep rules. See also the "
          + PrintSeeds.class.getSimpleName()
          + " program in R8.";

  private final Set<String> descriptors;
  private final Printer printer;
  private final boolean allowObfuscation;
  private Set<DexType> types = Sets.newIdentityHashSet();
  private Map<DexType, Set<DexMethod>> methods = Maps.newIdentityHashMap();
  private Map<DexType, Set<DexField>> fields = Maps.newIdentityHashMap();
  private Set<DexType> noObfuscationTypes = Sets.newIdentityHashSet();
  private Set<String> keepPackageNames = Sets.newHashSet();
  private final DirectMappedDexApplication application;
  private final AppInfoWithClassHierarchy appInfo;
  private int errors;

  class UseCollector extends UseRegistry {

    private DexProgramClass context;

    UseCollector(DexItemFactory factory) {
      super(factory);
    }

    public void setContext(DexProgramClass context) {
      this.context = context;
    }

    @Override
    public void registerInitClass(DexType clazz) {
      addType(clazz);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      ResolutionResult resolutionResult = appInfo.unsafeResolveMethodDueToDexFormat(method);
      DexEncodedMethod target =
          resolutionResult.isVirtualTarget() ? resolutionResult.getSingleTarget() : null;
      if (target != null && target.method != method) {
        addType(method.holder);
        addMethod(target.method);
      } else {
        addMethod(method);
      }
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      addMethod(method);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      DexEncodedMethod target = appInfo.unsafeResolveMethodDueToDexFormat(method).getSingleTarget();
      if (target != null && target.method != method) {
        addType(method.holder);
        addMethod(target.method);
      } else {
        addMethod(method);
      }
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      registerInvokeVirtual(method);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      DexEncodedMethod superTarget = appInfo.lookupSuperTarget(method, context);
      if (superTarget != null) {
        addMethod(superTarget.method);
      } else {
        addMethod(method);
      }
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      addField(field);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      addField(field);
    }

    @Override
    public void registerNewInstance(DexType type) {
      addType(type);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      addField(field);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      addField(field);
    }

    @Override
    public void registerTypeReference(DexType type) {
      addType(type);
    }

    @Override
    public void registerInstanceOf(DexType type) {
      addType(type);
    }

    private void addType(DexType type) {
      if (isTargetType(type) && types.add(type)) {
        DexClass clazz = appInfo.definitionFor(type);
        if (clazz == null || !allowObfuscation) {
          noObfuscationTypes.add(type);
        }
        if (clazz != null && clazz.accessFlags.isVisibilityDependingOnPackage()) {
          keepPackageNames.add(clazz.type.getPackageName());
        }
        methods.put(type, Sets.newIdentityHashSet());
        fields.put(type, Sets.newIdentityHashSet());
      }
    }

    private boolean isTargetType(DexType type) {
      return descriptors.contains(type.toDescriptorString());
    }

    private void addField(DexField field) {
      addType(field.type);
      DexEncodedField baseField = appInfo.resolveField(field).getResolvedField();
      if (baseField != null && baseField.holder() != field.holder) {
        field = baseField.field;
      }
      addType(field.holder);
      Set<DexField> typeFields = fields.get(field.holder);
      if (typeFields != null) {
        if (baseField == null) {
          System.out.println(field.toSourceString());
        }
        assert baseField != null : field.toSourceString();
        if (!allowObfuscation) {
          noObfuscationTypes.add(field.holder);
        }
        if (baseField.accessFlags.isVisibilityDependingOnPackage()) {
          keepPackageNames.add(baseField.holder().getPackageName());
        }
        typeFields.add(field);
      }
    }

    private void addMethod(DexMethod method) {
      addType(method.holder);
      for (DexType parameterType : method.proto.parameters.values) {
        addType(parameterType);
      }
      addType(method.proto.returnType);
      Set<DexMethod> typeMethods = methods.get(method.holder);
      if (typeMethods != null) {
        DexClass holder = appInfo.definitionForHolder(method);
        DexEncodedMethod definition = method.lookupOnClass(holder);
        assert definition != null : "Could not find method " + method.toString();
        if (!allowObfuscation) {
          noObfuscationTypes.add(method.holder);
        }
        if (definition.accessFlags.isVisibilityDependingOnPackage()) {
          keepPackageNames.add(definition.holder().getPackageName());
        }
        typeMethods.add(method);
      }
    }

    private void registerField(DexEncodedField field) {
      registerTypeReference(field.field.type);
    }

    private void registerMethod(ProgramMethod method) {
      DexEncodedMethod superTarget =
          appInfo
              .resolveMethodOn(method.getHolder(), method.getReference())
              .lookupInvokeSpecialTarget(context, appInfo);
      if (superTarget != null) {
        addMethod(superTarget.method);
      }
      for (DexType type : method.getDefinition().parameters().values) {
        registerTypeReference(type);
      }
      for (DexAnnotation annotation : method.getDefinition().annotations().annotations) {
        if (annotation.annotation.type == appInfo.dexItemFactory().annotationThrows) {
          DexValueArray dexValues = annotation.annotation.elements[0].value.asDexValueArray();
          for (DexValue dexValType : dexValues.getValues()) {
            registerTypeReference(dexValType.asDexValueType().value);
          }
        }
      }
      registerTypeReference(method.getDefinition().returnType());
      method.registerCodeReferences(this);
    }

    private void registerSuperType(DexProgramClass clazz, DexType superType) {
      registerTypeReference(superType);
      // If clazz overrides any methods in superType, we should keep those as well.
      clazz.forEachMethod(
          method -> {
            ResolutionResult resolutionResult =
                appInfo.resolveMethodOn(superType, method.method, superType != clazz.superType);
            DexEncodedMethod dexEncodedMethod = resolutionResult.getSingleTarget();
            if (dexEncodedMethod != null) {
              addMethod(dexEncodedMethod.method);
            }
          });
    }

    @Override
    public void registerCallSite(DexCallSite callSite) {
      super.registerCallSite(callSite);

      // For Lambda's, in order to find the correct use, we need to register the method for the
      // functional interface.
      List<DexType> directInterfaces = LambdaDescriptor.getInterfaces(callSite, appInfo);
      if (directInterfaces != null) {
        for (DexType directInterface : directInterfaces) {
          DexProgramClass clazz = asProgramClassOrNull(appInfo.definitionFor(directInterface));
          if (clazz != null) {
            clazz.forEachProgramVirtualMethodMatching(
                definition -> definition.getReference().name.equals(callSite.methodName),
                this::registerMethod);
          }
        }
      }
    }
  }

  public static void main(String... args) throws Exception {
    if (args.length != 3 && args.length != 4 && args.length != 5) {
      System.out.println(USAGE.replace("\n", System.lineSeparator()));
      return;
    }
    int argumentIndex = 0;
    boolean printKeep = false;
    boolean allowObfuscation = false;
    if (args[0].equals("--keeprules") || args[0].equals("--keeprules-allowobfuscation")) {
      printKeep = true;
      argumentIndex++;
      allowObfuscation = args[0].equals("--keeprules-allowobfuscation");
      // Make sure there is only one argument that mentions --keeprules
      for (int i = 1; i < args.length; i++) {
        if (args[i].startsWith("-keeprules")) {
          System.out.println("Use either --keeprules or --keeprules-allowobfuscation, not both.");
          System.out.println(USAGE.replace("\n", System.lineSeparator()));
          return;
        }
      }
    }
    AndroidApp.Builder builder = AndroidApp.builder();
    Path rtJar = Paths.get(args[argumentIndex++]);
    builder.addLibraryFile(rtJar);
    Path r8Jar = Paths.get(args[argumentIndex++]);
    builder.addLibraryFile(r8Jar);
    Path sampleJar = Paths.get(args[argumentIndex++]);
    builder.addProgramFile(sampleJar);
    PrintStream output = System.out;
    if (argumentIndex < args.length) {
      output = new PrintStream(Files.newOutputStream(Paths.get(args[argumentIndex])));
    }
    Set<String> descriptors = new HashSet<>(getDescriptors(r8Jar));
    descriptors.removeAll(getDescriptors(sampleJar));
    Printer printer = printKeep ? new KeepPrinter(output) : new DefaultPrinter(output);
    PrintUses printUses = new PrintUses(descriptors, builder.build(), printer, allowObfuscation);
    printUses.analyze();
    printUses.print();
    if (printUses.errors > 0) {
      System.err.println(printUses.errors + " errors");
      System.exit(1);
    }
  }

  private static Set<String> getDescriptors(Path path) throws IOException {
    return new ArchiveClassFileProvider(path).getClassDescriptors();
  }

  private PrintUses(
      Set<String> descriptors, AndroidApp inputApp, Printer printer, boolean allowObfuscation)
      throws Exception {
    this.descriptors = descriptors;
    this.printer = printer;
    this.allowObfuscation = allowObfuscation;
    InternalOptions options = new InternalOptions();
    application =
        new ApplicationReader(inputApp, options, new Timing("PrintUses")).read().toDirect();
    appInfo =
        AppInfoWithClassHierarchy.createInitialAppInfoWithClassHierarchy(
            application,
            ClassToFeatureSplitMap.createEmptyClassToFeatureSplitMap(),
            MainDexClasses.createEmptyMainDexClasses());
  }

  private void analyze() {
    UseCollector useCollector = new UseCollector(appInfo.dexItemFactory());
    for (DexProgramClass clazz : application.classes()) {
      useCollector.setContext(clazz);
      useCollector.registerSuperType(clazz, clazz.superType);
      for (DexType implementsType : clazz.interfaces.values) {
        useCollector.registerSuperType(clazz, implementsType);
      }
      clazz.forEachProgramMethod(useCollector::registerMethod);
      clazz.forEachField(useCollector::registerField);
    }
  }

  private void print() {
    errors =
        printer.print(application, types, noObfuscationTypes, keepPackageNames, methods, fields);
  }

  private abstract static class Printer {

    PrintStream output;

    Printer(PrintStream output) {
      this.output = output;
    }

    void append(String string) {
      output.print(string);
    }

    void appendLine(String string) {
      output.println(string);
    }

    void printArguments(DexMethod method) {
      append("(");
      for (int i = 0; i < method.getArity(); i++) {
        if (i != 0) {
          append(",");
        }
        append(method.proto.parameters.values[i].toSourceString());
      }
      append(")");
    }

    abstract void printConstructorName(DexEncodedMethod encodedMethod);

    void printError(String message) {
      appendLine("# Error: " + message);
    }

    abstract void printField(DexClass dexClass, DexField field);

    abstract void printMethod(DexEncodedMethod encodedMethod, String typeName);

    abstract void printPackageNames(List<String> packageNames);

    void printNameAndReturn(DexEncodedMethod encodedMethod) {
      if (encodedMethod.accessFlags.isConstructor()) {
        printConstructorName(encodedMethod);
      } else {
        DexMethod method = encodedMethod.method;
        append(method.proto.returnType.toSourceString());
        append(" ");
        append(method.name.toSourceString());
      }
    }

    abstract void printTypeHeader(DexClass dexClass, boolean allowObfuscation);

    abstract void printTypeFooter();

    int print(
        DexApplication application,
        Set<DexType> types,
        Set<DexType> noObfuscationTypes,
        Set<String> keepPackageNames,
        Map<DexType, Set<DexMethod>> methods,
        Map<DexType, Set<DexField>> fields) {
      int errors = 0;
      List<DexType> sortedTypes = new ArrayList<>(types);
      sortedTypes.sort(Comparator.comparing(DexType::toSourceString));
      for (DexType type : sortedTypes) {
        DexClass dexClass = application.definitionFor(type);
        if (dexClass == null) {
          printError("Could not find definition for type " + type.toSourceString());
          errors++;
          continue;
        }
        printTypeHeader(dexClass, !noObfuscationTypes.contains(type));
        List<DexEncodedMethod> methodDefinitions = new ArrayList<>(methods.size());
        for (DexMethod method : methods.get(type)) {
          DexEncodedMethod encodedMethod = dexClass.lookupMethod(method);
          if (encodedMethod == null) {
            printError("Could not find definition for method " + method.toSourceString());
            errors++;
            continue;
          }
          methodDefinitions.add(encodedMethod);
        }
        methodDefinitions.sort(Comparator.comparing(x -> x.method.name.toSourceString()));
        for (DexEncodedMethod encodedMethod : methodDefinitions) {
          printMethod(encodedMethod, dexClass.type.toSourceString());
        }
        List<DexField> sortedFields = new ArrayList<>(fields.get(type));
        sortedFields.sort(Comparator.comparing(DexField::toSourceString));
        for (DexField field : sortedFields) {
          printField(dexClass, field);
        }
        printTypeFooter();
      }
      ArrayList<String> packageNamesToKeep = new ArrayList<>(keepPackageNames);
      Collections.sort(packageNamesToKeep);
      printPackageNames(packageNamesToKeep);
      return errors;
    }
  }

  private static class DefaultPrinter extends Printer {

    DefaultPrinter(PrintStream output) {
      super(output);
    }

    @Override
    public void printConstructorName(DexEncodedMethod encodedMethod) {
      if (encodedMethod.accessFlags.isStatic()) {
        append("<clinit>");
      } else {
        String holderName = encodedMethod.holder().toSourceString();
        String constructorName = holderName.substring(holderName.lastIndexOf('.') + 1);
        append(constructorName);
      }
    }

    @Override
    void printMethod(DexEncodedMethod encodedMethod, String typeName) {
      append(typeName + ": ");
      printNameAndReturn(encodedMethod);
      printArguments(encodedMethod.method);
      appendLine("");
    }

    @Override
    void printPackageNames(List<String> packageNames) {
      // No need to print package names for text output.
    }

    @Override
    void printTypeHeader(DexClass dexClass, boolean allowObfuscation) {
      appendLine(dexClass.type.toSourceString());
    }

    @Override
    void printTypeFooter() {}

    @Override
    void printField(DexClass dexClass, DexField field) {
      appendLine(
          dexClass.type.toSourceString()
              + ": "
              + field.type.toSourceString()
              + " "
              + field.name.toString());
    }
  }

  private static class KeepPrinter extends Printer {

    KeepPrinter(PrintStream output) {
      super(output);
    }

    @Override
    public void printTypeHeader(DexClass dexClass, boolean allowObfuscation) {
      append(allowObfuscation ? "-keep,allowobfuscation" : "-keep");
      if (dexClass.isInterface()) {
        append(" interface " + dexClass.type.toSourceString() + " {\n");
      } else if (dexClass.accessFlags.isEnum()) {
        append(" enum " + dexClass.type.toSourceString() + " {\n");
      } else {
        append(" class " + dexClass.type.toSourceString() + " {\n");
      }
    }

    @Override
    public void printConstructorName(DexEncodedMethod encodedMethod) {
      append("<init>");
    }

    @Override
    public void printField(DexClass dexClass, DexField field) {
      append("  " + field.type.toSourceString() + " " + field.name.toString() + ";\n");
    }

    @Override
    public void printMethod(DexEncodedMethod encodedMethod, String typeName) {
      // Static initializers do not require keep rules - it is kept by keeping the class.
      if (encodedMethod.accessFlags.isConstructor() && encodedMethod.accessFlags.isStatic()) {
        return;
      }
      append("  ");
      if (encodedMethod.isPublicMethod()) {
        append("public ");
      } else if (encodedMethod.isPrivateMethod()) {
        append("private ");
      }
      if (encodedMethod.isStatic()) {
        append("static ");
      }
      printNameAndReturn(encodedMethod);
      printArguments(encodedMethod.method);
      appendLine(";");
    }

    @Override
    void printPackageNames(List<String> packageNames) {
      append("-keeppackagenames " + StringUtils.join(packageNames, ",") + "\n");
    }

    @Override
    public void printTypeFooter() {
      appendLine("}");
    }
  }
}
