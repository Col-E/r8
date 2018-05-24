// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.utils.DescriptorUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Utilities to lookup symbols in a classpath using ASM.
 */
public final class AsmUtils {

  private AsmUtils() {
  }

  public static boolean doesClassExist(List<Path> classpath, String className) {
    byte[] classData = loadClassBytesFromClasspath(classpath, className);
    return classData != null;
  }

  public static boolean doesMethodExist(List<Path> classpath, String className,
      String methodName,
      String methodDescriptor) {
    MethodFinder classVisitor = new MethodFinder(methodName, methodDescriptor);
    visitClass(classpath, className, classVisitor);
    return classVisitor.foundMethod;
  }

  private static final class MethodFinder extends ClassVisitor {

    private final String methodName;
    private final String methodDescriptor;
    public boolean foundMethod = false;

    public MethodFinder(String methodName, String methodDescriptor) {
      super(Opcodes.ASM6);
      this.methodName = methodName;
      this.methodDescriptor = methodDescriptor;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
        String[] exceptions) {
      if (name.equals(methodName) && desc.equals(methodDescriptor)) {
        assert !foundMethod;
        foundMethod = true;
      }
      return null;
    }
  }

  public static boolean doesFieldExist(List<Path> classpath, String className,
      String fieldName,
      String fieldType) {
    FieldFinder classVisitor = new FieldFinder(fieldName, fieldType);
    visitClass(classpath, className, classVisitor);
    return classVisitor.foundField;
  }

  private static final class FieldFinder extends ClassVisitor {

    private final String fieldName;
    private final String fieldDescriptor;
    public boolean foundField = false;

    public FieldFinder(String fieldName, String fieldType) {
      super(Opcodes.ASM6);
      this.fieldName = fieldName;
      this.fieldDescriptor = DescriptorUtils.javaTypeToDescriptor(fieldType);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
        Object value) {
      if (name.equals(fieldName) && desc.equals(fieldDescriptor)) {
        assert !foundField;
        foundField = true;
      }
      return null;
    }
  }

  private static void visitClass(List<Path> classpath, String className,
      ClassVisitor classVisitor) {
    byte[] classData = loadClassBytesFromClasspath(classpath, className);
    new ClassReader(classData).accept(classVisitor,
        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
  }

  private static byte[] loadClassBytesFromClasspath(List<Path> classpath, String className) {
    String classFilename = DescriptorUtils.getPathFromJavaType(className);
    for (Path path : classpath) {
      if (path.toFile().getPath().endsWith(".jar")) {
        try (JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(path))) {
          JarEntry jarEntry;
          while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
            if (jarEntry.isDirectory()) {
              continue;
            }
            String entryName = jarEntry.getName();
            if (entryName.equals(classFilename)) {
              byte[] data = new byte[1024];
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              while (true) {
                int bytesRead = jarInputStream.read(data, 0, data.length);
                if (bytesRead < 0) {
                  break;
                }
                baos.write(data, 0, bytesRead);
              }
              return baos.toByteArray();
            }
          }
        } catch (IOException e) {
          throw new AssertionError(e);
        }
      } else if (path.toFile().getPath().endsWith(".class")) {
        if (path.toFile().getPath().equals(classFilename)) {
          try {
            return Files.readAllBytes(path);
          } catch (IOException e) {
            throw new AssertionError(e);
          }
        }

      }
    }
    return null;
  }

}
