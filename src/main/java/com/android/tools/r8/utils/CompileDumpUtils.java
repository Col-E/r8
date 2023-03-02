// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.KeepMethodForCompileDump;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

class CompileDumpUtils {

  @KeepMethodForCompileDump
  static StartupProfileProvider createStartupProfileProviderFromDumpFile(Path path) {
    return new StartupProfileProvider() {

      @Override
      public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
        try {
          try (BufferedReader bufferedReader = Files.newBufferedReader(path)) {
            while (bufferedReader.ready()) {
              String rule = bufferedReader.readLine();
              if (rule.charAt(0) == 'S') {
                String classDescriptor = rule.substring(1);
                assert DescriptorUtils.isClassDescriptor(classDescriptor);
                startupProfileBuilder.addSyntheticStartupMethod(
                    syntheticStartupMethodBuilder ->
                        syntheticStartupMethodBuilder.setSyntheticContextReference(
                            Reference.classFromDescriptor(classDescriptor)));
              } else {
                MethodReference methodReference = MethodReferenceUtils.parseSmaliString(rule);
                if (methodReference != null) {
                  startupProfileBuilder.addStartupMethod(
                      startupMethodBuilder ->
                          startupMethodBuilder.setMethodReference(methodReference));
                } else {
                  assert DescriptorUtils.isClassDescriptor(rule);
                  startupProfileBuilder.addStartupClass(
                      startupClassBuilder ->
                          startupClassBuilder.setClassReference(
                              Reference.classFromDescriptor(rule)));
                }
              }
            }
          }
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public Origin getOrigin() {
        return new PathOrigin(path);
      }
    };
  }
}
