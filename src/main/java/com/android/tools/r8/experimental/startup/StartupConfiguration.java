// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StartupConfiguration {

  private final List<StartupClass<DexType>> startupClasses;
  private final List<DexMethod> startupMethods;

  public StartupConfiguration(
      List<StartupClass<DexType>> startupClasses, List<DexMethod> startupMethods) {
    this.startupClasses = startupClasses;
    this.startupMethods = startupMethods;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Parses the supplied startup configuration, if any. The startup configuration is a list of class
   * and method descriptors.
   *
   * <p>Example:
   *
   * <pre>
   * Landroidx/compose/runtime/ComposerImpl;->updateValue(Ljava/lang/Object;)V
   * Landroidx/compose/runtime/ComposerImpl;->updatedNodeCount(I)I
   * Landroidx/compose/runtime/ComposerImpl;->validateNodeExpected()V
   * Landroidx/compose/runtime/CompositionImpl;->applyChanges()V
   * Landroidx/compose/runtime/ComposerKt;->findLocation(Ljava/util/List;I)I
   * Landroidx/compose/runtime/ComposerImpl;
   * </pre>
   */
  public static StartupConfiguration createStartupConfiguration(
      DexItemFactory dexItemFactory, Reporter reporter) {
    String propertyValue = System.getProperty("com.android.tools.r8.startup.config");
    return propertyValue != null
        ? createStartupConfigurationFromFile(dexItemFactory, reporter, Paths.get(propertyValue))
        : null;
  }

  public static StartupConfiguration createStartupConfigurationFromFile(
      DexItemFactory dexItemFactory, Reporter reporter, Path path) {
    reporter.warning("Use of startupconfig is experimental");

    List<String> startupDescriptors;
    try {
      startupDescriptors = FileUtils.readAllLines(path);
    } catch (IOException e) {
      throw reporter.fatalError(new ExceptionDiagnostic(e));
    }

    if (startupDescriptors.isEmpty()) {
      return null;
    }

    return createStartupConfigurationFromLines(dexItemFactory, reporter, startupDescriptors);
  }

  public static StartupConfiguration createStartupConfigurationFromLines(
      DexItemFactory dexItemFactory, Reporter reporter, List<String> startupDescriptors) {
    List<StartupClass<DexType>> startupClasses = new ArrayList<>();
    List<DexMethod> startupMethods = new ArrayList<>();
    for (String startupDescriptor : startupDescriptors) {
      if (startupDescriptor.isEmpty()) {
        continue;
      }
      StartupClass.Builder<DexType> startupClassBuilder = StartupClass.builder();
      startupDescriptor = parseSyntheticFlag(startupDescriptor, startupClassBuilder);
      int methodNameStartIndex = getMethodNameStartIndex(startupDescriptor);
      if (methodNameStartIndex >= 0) {
        DexMethod startupMethod =
            parseStartupMethodDescriptor(startupDescriptor, methodNameStartIndex, dexItemFactory);
        if (startupMethod != null) {
          startupClasses.add(
              startupClassBuilder.setReference(startupMethod.getHolderType()).build());
          startupMethods.add(startupMethod);
        } else {
          reporter.warning(
              new StringDiagnostic("Invalid descriptor for startup method: " + startupDescriptor));
        }
      } else {
        DexType startupClass = parseStartupClassDescriptor(startupDescriptor, dexItemFactory);
        if (startupClass != null) {
          startupClasses.add(startupClassBuilder.setReference(startupClass).build());
        } else {
          reporter.warning(
              new StringDiagnostic("Invalid descriptor for startup class: " + startupDescriptor));
        }
      }
    }
    return new StartupConfiguration(startupClasses, startupMethods);
  }

  public static String parseSyntheticFlag(
      String startupDescriptor, StartupClass.Builder<?> startupClassBuilder) {
    if (!startupDescriptor.isEmpty() && startupDescriptor.charAt(0) == 'S') {
      startupClassBuilder.setSynthetic();
      return startupDescriptor.substring(1);
    }
    return startupDescriptor;
  }

  private static int getMethodNameStartIndex(String startupDescriptor) {
    int arrowIndex = startupDescriptor.indexOf("->");
    return arrowIndex >= 0 ? arrowIndex + 2 : arrowIndex;
  }

  private static DexType parseStartupClassDescriptor(
      String startupClassDescriptor, DexItemFactory dexItemFactory) {
    if (DescriptorUtils.isClassDescriptor(startupClassDescriptor)) {
      return dexItemFactory.createType(startupClassDescriptor);
    } else {
      return null;
    }
  }

  private static DexMethod parseStartupMethodDescriptor(
      String startupMethodDescriptor, int methodNameStartIndex, DexItemFactory dexItemFactory) {
    String classDescriptor = startupMethodDescriptor.substring(0, methodNameStartIndex - 2);
    DexType classType = parseStartupClassDescriptor(classDescriptor, dexItemFactory);
    if (classType == null) {
      return null;
    }

    String protoWithNameDescriptor = startupMethodDescriptor.substring(methodNameStartIndex);
    int methodNameEndIndex = protoWithNameDescriptor.indexOf('(');
    if (methodNameEndIndex <= 0) {
      return null;
    }
    String methodName = protoWithNameDescriptor.substring(0, methodNameEndIndex);

    String protoDescriptor = protoWithNameDescriptor.substring(methodNameEndIndex);
    DexProto proto = parseStartupMethodProto(protoDescriptor, dexItemFactory);
    return dexItemFactory.createMethod(classType, proto, methodName);
  }

  private static DexProto parseStartupMethodProto(
      String protoDescriptor, DexItemFactory dexItemFactory) {
    List<DexType> parameterTypes = new ArrayList<>();
    for (String parameterTypeDescriptor :
        DescriptorUtils.getArgumentTypeDescriptors(protoDescriptor)) {
      parameterTypes.add(dexItemFactory.createType(parameterTypeDescriptor));
    }
    String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(protoDescriptor);
    DexType returnType = dexItemFactory.createType(returnTypeDescriptor);
    return dexItemFactory.createProto(returnType, parameterTypes);
  }

  public boolean hasStartupClasses() {
    return !startupClasses.isEmpty();
  }

  public List<StartupClass<DexType>> getStartupClasses() {
    return startupClasses;
  }

  public static class Builder {

    private final ImmutableList.Builder<StartupClass<DexType>> startupClassesBuilder =
        ImmutableList.builder();
    private final ImmutableList.Builder<DexMethod> startupMethodsBuilder = ImmutableList.builder();

    public Builder addStartupClass(StartupClass<DexType> startupClass) {
      this.startupClassesBuilder.add(startupClass);
      return this;
    }

    public Builder apply(Consumer<Builder> consumer) {
      consumer.accept(this);
      return this;
    }

    public StartupConfiguration build() {
      return new StartupConfiguration(startupClassesBuilder.build(), startupMethodsBuilder.build());
    }
  }
}
