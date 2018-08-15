// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.JarClassFileReader.ReparseContext;
import com.android.tools.r8.graph.JarCode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.function.BiFunction;

// Source code representing code of a method generated based on a template.
public abstract class TemplateMethodCode extends JarCode {
  private final String templateMethodName;
  private final String templateMethodDesc;

  protected TemplateMethodCode(
      InternalOptions options, DexMethod method, String name, String desc) {
    super(method, Origin.unknown(), new ReparseContext(), new JarApplicationReader(options));
    this.templateMethodName = name;
    this.templateMethodDesc = desc;
  }

  public void setUpContext(DexProgramClass owner) {
    assert context != null;
    context.owner = owner;
    context.classCache = getClassAsBytes();
  }

  @Override
  protected BiFunction<String, String, JarCode> createCodeLocator(ReparseContext context) {
    return this::getCodeOrNull;
  }

  private JarCode getCodeOrNull(String name, String desc) {
    return name.equals(templateMethodName) && desc.equals(templateMethodDesc) ? this : null;
  }

  private byte[] getClassAsBytes() {
    Class<? extends TemplateMethodCode> clazz = this.getClass();
    String s = clazz.getSimpleName() + ".class";
    Class outer = clazz.getEnclosingClass();
    while (outer != null) {
      s = outer.getSimpleName() + '$' + s;
      outer = outer.getEnclosingClass();
    }
    try {
      return ByteStreams.toByteArray(clazz.getResourceAsStream(s));
    } catch (IOException e) {
      throw new Unreachable();
    }
  }
}
