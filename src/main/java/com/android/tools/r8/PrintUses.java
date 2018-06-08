// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo.ResolutionResult;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
      "Arguments: <rt.jar> <r8.jar> <sample.jar>\n"
          + "\n"
          + "PrintUses prints the classes, interfaces, methods and fields used by <sample.jar>,\n"
          + "restricted to classes and interfaces in <r8.jar> that are not in <sample.jar>.\n"
          + "<rt.jar> and <r8.jar> should point to libraries used by <sample.jar>.\n"
          + "\n"
          + "The output is in the same format as what is printed when specifying -printseeds in\n"
          + "a ProGuard configuration file. See also the "
          + PrintSeeds.class.getSimpleName()
          + " program in R8.";

  private final Set<String> descriptors;
  private final PrintStream out;
  private Set<DexType> types = Sets.newIdentityHashSet();
  private Map<DexType, Set<DexMethod>> methods = Maps.newIdentityHashMap();
  private Map<DexType, Set<DexField>> fields = Maps.newIdentityHashMap();
  private final DexApplication application;
  private final AppInfoWithSubtyping appInfo;
  private int errors;

  class UseCollector extends UseRegistry {

    @Override
    public boolean registerInvokeVirtual(DexMethod method) {
      DexEncodedMethod target = appInfo.lookupVirtualTarget(method.holder, method);
      if (target != null && target.method != method) {
        addType(method.holder);
        addMethod(target.method);
      } else {
        addMethod(method);
      }
      return false;
    }

    @Override
    public boolean registerInvokeDirect(DexMethod method) {
      addMethod(method);
      return false;
    }

    @Override
    public boolean registerInvokeStatic(DexMethod method) {
      DexEncodedMethod target = appInfo.lookupStaticTarget(method);
      if (target != null && target.method != method) {
        addType(method.holder);
        addMethod(target.method);
      } else {
        addMethod(method);
      }
      return false;
    }

    @Override
    public boolean registerInvokeInterface(DexMethod method) {
      return registerInvokeVirtual(method);
    }

    @Override
    public boolean registerInvokeSuper(DexMethod method) {
      addMethod(method);
      return false;
    }

    @Override
    public boolean registerInstanceFieldWrite(DexField field) {
      addField(field);
      return false;
    }

    @Override
    public boolean registerInstanceFieldRead(DexField field) {
      addField(field);
      return false;
    }

    @Override
    public boolean registerNewInstance(DexType type) {
      addType(type);
      return false;
    }

    @Override
    public boolean registerStaticFieldRead(DexField field) {
      addField(field);
      return false;
    }

    @Override
    public boolean registerStaticFieldWrite(DexField field) {
      addField(field);
      return false;
    }

    @Override
    public boolean registerTypeReference(DexType type) {
      addType(type);
      return false;
    }

    private void addType(DexType type) {
      if (isTargetType(type) && types.add(type)) {
        methods.put(type, Sets.newIdentityHashSet());
        fields.put(type, Sets.newIdentityHashSet());
      }
    }

    private boolean isTargetType(DexType type) {
      return descriptors.contains(type.toDescriptorString());
    }

    private void addField(DexField field) {
      addType(field.type);
      addType(field.clazz);
      Set<DexField> typeFields = fields.get(field.clazz);
      if (typeFields != null) {
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
        typeMethods.add(method);
      }
    }

    private void registerField(DexEncodedField field) {
      registerTypeReference(field.field.type);
    }

    private void registerMethod(DexEncodedMethod method) {
      DexEncodedMethod superTarget = appInfo.lookupSuperTarget(method.method, method.method.holder);
      if (superTarget != null) {
        registerInvokeSuper(superTarget.method);
      }
      for (DexType type : method.method.proto.parameters.values) {
        registerTypeReference(type);
      }
      registerTypeReference(method.method.proto.returnType);
      method.registerCodeReferences(this);
    }

    private void registerSuperType(DexProgramClass clazz, DexType superType) {
      registerTypeReference(superType);
      // If clazz overrides any methods in superType, we should keep those as well.
      clazz.forEachMethod(
          method -> {
            ResolutionResult resolutionResult = appInfo.resolveMethod(superType, method.method);
            for (DexEncodedMethod dexEncodedMethod : resolutionResult.asListOfTargets()) {
              addMethod(dexEncodedMethod.method);
            }
          });
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      System.out.println(USAGE.replace("\n", System.lineSeparator()));
      return;
    }
    AndroidApp.Builder builder = AndroidApp.builder();
    Path rtJar = Paths.get(args[0]);
    builder.addLibraryFile(rtJar);
    Path r8Jar = Paths.get(args[1]);
    builder.addLibraryFile(r8Jar);
    Path sampleJar = Paths.get(args[2]);
    builder.addProgramFile(sampleJar);
    Set<String> descriptors = new HashSet<>(getDescriptors(r8Jar));
    descriptors.removeAll(getDescriptors(sampleJar));
    PrintUses printUses = new PrintUses(descriptors, builder.build(), System.out);
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

  private PrintUses(Set<String> descriptors, AndroidApp inputApp, PrintStream out)
      throws Exception {
    this.descriptors = descriptors;
    this.out = out;
    InternalOptions options = new InternalOptions();
    application =
        new ApplicationReader(inputApp, options, new Timing("PrintUses")).read().toDirect();
    appInfo = new AppInfoWithSubtyping(application);
  }

  private void analyze() {
    UseCollector useCollector = new UseCollector();
    for (DexProgramClass dexProgramClass : application.classes()) {
      useCollector.registerSuperType(dexProgramClass, dexProgramClass.superType);
      for (DexType implementsType : dexProgramClass.interfaces.values) {
        useCollector.registerSuperType(dexProgramClass, implementsType);
      }
      dexProgramClass.forEachMethod(useCollector::registerMethod);
      dexProgramClass.forEachField(useCollector::registerField);
    }
  }

  private void print() {
    List<DexType> types = new ArrayList<>(this.types);
    types.sort(Comparator.comparing(DexType::toSourceString));
    for (DexType type : types) {
      String typeName = type.toSourceString();
      DexClass dexClass = application.definitionFor(type);
      if (dexClass == null) {
        error("Could not find definition for type " + type.toSourceString());
        continue;
      }
      out.println(typeName);
      List<DexMethod> methods = new ArrayList<>(this.methods.get(type));
      List<String> methodDefinitions = new ArrayList<>(methods.size());
      for (DexMethod method : methods) {
        DexEncodedMethod encodedMethod = dexClass.lookupMethod(method);
        if (encodedMethod == null) {
          error("Could not find definition for method " + method.toSourceString());
          continue;
        }
        methodDefinitions.add(getMethodSourceString(encodedMethod));
      }
      methodDefinitions.sort(Comparator.naturalOrder());
      for (String encodedMethod : methodDefinitions) {
        out.println(typeName + ": " + encodedMethod);
      }
      List<DexField> fields = new ArrayList<>(this.fields.get(type));
      fields.sort(Comparator.comparing(DexField::toSourceString));
      for (DexField field : fields) {
        out.println(
            typeName + ": " + field.type.toSourceString() + " " + field.name.toSourceString());
      }
    }
  }

  private void error(String message) {
    out.println("# Error: " + message);
    errors += 1;
  }

  private static String getMethodSourceString(DexEncodedMethod encodedMethod) {
    DexMethod method = encodedMethod.method;
    StringBuilder builder = new StringBuilder();
    if (encodedMethod.accessFlags.isConstructor()) {
      if (encodedMethod.accessFlags.isStatic()) {
        builder.append("<clinit>");
      } else {
        String holderName = method.holder.toSourceString();
        String constructorName = holderName.substring(holderName.lastIndexOf('.') + 1);
        builder.append(constructorName);
      }
    } else {
      builder
          .append(method.proto.returnType.toSourceString())
          .append(" ")
          .append(method.name.toSourceString());
    }
    builder.append("(");
    for (int i = 0; i < method.getArity(); i++) {
      if (i != 0) {
        builder.append(",");
      }
      builder.append(method.proto.parameters.values[i].toSourceString());
    }
    builder.append(")");
    return builder.toString();
  }
}
