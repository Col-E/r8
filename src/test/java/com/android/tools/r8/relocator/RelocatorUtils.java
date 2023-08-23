// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundFieldSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RelocatorUtils {

  public static void runRelocator(
      Path input, Map<String, String> mapping, Path output, boolean external)
      throws CompilationFailedException {
    if (external) {
      List<String> args = new ArrayList<>();
      args.add("--input");
      args.add(input.toString());
      args.add("--output");
      args.add(output.toString());
      mapping.forEach(
          (key, value) -> {
            args.add("--map");
            args.add(key + "->" + value);
          });
      RelocatorCommandLine.run(args.toArray(new String[0]));
    } else {
      RelocatorCommand.Builder builder =
          RelocatorCommand.builder().addProgramFiles(input).setOutputPath(output);
      mapping.forEach(
          (key, value) -> {
            if (DescriptorUtils.isClassDescriptor(key)) {
              builder.addClassMapping(
                  Reference.classFromDescriptor(key), Reference.classFromDescriptor(value));
            } else {
              builder.addPackageMapping(
                  Reference.packageFromString(key), Reference.packageFromString(value));
            }
          });
      Relocator.run(builder.build());
    }
  }

  public static void inspectAllClassesRelocated(
      Path original, Path relocated, String originalPrefix, String newPrefix) throws IOException {
    CodeInspector originalInspector = new CodeInspector(original);
    CodeInspector relocatedInspector = new CodeInspector(relocated);
    for (FoundClassSubject clazz : originalInspector.allClasses()) {
      if (originalPrefix.isEmpty()
          || clazz
              .getFinalName()
              .startsWith(originalPrefix + DescriptorUtils.JAVA_PACKAGE_SEPARATOR)) {
        String relocatedName = newPrefix + clazz.getFinalName().substring(originalPrefix.length());
        ClassSubject relocatedClass = relocatedInspector.clazz(relocatedName);
        assertThat(relocatedClass, isPresent());
      }
    }
  }

  public static void inspectAllSignaturesNotContainingString(Path relocated, String originalPrefix)
      throws IOException {
    CodeInspector relocatedInspector = new CodeInspector(relocated);
    for (FoundClassSubject clazz : relocatedInspector.allClasses()) {
      assertThat(clazz.getFinalSignatureAttribute(), not(containsString(originalPrefix)));
      for (FoundMethodSubject method : clazz.allMethods()) {
        assertThat(method.getJvmMethodSignatureAsString(), not(containsString(originalPrefix)));
      }
      for (FoundFieldSubject field : clazz.allFields()) {
        assertThat(field.getJvmFieldSignatureAsString(), not(containsString(originalPrefix)));
      }
    }
  }
}
