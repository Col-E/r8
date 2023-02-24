// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DumpCoreLibUsage {
  public boolean usesTypeFromPackage(String pkg, DexField field) {
    return field.type.toSourceString().contains(pkg);
  }

  public boolean usesTypeFromPackage(String pkg, DexMethod method) {
    List<DexType> types = new ArrayList<>();
    types.add(method.proto.returnType);
    types.addAll(Arrays.asList(method.proto.parameters.values));
    for (DexType type : types) {
      if (type.toSourceString().contains(pkg)) {
        return true;
      }
    }
    return false;
  }

  private boolean writeHeaderIfNeeded(DexType type, boolean headerWritten) {
    if (!headerWritten) {
      System.out.println("class " + type.toSourceString() + " {");
    }
    return true;
  }

  private void writeFooterIfNeeded(boolean headerWritten) {
    if (headerWritten) {
      System.out.println("}");
      System.out.println();
    }
  }

  private Set<DexReference> checkPackage(
      String pkg, AndroidApiLevel apiLevel, Set<DexReference> filter, DexItemFactory factory)
      throws IOException {
    AndroidApp input =
        AndroidApp.builder().addLibraryFiles(ToolHelper.getAndroidJar(apiLevel)).build();
    DirectMappedDexApplication dexApplication =
        new ApplicationReader(input, new InternalOptions(factory, new Reporter()), Timing.empty())
            .read()
            .toDirect();

    Set<DexReference> found = Sets.newIdentityHashSet();
    found.addAll(filter);

    for (DexLibraryClass libraryClass : dexApplication.libraryClasses()) {
      // Skip methods in the package itself.
      if (libraryClass.type.toSourceString().startsWith(pkg)) {
        continue;
      }

      boolean headerWritten = false;

      for (DexEncodedField field : libraryClass.fields()) {
        if (!field.accessFlags.isPublic()) {
          continue;
        }
        if (filter.contains(field.getReference())) {
          continue;
        }
        if (usesTypeFromPackage(pkg, field.getReference())) {
          headerWritten = writeHeaderIfNeeded(libraryClass.type, headerWritten);
          System.out.println("  " + field.toSourceString());
          found.add(field.getReference());
        }
      }

      for (DexEncodedMethod method : libraryClass.methods()) {
        if (!method.isPublicMethod()) {
          continue;
        }
        // Class java.util.concurrent.CompletableFuture have most methods duplicated with a
        // synthetic bridge that only differs on return type (the bridge has return type
        // CompletionStage, whereas the non bridge has return type CompletableFuture.
        if (method.isSyntheticMethod()) {
          continue;
        }
        if (filter.contains(method.getReference())) {
          continue;
        }
        if (usesTypeFromPackage(pkg, method.getReference())) {
          headerWritten = writeHeaderIfNeeded(libraryClass.type, headerWritten);
          System.out.println("  " + method.getReference().toSourceStringWithoutHolder());
          found.add(method.getReference());
        }
      }

      writeFooterIfNeeded(headerWritten);
    }
    return found;
  }

  public static void main(String[] args) throws Exception {
    DexItemFactory factory = new DexItemFactory();
    for (String pkg : new String[] {"java.time", "java.util.function", "java.util.stream"}) {
      long prevMethods = 0;
      long prevFields = 0;
      Set<DexReference> found = Collections.emptySet();
      for (AndroidApiLevel apiLevel :
          new AndroidApiLevel[] {
            AndroidApiLevel.M,
            AndroidApiLevel.N,
            AndroidApiLevel.N_MR1,
            AndroidApiLevel.O,
            AndroidApiLevel.P,
            AndroidApiLevel.Q
          }) {
        String header = "Usage of package " + pkg + " on " + apiLevel;
        System.out.println(header);
        System.out.println(new String(new char[header.length()]).replace("\0", "-"));
        System.out.println();
        found = new DumpCoreLibUsage().checkPackage(pkg, apiLevel, found, factory);

        long methods = found.stream().filter(DexReference::isDexMethod).count();
        long fields = found.stream().filter(DexReference::isDexField).count();
        System.out.println(
            "Total methods: " + methods + " (" + (methods - prevMethods) + " added)");
        System.out.println("Total fields: " + fields + " (" + (fields - prevFields) + " added)");
        System.out.println();
        prevMethods = methods;
        prevFields = fields;
      }
    }
  }
}
