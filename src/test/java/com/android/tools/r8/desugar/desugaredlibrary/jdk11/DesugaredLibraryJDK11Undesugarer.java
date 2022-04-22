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

  private static final Map<String, String> ownerMap =
      ImmutableMap.<String, String>builder()
          .put("java/util/DesugarTimeZone", "java/util/TimeZone")
          .put("java/lang/DesugarLong", "java/lang/Long")
          .put("java/lang/DesugarInteger", "java/lang/Integer")
          .put("java/lang/DesugarDouble", "java/lang/Double")
          .put("java/util/DesugarArrays", "java/util/Arrays")
          .put("java/lang/DesugarMath", "java/lang/Math")
          .put("java/io/DesugarBufferedReader", "java/io/BufferedReader")
          .put("java/io/DesugarInputStream", "java/io/InputStream")
          .put("wrapper/adapter/HybridFileSystemProvider", "java/adapter/HybridFileSystemProvider")
          .put("wrapper/adapter/HybridFileTypeDetector", "java/adapter/HybridFileTypeDetector")
          .build();

  public static void main(String[] args) throws Exception {
    setUpDesugaredLibrary();
    undesugaredJar();
  }

  public static Path undesugaredJar() {
    if (!isJDK11DesugaredLibrary()) {
      return ToolHelper.getDesugarJDKLibsBazelGeneratedFile();
    }
    Path desugaredLibJDK11Undesugared = Paths.get("build/libs/desugar_jdk_libs_11_undesugared.jar");
    return generateUndesugaredJar(desugaredLibJDK11Undesugared);
  }

  private static synchronized Path generateUndesugaredJar(Path desugaredLibJDK11Undesugared) {
    if (Files.exists(desugaredLibJDK11Undesugared)) {
      return desugaredLibJDK11Undesugared;
    }
    OpenOption[] options =
        new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
    try (ZipOutputStream out =
        new ZipOutputStream(
            new BufferedOutputStream(
                Files.newOutputStream(desugaredLibJDK11Undesugared, options)))) {
      new DesugaredLibraryJDK11Undesugarer()
          .undesugar(ToolHelper.getDesugarJDKLibsBazelGeneratedFile(), out);
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
          if (ownerMap.containsKey(owner)) {
            String nonDesugaredType = ownerMap.get(owner);
            int firstTypeEnd = descriptor.indexOf(";");
            int firstTypeStart = descriptor.indexOf("L");
            String firstArg =
                firstTypeEnd == -1
                    ? "NoFirstType"
                    : descriptor.substring(firstTypeStart + 1, firstTypeEnd);
            int newOpcode;
            String newDescriptor;
            if (firstArg.equals(nonDesugaredType)) {
              newOpcode = Opcodes.INVOKEVIRTUAL;
              newDescriptor = "(" + descriptor.substring(firstTypeEnd + 1);
            } else {
              newOpcode = Opcodes.INVOKESTATIC;
              newDescriptor = descriptor;
            }
            super.visitMethodInsn(newOpcode, nonDesugaredType, name, newDescriptor, isInterface);
            return;
          }
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }
    };
  }
}
