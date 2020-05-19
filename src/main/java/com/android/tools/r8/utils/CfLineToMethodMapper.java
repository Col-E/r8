// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public class CfLineToMethodMapper {

  private Map<String, Int2ReferenceOpenHashMap<String>> sourceMethodMapping = null;
  private final AndroidApp inputApp;
  private static final String NAME_DESCRIPTOR_SEPARATOR = ";;";

  public CfLineToMethodMapper(AndroidApp inputApp) {
    this.inputApp = inputApp;
  }

  public String lookupNameAndDescriptor(String binaryName, int lineNumber)
      throws ResourceException {
    if (sourceMethodMapping == null) {
      sourceMethodMapping = new HashMap<>();
      readLineNumbersFromClassFiles();
    }
    Int2ReferenceOpenHashMap<String> lineMappings = sourceMethodMapping.get(binaryName);
    return lineMappings == null ? null : lineMappings.get(lineNumber);
  }

  private void readLineNumbersFromClassFiles() throws ResourceException {
    ClassVisitor classVisitor = new ClassVisitor();
    for (ProgramResourceProvider resourceProvider : inputApp.getProgramResourceProviders()) {
      if (resourceProvider instanceof ArchiveResourceProvider) {
        ArchiveResourceProvider provider = (ArchiveResourceProvider) resourceProvider;
        provider.accept(
            programResource -> {
              if (programResource.getKind() != Kind.CF) {
                return;
              }
              try {
                new ClassReader(StreamUtils.StreamToByteArrayClose(programResource.getByteStream()))
                    .accept(classVisitor, ClassReader.SKIP_FRAMES);
              } catch (IOException | ResourceException e) {
                // Intentionally left empty because the addition of inline info for kotlin inline
                // functions is a best effort.
              }
            });
      } else {
        for (ProgramResource programResource : resourceProvider.getProgramResources()) {
          if (programResource.getKind() == Kind.CF) {
            try {
              new ClassReader(StreamUtils.StreamToByteArrayClose(programResource.getByteStream()))
                  .accept(classVisitor, ClassReader.SKIP_FRAMES);
            } catch (IOException e) {
              // Intentionally left empty because the addition of inline info for kotlin inline
              // functions is a best effort.
            }
          }
        }
      }
    }
  }

  public static String getName(String nameAndDescriptor) {
    int index = nameAndDescriptor.indexOf(NAME_DESCRIPTOR_SEPARATOR);
    assert index > 0;
    return nameAndDescriptor.substring(0, index);
  }

  public static String getDescriptor(String nameAndDescriptor) {
    int index = nameAndDescriptor.indexOf(NAME_DESCRIPTOR_SEPARATOR);
    assert index > 0;
    return nameAndDescriptor.substring(index + NAME_DESCRIPTOR_SEPARATOR.length());
  }

  private class ClassVisitor extends org.objectweb.asm.ClassVisitor {

    private Int2ReferenceOpenHashMap<String> currentLineNumberMapping = null;

    private ClassVisitor() {
      super(InternalOptions.ASM_VERSION);
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      super.visit(version, access, name, signature, superName, interfaces);
      currentLineNumberMapping =
          sourceMethodMapping.computeIfAbsent(name, ignored -> new Int2ReferenceOpenHashMap<>());
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      return new MethodLineVisitor(
          name + NAME_DESCRIPTOR_SEPARATOR + descriptor, currentLineNumberMapping);
    }
  }

  private static class MethodLineVisitor extends MethodVisitor {

    private final String nameAndDescriptor;
    private final Map<Integer, String> lineMethodMapping;

    private MethodLineVisitor(String nameAndDescriptor, Map<Integer, String> lineMethodMapping) {
      super(InternalOptions.ASM_VERSION);
      this.nameAndDescriptor = nameAndDescriptor;
      this.lineMethodMapping = lineMethodMapping;
    }

    @Override
    public void visitLineNumber(int line, Label start) {
      lineMethodMapping.put(line, nameAndDescriptor);
    }
  }
}
