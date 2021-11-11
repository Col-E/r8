// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.StreamUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableMap;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.Opcodes;

public class DesugaredLibraryJDK11Undesugarer extends DesugaredLibraryTestBase {

  private static Map<String, String> ownerMap =
      ImmutableMap.of(
          "java/io/DesugarBufferedReader", "java/io/BufferedReader",
          "java/io/DesugarInputStream", "java/io/InputStream");

  public static void main(String[] args) throws Exception {
    setUpDesugaredLibrary();
    undesugaredJar();
  }

  public static Path undesugaredJar() {
    if (!isJDK11DesugaredLibrary()) {
      return ToolHelper.getDesugarJDKLibs();
    }
    Path desugaredLibJDK11Undesugared = Paths.get("build/libs/desugar_jdk_libs_11_undesugared.jar");
    if (Files.exists(desugaredLibJDK11Undesugared)) {
      return desugaredLibJDK11Undesugared;
    }
    OpenOption[] options =
        new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
    try (ZipOutputStream out =
        new ZipOutputStream(
            new BufferedOutputStream(
                Files.newOutputStream(desugaredLibJDK11Undesugared, options)))) {
      new DesugaredLibraryJDK11Undesugarer().undesugar(ToolHelper.getDesugarJDKLibs(), out);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return desugaredLibJDK11Undesugared;
  }

  private void undesugar(Path desugaredLibraryFiles, ZipOutputStream out) throws IOException {
    ZipUtils.iter(
        desugaredLibraryFiles,
        ((entry, input) -> {
          if (!entry.getName().endsWith(".class")) {
            return;
          }
          final byte[] bytes = StreamUtils.StreamToByteArrayClose(input);
          final byte[] rewrittenBytes =
              transformInvoke(entry.getName().substring(0, entry.getName().length() - 6), bytes);
          ZipUtils.writeToZipStream(out, entry.getName(), rewrittenBytes, ZipEntry.STORED);
        }));
  }

  private byte[] transformInvoke(String descriptor, byte[] bytes) {
    return transformer(bytes, Reference.classFromDescriptor(descriptor))
        .addMethodTransformer(getMethodTransformer())
        .transform();
  }

  private MethodTransformer getMethodTransformer() {
    return new MethodTransformer() {
      @Override
      public void visitMethodInsn(
          int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (opcode == Opcodes.INVOKESTATIC) {
          for (String ownerToRewrite : ownerMap.keySet()) {
            if (ownerToRewrite.equals(owner)) {
              super.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  ownerMap.get(owner),
                  name,
                  withoutFirstObjectArg(descriptor),
                  isInterface);
              return;
            }
          }
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }
    };
  }

  private String withoutFirstObjectArg(String descriptor) {
    int i = descriptor.indexOf(";");
    return "(" + descriptor.substring(i + 1);
  }
}
