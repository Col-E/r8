// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class Main {

  private static final String USAGE = StringUtils.joinLines(
      "Usage: --method <qualified-name> <input>*",
      "where <qualified-name> is a fully qualified name of a method (eg, foo.Bar.baz)",
      "  and <input> is a series of input files (eg, .class, .dex, .jar, .zip or .apk)"
  );

  public static void main(String[] args) throws IOException, ExecutionException {
    List<Path> inputs = new ArrayList<>();
    String method = null;
    for (int i = 0; i < args.length; i++) {
      String arg = args[i].trim();
      if (arg.startsWith("--")) {
        if (arg.equals("--help")) {
          System.out.println(USAGE);
          System.exit(0);
          return;
        } else if (arg.equals("--method")) {
          method = args[++i].trim();
          continue;
        }
        throw error("Unknown argument: " + arg);
      } else {
        inputs.add(Paths.get(arg));
      }
    }
    if (method == null) {
      throw error("Requires a --method argument.");
    }

    CodeInspector inspector = new CodeInspector(inputs);
    int methodStart = method.lastIndexOf('.');
    if (methodStart < 0) {
      throw error("Requires a valid --method argument, got '" + method + "'");
    }
    String clazz = method.substring(0, methodStart);
    String methodName = method.substring(methodStart + 1);
    ClassSubject clazzSubject = inspector.clazz(clazz);

    if (!clazzSubject.isPresent()) {
      System.out.println("No definition found for class: '" + clazz + "'");
      return;
    }

    List<FoundMethodSubject> found = clazzSubject
        .allMethods()
        .stream()
        .filter(m -> m.getOriginalName().equals(methodName))
        .collect(Collectors.toList());

    System.out.println("Methods found: " + found.size());
    for (FoundMethodSubject methodSubject : found) {
      System.out.println(methodSubject.getMethod().codeToString());
    }
  }

  private static RuntimeException error(String message) {
    System.err.println(message);
    System.err.println(USAGE);
    System.exit(1);
    throw new RuntimeException();
  }
}
