// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.graph;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DesugarGraphUtils {

  public static Origin addClassWithOrigin(Class<?> clazz, D8TestBuilder builder)
      throws IOException {
    return addClassWithOrigin(clazz.getTypeName(), ToolHelper.getClassAsBytes(clazz), builder);
  }

  public static Origin addClassWithOrigin(String name, byte[] bytes, D8TestBuilder builder) {
    Origin origin = makeOrigin(name);
    builder.getBuilder().addClassProgramData(bytes, origin);
    return origin;
  }

  private final Map<String, Origin> origins = new HashMap<>();

  private static Origin makeOrigin(String name) {
    return new Origin(Origin.root()) {
      @Override
      public String part() {
        return name;
      }
    };
  }

  public Origin origin(String typeName) {
    return origins.computeIfAbsent(typeName, DesugarGraphUtils::makeOrigin);
  }

  public Origin origin(Class<?> clazz) {
    return origin(clazz.getTypeName());
  }

  public void addProgramClasses(D8TestBuilder builder, Class<?>... classes) throws IOException {
    for (Class<?> clazz : classes) {
      builder.getBuilder().addClassProgramData(ToolHelper.getClassAsBytes(clazz), origin(clazz));
    }
  }

  public void addClasspathClass(D8TestBuilder builder, Class<?>... classes) throws IOException {
    Map<String, ProgramResource> map = new HashMap<>();
    for (Class<?> clazz : classes) {
      String descriptor = DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName());
      map.put(
          descriptor,
          ProgramResource.fromBytes(
              origin(clazz),
              Kind.CF,
              ToolHelper.getClassAsBytes(clazz),
              Collections.singleton(descriptor)));
    }
    builder
        .getBuilder()
        .addClasspathResourceProvider(
            new ClassFileResourceProvider() {
              @Override
              public Set<String> getClassDescriptors() {
                return map.keySet();
              }

              @Override
              public ProgramResource getProgramResource(String descriptor) {
                return map.get(descriptor);
              }
            });
  }
}
