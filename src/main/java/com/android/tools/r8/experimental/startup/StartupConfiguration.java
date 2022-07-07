// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
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

  private final List<StartupClass<DexType, DexMethod>> startupClasses;

  public StartupConfiguration(List<StartupClass<DexType, DexMethod>> startupClasses) {
    this.startupClasses = startupClasses;
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
    List<StartupClass<DexType, DexMethod>> startupClasses = new ArrayList<>();
    StartupConfigurationParser.createDexParser(dexItemFactory)
        .parseLines(
            startupDescriptors,
            startupClasses::add,
            // TODO(b/238173796): Startup methods should be added as startup methods.
            startupMethod ->
                startupClasses.add(
                    StartupClass.dexBuilder()
                        .setClassReference(startupMethod.getReference().getHolderType())
                        .setFlags(startupMethod.getFlags())
                        .build()),
            error ->
                reporter.warning(
                    new StringDiagnostic(
                        "Invalid descriptor for startup class or method: " + error)));
    return new StartupConfiguration(startupClasses);
  }

  public boolean hasStartupClasses() {
    return !startupClasses.isEmpty();
  }

  public List<StartupClass<DexType, DexMethod>> getStartupClasses() {
    return startupClasses;
  }

  public static class Builder {

    private final ImmutableList.Builder<StartupClass<DexType, DexMethod>> startupClassesBuilder =
        ImmutableList.builder();

    public Builder addStartupItem(StartupItem<DexType, DexMethod, ?> startupItem) {
      if (startupItem.isStartupClass()) {
        return addStartupClass(startupItem.asStartupClass());
      } else {
        assert startupItem.isStartupMethod();
        return addStartupMethod(startupItem.asStartupMethod());
      }
    }

    public Builder addStartupClass(StartupClass<DexType, DexMethod> startupClass) {
      this.startupClassesBuilder.add(startupClass);
      return this;
    }

    public Builder addStartupMethod(StartupMethod<DexType, DexMethod> startupMethod) {
      // TODO(b/238173796): Startup methods should be added as startup methods.
      return addStartupClass(
          StartupClass.dexBuilder()
              .setFlags(startupMethod.getFlags())
              .setClassReference(startupMethod.getReference().getHolderType())
              .build());
    }

    public Builder apply(Consumer<Builder> consumer) {
      consumer.accept(this);
      return this;
    }

    public StartupConfiguration build() {
      return new StartupConfiguration(startupClassesBuilder.build());
    }
  }
}
