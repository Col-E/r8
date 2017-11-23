// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.google.common.collect.ImmutableList;
import jasmin.ClassFile;
import java.io.File;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

// TODO(b/65474850) Should we build Jasmin at compile time or runtime ?
public class JasminDebugTest extends DebugTestBase {

  static class JasminTestConfig extends D8BaseDebugTestConfig {

    public JasminTestConfig(TemporaryFolder temp, JasminBuilder builder) {
      super(temp, compile(temp, builder));
    }

    private static Path compile(TemporaryFolder temp, JasminBuilder builder) {
      try {
        ImmutableList<ClassBuilder> classes = builder.getClasses();
        File out = temp.newFolder();
        List<Path> classFiles = new ArrayList<>(classes.size());
        for (ClassBuilder clazz : classes) {
          ClassFile file = new ClassFile();
          file.readJasmin(new StringReader(clazz.toString()), clazz.name, false);
          Path path = out.toPath().resolve(clazz.name + ".class");
          Files.createDirectories(path.getParent());
          try (OutputStream outputStream = Files.newOutputStream(path)) {
            file.write(outputStream);
          }
          classFiles.add(path);
        }
        return compile(temp, classFiles);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    private static Path compile(TemporaryFolder temp, List<Path> paths) throws Throwable {
      int minSdk = ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm());
      Path out = temp.newFolder().toPath().resolve("d8_jasmin.jar");
      ToolHelper.runD8(
          D8Command.builder()
              .addProgramFiles(paths)
              .setOutputPath(out)
              .setMinApiLevel(minSdk)
              .setMode(CompilationMode.DEBUG)
              .addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(minSdk)))
              .build(),
          null);
      return out;
    }
  }

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void testUselessCheckcast() throws Throwable {
    final String className = "UselessCheckCast";
    final String sourcefile = className + ".j";
    final String methodName = "test";
    runDebugTest(
        new JasminTestConfig(temp, getBuilderForUselessCheckcast(className, methodName)),
        className,
        breakpoint(className, methodName),
        run(),
        checkLine(sourcefile, 1),
        stepOver(),
        checkLine(sourcefile, 2),
        checkLocal("local"),
        stepOver(),
        checkLine(sourcefile, 3),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 4),
        run());
  }

  private JasminBuilder getBuilderForUselessCheckcast(String testClassName, String testMethodName) {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass(testClassName);

    clazz.addStaticMethod(testMethodName, ImmutableList.of("Ljava/lang/Object;"), "V",
        ".limit stack 1",
        ".limit locals 3",
        ".var 1 is local Ljava/lang/Object; from Label1 to Label2",
        ".line 1",
        " aload 0",
        " dup",
        " astore 1",
        " Label1:",
        ".line 2",
        " checkcast " + testClassName,
        " Label2:",
        ".line 3",
        " checkcast " + testClassName,
        ".line 4",
        "return");

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "aconst_null",
        "invokestatic " + testClassName + "/" + testMethodName + "(Ljava/lang/Object;)V",
        "return");

    return builder;
  }
}
