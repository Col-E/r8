// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCode.LocalVariableInfo;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProgramClass.ChecksumSupplier;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfiguration;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfigurationParser;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public class GenerateLintFiles {

  private static final String ANDROID_JAR_PATTERN = "third_party/android_jar/lib-v%d/android.jar";

  private final DexItemFactory factory = new DexItemFactory();
  private final Reporter reporter = new Reporter();
  private final InternalOptions options = new InternalOptions(factory, reporter);

  private final DesugaredLibraryConfiguration desugaredLibraryConfiguration;
  private final Path desugaredLibraryImplementation;
  private final Path outputDirectory;

  private final Set<DexMethod> parallelMethods = Sets.newIdentityHashSet();

  public GenerateLintFiles(
      String desugarConfigurationPath, String desugarImplementationPath, String outputDirectory)
      throws Exception {
    this.desugaredLibraryConfiguration =
        readDesugaredLibraryConfiguration(desugarConfigurationPath);
    this.desugaredLibraryImplementation = Paths.get(desugarImplementationPath);
    this.outputDirectory = Paths.get(outputDirectory);
    if (!Files.isDirectory(this.outputDirectory)) {
      throw new Exception("Output directory " + outputDirectory + " is not a directory");
    }

    DexType streamType = factory.createType(factory.createString("Ljava/util/stream/Stream;"));
    DexMethod parallelMethod =
        factory.createMethod(
            factory.collectionType,
            factory.createProto(streamType),
            factory.createString("parallelStream"));
    parallelMethods.add(parallelMethod);
    DexType baseStreamType =
        factory.createType(factory.createString("Ljava/util/stream/BaseStream;"));
    for (String typePrefix : new String[] {"Base", "Double", "Int", "Long"}) {
      streamType =
          factory.createType(factory.createString("Ljava/util/stream/" + typePrefix + "Stream;"));
      parallelMethod =
          factory.createMethod(
              streamType, factory.createProto(streamType), factory.createString("parallel"));
      parallelMethods.add(parallelMethod);
      // Also filter out the generated bridges for the covariant return type.
      parallelMethod =
          factory.createMethod(
              streamType, factory.createProto(baseStreamType), factory.createString("parallel"));
      parallelMethods.add(parallelMethod);
    }
  }

  private static Path getAndroidJarPath(AndroidApiLevel apiLevel) {
    String jar = String.format(ANDROID_JAR_PATTERN, apiLevel.getLevel());
    return Paths.get(jar);
  }

  private DesugaredLibraryConfiguration readDesugaredLibraryConfiguration(
      String desugarConfigurationPath) {
    return new DesugaredLibraryConfigurationParser(
            factory, reporter, false, AndroidApiLevel.B.getLevel())
        .parse(StringResource.fromFile(Paths.get(desugarConfigurationPath)));
  }

  private CfCode buildEmptyThrowingCfCode(DexMethod method) {
    CfInstruction insn[] = {new CfConstNull(), new CfThrow()};
    return new CfCode(
        method.holder,
        1,
        method.proto.parameters.size() + 1,
        Arrays.asList(insn),
        Collections.emptyList(),
        Collections.emptyList());
  }

  private void addMethodsToHeaderJar(
      DexApplication.Builder builder, DexClass clazz, List<DexEncodedMethod> methods) {
    if (methods.size() == 0) {
      return;
    }

    List<DexEncodedMethod> directMethods = new ArrayList<>();
    List<DexEncodedMethod> virtualMethods = new ArrayList<>();
    for (DexEncodedMethod method : methods) {
      assert method.holder() == clazz.type;
      CfCode code = null;
      if (!method.accessFlags.isAbstract() /*&& !method.accessFlags.isNative()*/) {
        code = buildEmptyThrowingCfCode(method.method);
      }
      DexEncodedMethod throwingMethod =
          new DexEncodedMethod(
              method.method,
              method.accessFlags,
              MethodTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              code,
              false,
              CfVersion.V1_6,
              false);
      if (method.isStatic() || method.isDirectMethod()) {
        directMethods.add(throwingMethod);
      } else {
        virtualMethods.add(throwingMethod);
      }
    }

    DexEncodedMethod[] directMethodsArray = new DexEncodedMethod[directMethods.size()];
    DexEncodedMethod[] virtualMethodsArray = new DexEncodedMethod[virtualMethods.size()];
    directMethods.toArray(directMethodsArray);
    virtualMethods.toArray(virtualMethodsArray);
    assert !options.encodeChecksums;
    ChecksumSupplier checksumSupplier = DexProgramClass::invalidChecksumRequest;
    DexProgramClass programClass =
        new DexProgramClass(
            clazz.type,
            null,
            Origin.unknown(),
            clazz.accessFlags,
            clazz.superType,
            clazz.interfaces,
            null,
            null,
            Collections.emptyList(),
            null,
            Collections.emptyList(),
            ClassSignature.noSignature(),
            DexAnnotationSet.empty(),
            DexEncodedField.EMPTY_ARRAY,
            DexEncodedField.EMPTY_ARRAY,
            directMethodsArray,
            virtualMethodsArray,
            false,
            checksumSupplier);
    builder.addProgramClass(programClass);
  }

  public static class SupportedMethods {
    public final Set<DexClass> classesWithAllMethodsSupported;
    public final Map<DexClass, List<DexEncodedMethod>> supportedMethods;

    public SupportedMethods(
        Set<DexClass> classesWithAllMethodsSupported,
        Map<DexClass, List<DexEncodedMethod>> supportedMethods) {
      this.classesWithAllMethodsSupported = classesWithAllMethodsSupported;
      this.supportedMethods = supportedMethods;
    }
  }

  private SupportedMethods collectSupportedMethods(
      AndroidApiLevel compilationApiLevel, Predicate<DexEncodedMethod> supported) throws Exception {

    // Read the android.jar for the compilation API level. Read it as program instead of library
    // to get the local information for parameter names.
    AndroidApp library =
        AndroidApp.builder().addProgramFiles(getAndroidJarPath(compilationApiLevel)).build();
    DirectMappedDexApplication dexApplication =
        new ApplicationReader(library, options, Timing.empty()).read().toDirect();

    AndroidApp implementation =
        AndroidApp.builder().addProgramFiles(desugaredLibraryImplementation).build();
    DirectMappedDexApplication implementationApplication =
        new ApplicationReader(implementation, options, Timing.empty()).read().toDirect();

    // Collect all the methods that the library desugar configuration adds support for.
    Set<DexClass> classesWithAllMethodsSupported = Sets.newIdentityHashSet();
    Map<DexClass, List<DexEncodedMethod>> supportedMethods = new LinkedHashMap<>();
    for (DexProgramClass clazz : dexApplication.classes()) {
      String className = clazz.toSourceString();
      // All the methods with the rewritten prefix are supported.
      for (String prefix : desugaredLibraryConfiguration.getRewritePrefix().keySet()) {
        if (clazz.accessFlags.isPublic() && className.startsWith(prefix)) {
          DexProgramClass implementationClass =
              implementationApplication.programDefinitionFor(clazz.getType());
          if (implementationClass == null) {
            throw new Exception("Implementation class not found for " + clazz.toSourceString());
          }
          boolean allMethodsAddad = true;
          for (DexEncodedMethod method : clazz.methods()) {
            if (!method.isPublic()) {
              continue;
            }
            ProgramMethod implementationMethod =
                implementationClass.lookupProgramMethod(method.method);
            // Don't include methods which are not implemented by the desugared library.
            if (supported.test(method) && implementationMethod != null) {
              supportedMethods.computeIfAbsent(clazz, k -> new ArrayList<>()).add(method);
            } else {
              allMethodsAddad = false;
            }
          }
          if (allMethodsAddad) {
            classesWithAllMethodsSupported.add(clazz);
          }
        }
      }

      // All retargeted methods are supported.
      for (DexEncodedMethod method : clazz.methods()) {
        if (desugaredLibraryConfiguration
            .getRetargetCoreLibMember()
            .keySet()
            .contains(method.method.name)) {
          if (desugaredLibraryConfiguration
              .getRetargetCoreLibMember()
              .get(method.method.name)
              .containsKey(clazz.type)) {
            if (supported.test(method)) {
              supportedMethods.computeIfAbsent(clazz, k -> new ArrayList<>()).add(method);
            }
          }
        }
      }

      // All emulated interfaces static and default methods are supported.
      if (desugaredLibraryConfiguration.getEmulateLibraryInterface().containsKey(clazz.type)) {
        assert clazz.isInterface();
        for (DexEncodedMethod method : clazz.methods()) {
          if (!method.isDefaultMethod() && !method.isStatic()) {
            continue;
          }
          if (supported.test(method)) {
            supportedMethods.computeIfAbsent(clazz, k -> new ArrayList<>()).add(method);
          }
        }
      }
    }

    return new SupportedMethods(classesWithAllMethodsSupported, supportedMethods);
  }

  private String lintBaseFileName(
      AndroidApiLevel compilationApiLevel, AndroidApiLevel minApiLevel) {
    return "desugared_apis_" + compilationApiLevel.getLevel() + "_" + minApiLevel.getLevel();
  }

  private Path lintFile(
      AndroidApiLevel compilationApiLevel, AndroidApiLevel minApiLevel, String extension)
      throws Exception {
    Path directory = outputDirectory.resolve("compile_api_level_" + compilationApiLevel.getLevel());
    Files.createDirectories(directory);
    return Paths.get(
        directory
            + File.separator
            + lintBaseFileName(compilationApiLevel, minApiLevel)
            + extension);
  }

  private void writeLintFiles(
      AndroidApiLevel compilationApiLevel,
      AndroidApiLevel minApiLevel,
      SupportedMethods supportedMethods)
      throws Exception {
    // Build a plain text file with the desugared APIs.
    List<String> desugaredApisSignatures = new ArrayList<>();

    LazyLoadedDexApplication.Builder builder = DexApplication.builder(options, Timing.empty());
    supportedMethods.supportedMethods.forEach(
        (clazz, methods) -> {
          String classBinaryName =
              DescriptorUtils.getClassBinaryNameFromDescriptor(clazz.type.descriptor.toString());
          if (!supportedMethods.classesWithAllMethodsSupported.contains(clazz)) {
            for (DexEncodedMethod method : methods) {
              if (method.isInstanceInitializer() || method.isClassInitializer()) {
                // No new constructors are added.
                continue;
              }
              desugaredApisSignatures.add(
                  classBinaryName
                      + '#'
                      + method.method.name
                      + method.method.proto.toDescriptorString());
            }
          } else {
            desugaredApisSignatures.add(classBinaryName);
          }

          addMethodsToHeaderJar(builder, clazz, methods);
        });

    // Write a plain text file with the desugared APIs.
    desugaredApisSignatures.sort(Comparator.naturalOrder());
    FileUtils.writeTextFile(
        lintFile(compilationApiLevel, minApiLevel, ".txt"), desugaredApisSignatures);

    // Write a header jar with the desugared APIs.
    AppView<?> appView = AppView.createForD8(AppInfo.createInitialAppInfo(builder.build()));
    CfApplicationWriter writer =
        new CfApplicationWriter(
            appView,
            options.getMarker(Tool.L8),
            GraphLens.getIdentityLens(),
            NamingLens.getIdentityLens(),
            null);
    ClassFileConsumer consumer =
        new ClassFileConsumer.ArchiveConsumer(
            lintFile(compilationApiLevel, minApiLevel, FileUtils.JAR_EXTENSION));
    writer.write(consumer);
    consumer.finished(options.reporter);
  }

  private void generateLintFiles(
      AndroidApiLevel compilationApiLevel,
      Predicate<AndroidApiLevel> generateForThisMinApiLevel,
      BiPredicate<AndroidApiLevel, DexEncodedMethod> supportedForMinApiLevel)
      throws Exception {
    System.out.print("  - generating for min API:");
    for (AndroidApiLevel minApiLevel : AndroidApiLevel.values()) {
      if (!generateForThisMinApiLevel.test(minApiLevel)) {
        continue;
      }

      System.out.print(" " + minApiLevel);

      SupportedMethods supportedMethods =
          collectSupportedMethods(
              compilationApiLevel, (method -> supportedForMinApiLevel.test(minApiLevel, method)));
      writeLintFiles(compilationApiLevel, minApiLevel, supportedMethods);
    }
    System.out.println();
  }

  private void run() throws Exception {
    // Run over all the API levels that the desugared library can be compiled with.
    for (int apiLevel = AndroidApiLevel.LATEST.getLevel();
        apiLevel >= desugaredLibraryConfiguration.getRequiredCompilationApiLevel().getLevel();
        apiLevel--) {
      System.out.println("Generating lint files for compile API " + apiLevel);
      run(apiLevel);
    }
  }

  public void run(int apiLevel) throws Exception {
    generateLintFiles(
        AndroidApiLevel.getAndroidApiLevel(apiLevel),
        minApiLevel -> minApiLevel == AndroidApiLevel.L || minApiLevel == AndroidApiLevel.B,
        (minApiLevel, method) -> {
          assert minApiLevel == AndroidApiLevel.L || minApiLevel == AndroidApiLevel.B;
          if (minApiLevel == AndroidApiLevel.L) {
            return true;
          }
          assert minApiLevel == AndroidApiLevel.B;
          return !parallelMethods.contains(method.method);
        });
  }

  private static class StringBuilderWithIndent {
    String NL = System.lineSeparator();
    StringBuilder builder = new StringBuilder();
    String indent = "";

    StringBuilderWithIndent() {}

    StringBuilderWithIndent indent(String indent) {
      this.indent = indent;
      return this;
    }

    StringBuilderWithIndent appendLine(String line) {
      builder.append(indent);
      builder.append(line);
      builder.append(NL);
      return this;
    }

    StringBuilderWithIndent emptyLine() {
      builder.append(NL);
      return this;
    }

    @Override
    public String toString() {
      return builder.toString();
    }
  }

  private abstract static class SourceBuilder<B extends SourceBuilder> {

    protected final DexClass clazz;
    protected final boolean newClass;
    protected List<DexEncodedField> fields = new ArrayList<>();
    protected List<DexEncodedMethod> constructors = new ArrayList<>();
    protected List<DexEncodedMethod> methods = new ArrayList<>();

    String className;
    String packageName;

    private SourceBuilder(DexClass clazz, boolean newClass) {
      this.clazz = clazz;
      this.newClass = newClass;
      this.className = clazz.type.toSourceString();
      int index = this.className.lastIndexOf('.');
      this.packageName = index > 0 ? this.className.substring(0, index) : "";
    }

    public abstract B self();

    private B addField(DexEncodedField field) {
      fields.add(field);
      return self();
    }

    private B addMethod(DexEncodedMethod method) {
      assert !method.isClassInitializer();
      if (method.isInitializer()) {
        constructors.add(method);
      } else {
        methods.add(method);
      }
      return self();
    }

    protected String typeInPackage(String typeName, String packageName) {
      if (typeName.startsWith(packageName)
          && typeName.length() > packageName.length()
          && typeName.charAt(packageName.length()) == '.'
          && typeName.indexOf('.', packageName.length() + 1) == -1) {
        return typeName.substring(packageName.length() + 1);
      }
      return null;
    }

    protected String typeInPackage(String typeName) {
      String result = typeInPackage(typeName, packageName);
      if (result == null) {
        result = typeInPackage(typeName, "java.lang");
      }
      if (result == null) {
        result = typeName;
      }
      return result.replace('$', '.');
    }

    protected String typeInPackage(DexType type) {
      if (type.isPrimitiveType()) {
        return type.toSourceString();
      }
      return typeInPackage(type.toSourceString());
    }

    protected String accessFlags(ClassAccessFlags accessFlags) {
      List<String> flags = new ArrayList<>();
      if (accessFlags.isPublic()) {
        flags.add("public");
      }
      if (accessFlags.isProtected()) {
        flags.add("protected");
      }
      if (accessFlags.isPrivate()) {
        assert false;
        flags.add("private");
      }
      if (accessFlags.isPackagePrivate()) {
        assert false;
        flags.add("/* package */");
      }
      if (accessFlags.isAbstract() && !accessFlags.isInterface()) {
        flags.add("abstract");
      }
      if (accessFlags.isStatic()) {
        flags.add("static");
      }
      if (accessFlags.isFinal()) {
        flags.add("final");
      }
      return String.join(" ", flags);
    }

    protected String accessFlags(FieldAccessFlags accessFlags) {
      List<String> flags = new ArrayList<>();
      if (accessFlags.isPublic()) {
        flags.add("public");
      }
      if (accessFlags.isProtected()) {
        flags.add("protected");
      }
      if (accessFlags.isPrivate()) {
        assert false;
        flags.add("private");
      }
      if (accessFlags.isPackagePrivate()) {
        assert false;
        flags.add("/* package */");
      }
      if (accessFlags.isStatic()) {
        flags.add("static");
      }
      if (accessFlags.isFinal()) {
        flags.add("final");
      }
      return String.join(" ", flags);
    }

    protected String accessFlags(MethodAccessFlags accessFlags) {
      List<String> flags = new ArrayList<>();
      if (accessFlags.isPublic()) {
        flags.add("public");
      }
      if (accessFlags.isProtected()) {
        flags.add("protected");
      }
      if (accessFlags.isPrivate()) {
        assert false;
        flags.add("private");
      }
      if (accessFlags.isPackagePrivate()) {
        assert false;
        flags.add("/* package */");
      }
      if (accessFlags.isAbstract()) {
        flags.add("abstract");
      }
      if (accessFlags.isStatic()) {
        flags.add("static");
      }
      if (accessFlags.isFinal()) {
        flags.add("final");
      }
      return String.join(" ", flags);
    }

    public String arguments(DexEncodedMethod method) {
      DexProto proto = method.method.proto;
      StringBuilder argsBuilder = new StringBuilder();
      boolean firstArg = true;
      int argIndex = method.isVirtualMethod() || method.accessFlags.isConstructor() ? 1 : 0;
      int argNumber = 0;
      argsBuilder.append("(");
      for (DexType type : proto.parameters.values) {
        if (!firstArg) {
          argsBuilder.append(", ");
        }
        if (method.hasCode()) {
          String name = "p" + argNumber;
          for (LocalVariableInfo localVariable : method.getCode().asCfCode().getLocalVariables()) {
            if (localVariable.getIndex() == argIndex) {
              assert !localVariable.getLocal().name.toString().equals("this");
              name = localVariable.getLocal().name.toString();
            }
          }
          argsBuilder.append(typeInPackage(type)).append(" ").append(name);
        } else {
          argsBuilder.append(typeInPackage(type)).append(" p").append(argNumber);
        }
        firstArg = false;
        argIndex += type.isWideType() ? 2 : 1;
        argNumber++;
      }
      argsBuilder.append(")");
      return argsBuilder.toString();
    }
  }

  private static class HTMLBuilder extends StringBuilderWithIndent {
    private String indent = "";

    private void increaseIndent() {
      indent += "  ";
      indent(indent);
    }

    private void decreaseIndent() {
      indent = indent.substring(0, indent.length() - 2);
      indent(indent);
    }

    HTMLBuilder appendTdCode(String s) {
      appendLine("<td><code>" + s + "</code></td>");
      return this;
    }

    HTMLBuilder appendTdP(String s) {
      appendLine("<td><p>" + s + "</p></td>");
      return this;
    }

    HTMLBuilder appendLiCode(String s) {
      appendLine("<li><code>" + s + "</code></li>");
      return this;
    }

    HTMLBuilder start(String tag) {
      appendLine("<" + tag + ">");
      increaseIndent();
      return this;
    }

    HTMLBuilder end(String tag) {
      decreaseIndent();
      appendLine("</" + tag + ">");
      return this;
    }
  }

  public static class HTMLSourceBuilder extends SourceBuilder<HTMLSourceBuilder> {
    private final Set<DexMethod> parallelMethods;

    public HTMLSourceBuilder(DexClass clazz, boolean newClass, Set<DexMethod> parallelMethods) {
      super(clazz, newClass);
      this.parallelMethods = parallelMethods;
    }

    @Override
    public HTMLSourceBuilder self() {
      return this;
    }

    @Override
    public String toString() {
      HTMLBuilder builder = new HTMLBuilder();
      builder.start("tr");
      if (packageName.length() > 0) {
        builder.appendTdCode(packageName);
      }
      builder.appendTdCode(typeInPackage(className));
      builder.start("td").start("ul");
      if (!fields.isEmpty()) {
        assert newClass; // Currently no fields are added to existing classes.
        for (DexEncodedField field : fields) {
          builder.appendLiCode(
              accessFlags(field.accessFlags)
                  + " "
                  + typeInPackage(field.field.type)
                  + " "
                  + field.field.name);
        }
      }
      if (!constructors.isEmpty()) {
        for (DexEncodedMethod constructor : constructors) {
          builder.appendLiCode(
              accessFlags(constructor.accessFlags)
                  + " "
                  + typeInPackage(className)
                  + arguments(constructor));
        }
      }
      List<String> parallelM = new ArrayList<>();
      if (!methods.isEmpty()) {
        for (DexEncodedMethod method : methods) {
          builder.appendLiCode(
              accessFlags(method.accessFlags)
                  + " "
                  + typeInPackage(method.method.proto.returnType)
                  + " "
                  + method.method.name
                  + arguments(method));
          if (parallelMethods.contains(method.method)) {
            parallelM.add(method.method.name.toString());
          }
        }
      }
      builder.end("ul").end("td");
      StringBuilder commentBuilder = new StringBuilder();
      if (newClass) {
        commentBuilder.append("Fully implemented class.");
      } else {
        commentBuilder.append("Additional methods on existing class.");
      }
      if (!parallelM.isEmpty()) {
        commentBuilder.append(newClass ? "" : "<br>");
        if (parallelM.size() == 1) {
          commentBuilder
              .append("The method <code>")
              .append(parallelM.get(0))
              .append("</code> is only supported from API level 21.");
        } else {
          commentBuilder.append("The following methods are only supported from API level 21:<br>");
          for (int i = 0; i < parallelM.size(); i++) {
            commentBuilder.append("<code>").append(parallelM.get(i)).append("</code><br>");
          }
        }
      }
      builder.appendTdP(commentBuilder.toString());
      builder.end("tr");
      return builder.toString();
    }
  }

  private void generateClassHTML(
      PrintStream ps,
      DexClass clazz,
      boolean newClass,
      Predicate<DexEncodedField> fieldsFilter,
      Predicate<DexEncodedMethod> methodsFilter) {
    SourceBuilder builder = new HTMLSourceBuilder(clazz, newClass, parallelMethods);
    StreamSupport.stream(clazz.fields().spliterator(), false)
        .filter(fieldsFilter)
        .filter(field -> field.accessFlags.isPublic() || field.accessFlags.isProtected())
        .sorted(Comparator.comparing(DexEncodedField::toSourceString))
        .forEach(builder::addField);
    StreamSupport.stream(clazz.methods().spliterator(), false)
        .filter(methodsFilter)
        .filter(
            method ->
                (method.accessFlags.isPublic() || method.accessFlags.isProtected())
                    && !method.accessFlags.isBridge())
        .sorted(Comparator.comparing(DexEncodedMethod::toSourceString))
        .forEach(builder::addMethod);
    ps.println(builder);
  }

  private void generateDesugaredLibraryApisDocumetation() throws Exception {
    PrintStream ps = new PrintStream(Files.newOutputStream(outputDirectory.resolve("apis.html")));
    // Full classes added.
    SupportedMethods supportedMethods = collectSupportedMethods(AndroidApiLevel.Q, x -> true);
    supportedMethods.classesWithAllMethodsSupported.stream()
        .sorted(Comparator.comparing(clazz -> clazz.type.toSourceString()))
        .forEach(clazz -> generateClassHTML(ps, clazz, true, field -> true, method -> true));

    // Methods added to existing classes.
    supportedMethods.supportedMethods.keySet().stream()
        .filter(clazz -> !supportedMethods.classesWithAllMethodsSupported.contains(clazz))
        .sorted(Comparator.comparing(clazz -> clazz.type.toSourceString()))
        .forEach(
            clazz ->
                generateClassHTML(
                    ps,
                    clazz,
                    false,
                    field -> false,
                    method -> supportedMethods.supportedMethods.get(clazz).contains(method)));
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 3) {
      new GenerateLintFiles(args[0], args[1], args[2]).run();
      return;
    }
    if (args.length == 4 && args[0].equals("--generate-api-docs")) {
      new GenerateLintFiles(args[1], args[2], args[3]).generateDesugaredLibraryApisDocumetation();
      return;
    }
    System.out.println(
        "Usage: GenerateLineFiles [--generate-api-docs] "
            + "<desugar configuration> <desugar implementation> <output directory>");
    System.exit(1);
  }
}
