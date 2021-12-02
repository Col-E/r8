// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class StartupConfiguration {

  List<DexType> startupClasses;

  public StartupConfiguration(List<DexType> startupClasses) {
    this.startupClasses = startupClasses;
  }

  public static StartupConfiguration createStartupConfiguration(
      DexItemFactory dexItemFactory, Reporter reporter) {
    String propertyValue = System.getProperty("com.android.tools.r8.startupclassdescriptors");
    if (propertyValue == null) {
      return null;
    }

    List<String> startupClassDescriptors;
    try {
      startupClassDescriptors = FileUtils.readAllLines(Paths.get(propertyValue));
    } catch (IOException e) {
      throw reporter.fatalError(new ExceptionDiagnostic(e));
    }

    if (startupClassDescriptors.isEmpty()) {
      return null;
    }

    List<DexType> startupClasses = new ArrayList<>(startupClassDescriptors.size());
    for (String startupClassDescriptor : startupClassDescriptors) {
      if (startupClassDescriptor.trim().isEmpty()) {
        continue;
      }
      if (!DescriptorUtils.isClassDescriptor(startupClassDescriptor)) {
        reporter.warning(
            new StringDiagnostic(
                "Invalid class descriptor for startup class: " + startupClassDescriptor));
        continue;
      }
      DexType startupClass = dexItemFactory.createType(startupClassDescriptor);
      startupClasses.add(startupClass);
    }
    return new StartupConfiguration(startupClasses);
  }

  public boolean hasStartupClasses() {
    return !startupClasses.isEmpty();
  }

  public List<DexType> getStartupClasses() {
    return startupClasses;
  }
}
