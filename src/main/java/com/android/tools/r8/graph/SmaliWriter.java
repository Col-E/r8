// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class SmaliWriter extends DexByteCodeWriter {

  private final boolean writeCode;

  public SmaliWriter(DexApplication application, InternalOptions options, boolean writeCode) {
    super(application, options);
    this.writeCode = writeCode;
  }

  /** Return smali source for the application code. */
  public static String smali(AndroidApp application, InternalOptions options) {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    try (PrintStream ps = new PrintStream(os)) {
      DexApplication dexApplication =
          new ApplicationReader(application, options, Timing.empty()).read();
      SmaliWriter writer = new SmaliWriter(dexApplication, options, true);
      writer.write(ps);
    } catch (IOException e) {
      throw new CompilationError("Failed to generate smali sting", e);
    }
    return new String(os.toByteArray(), StandardCharsets.UTF_8);
  }

  @Override
  String getFileEnding() {
    return ".smali";
  }

  @Override
  void writeClassHeader(DexProgramClass clazz, PrintStream ps) {
    ps.append(".class ");
    ps.append(clazz.accessFlags.toSmaliString());
    ps.append(" ");
    ps.append(clazz.type.toSmaliString());
    ps.append('\n');

    if (clazz.type != application.dexItemFactory.objectType) {
      ps.append(".super ");
      ps.append(clazz.superType.toSmaliString());
      ps.append('\n');
    }
    ps.append('\n');

    if (!clazz.getInterfaces().isEmpty()) {
      ps.append("# interfaces").append('\n');
      for (DexType iface : clazz.interfaces.values) {
        ps.append(".implements ");
        ps.append(iface.toSmaliString());
        ps.append('\n');
      }
      ps.append('\n');
    }
    ps.append('\n');
  }

  @Override
  void writeClassFooter(DexProgramClass clazz, PrintStream ps) {
    ps.append("# End of class ");
    ps.append(clazz.type.toSmaliString());
    ps.append("\n");
    ps.append("\n");
  }

  @Override
  void writeMethod(ProgramMethod method, PrintStream ps) {
    ps.append("\n");
    ps.append(method.getDefinition().toSmaliString(application.getProguardMap(), writeCode));
    ps.append("\n");
  }

  @Override
  void writeInstanceFieldsHeader(DexProgramClass clazz, PrintStream ps) {
    if (clazz.hasStaticFields()) {
      ps.append('\n');
    }
    if (clazz.hasInstanceFields()) {
      ps.append("# instance fields").append('\n');
    }
  }

  @Override
  void writeStaticFieldsHeader(DexProgramClass clazz, PrintStream ps) {
    if (clazz.hasStaticFields()) {
      ps.append("# static fields").append('\n');
    }
  }

  @Override
  void writeField(DexEncodedField field, PrintStream ps) {
    ps.append(field.toSmaliString(application.getProguardMap())).append('\n').append('\n');
  }
}
