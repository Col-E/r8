// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.DebuggeeState;
import com.android.tools.r8.utils.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.harmony.jpda.tests.framework.jdwp.Frame.Variable;

public class DebugStreamComparator {
  private boolean verifyLines = true;
  private boolean verifyFiles = true;
  private boolean verifyMethods = true;
  private boolean verifyClasses = true;
  private boolean verifyVariables = true;

  private final List<String> names = new ArrayList<>();
  private final List<Stream<DebuggeeState>> streams = new ArrayList<>();

  public DebugStreamComparator add(String name, Stream<DebuggeeState> stream) {
    names.add(name);
    streams.add(stream);
    return this;
  }

  public DebugStreamComparator setVerifyLines(boolean verifyLines) {
    this.verifyLines = verifyLines;
    return this;
  }

  public DebugStreamComparator setVerifyFiles(boolean verifyFiles) {
    this.verifyFiles = verifyFiles;
    return this;
  }

  public DebugStreamComparator setVerifyMethods(boolean verifyMethods) {
    this.verifyMethods = verifyMethods;
    return this;
  }

  public DebugStreamComparator setVerifyClasses(boolean verifyClasses) {
    this.verifyClasses = verifyClasses;
    return this;
  }

  public DebugStreamComparator setVerifyVariables(boolean verifyVariables) {
    this.verifyVariables = verifyVariables;
    return this;
  }

  public void compare() {
    List<Iterator<DebuggeeState>> iterators =
        streams.stream().map(Stream::iterator).collect(Collectors.toList());
    while (true) {
      List<DebuggeeState> states = new ArrayList<>(iterators.size());
      boolean done = false;
      for (Iterator<DebuggeeState> iterator : iterators) {
        DebuggeeState state = iterator.next();
        states.add(state);
        if (state == null) {
          done = true;
        }
      }
      try {
        if (done) {
          assertTrue(
              "Not all streams completed at the same time",
              states.stream().allMatch(Objects::isNull));
          return;
        } else {
          verifyStatesEqual(states);
        }
      } catch (AssertionError e) {
        for (int i = 0; i < names.size(); i++) {
          System.err.println(names.get(i) + ": " + prettyPrintState(states.get(i)));
        }
        throw e;
      }
    }
  }

  public static String prettyPrintState(DebuggeeState state) {
    StringBuilder builder = new StringBuilder()
        .append(state.getSourceFile())
        .append(':')
        .append(state.getLineNumber())
        .append(' ')
        .append(state.getClassName())
        .append('.')
        .append(state.getMethodName())
        .append(state.getMethodSignature());
    return builder.toString();
  }

  private void verifyStatesEqual(List<DebuggeeState> states) {
    DebuggeeState reference = states.get(0);
    int line = reference.getLineNumber();
    String file = reference.getSourceFile();
    String clazz = reference.getClassName();
    String method = reference.getMethodName();
    String sig = reference.getMethodSignature();
    List<Variable> variables = reference.getVisibleVariables();
    for (int i = 1; i < states.size(); i++) {
      DebuggeeState state = states.get(i);
      if (verifyFiles) {
        assertEquals("source file mismatch", file, state.getSourceFile());
      }
      if (verifyLines) {
        assertEquals("line number mismatch", line, state.getLineNumber());
      }
      if (verifyClasses) {
        assertEquals("class name mismatch", clazz, state.getClassName());
      }
      if (verifyMethods) {
        assertEquals(
            "method mismatch", method + sig, state.getMethodName() + state.getMethodSignature());
      }
      if (verifyVariables) {
        verifyVariablesEqual(variables, state.getVisibleVariables());
      }
    }
  }

  private static void verifyVariablesEqual(List<Variable> xs, List<Variable> ys) {
    Map<String, Variable> map = new HashMap<>(xs.size());
    for (Variable x : xs) {
      map.put(x.getName(), x);
    }
    List<Variable> missing = new ArrayList<>(ys.size());
    List<Pair<Variable, Variable>> different = new ArrayList<>(Math.min(xs.size(), ys.size()));
    for (Variable y : ys) {
      Variable x = map.remove(y.getName());
      if (x == null) {
        missing.add(y);
      } else if (!isVariableEqual(x, y)) {
        different.add(new Pair<>(x, y));
      }
    }
    StringBuilder builder = null;
    if (!map.isEmpty() || !missing.isEmpty()) {
      builder = new StringBuilder("Missing variables: ");
      for (Variable variable : map.values()) {
        builder.append(variable.getName()).append(", ");
      }
      for (Variable variable : missing) {
        builder.append(variable.getName()).append(", ");
      }
    }
    if (!different.isEmpty()) {
      if (builder == null) {
        builder = new StringBuilder();
      }
      builder.append("Different variables: ");
      for (Pair<Variable, Variable> pair : different) {
        Variable x = pair.getFirst();
        Variable y = pair.getSecond();
        builder.append(x.getName()).append(":").append(x.getType());
        if (x.getGenericSignature() != null) {
          builder.append('(').append(x.getGenericSignature()).append(')');
        }
        builder.append(" != ");
        builder.append(y.getName()).append(":").append(y.getType());
        if (y.getGenericSignature() != null) {
          builder.append('(').append(y.getGenericSignature()).append(')');
        }
      }
    }
    if (builder != null) {
      fail(builder.toString());
    }
  }

  private static boolean isVariableEqual(Variable x, Variable y) {
    return x.getName().equals(y.getName())
        && x.getType().equals(y.getType())
        && Objects.equals(x.getGenericSignature(), y.getGenericSignature());
  }
}
