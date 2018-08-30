// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.DebuggeeState;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.FrameInspector;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.harmony.jpda.tests.framework.jdwp.Frame.Variable;

public class DebugStreamComparator {

  public static class PrintOptions {
    boolean printStates = false;
    boolean printClass = false;
    boolean printMethod = false;
    boolean printVariables = false;
    boolean printStack = false;

    public static PrintOptions printAll() {
      PrintOptions options = new PrintOptions();
      options.printStates = true;
      options.printClass = true;
      options.printMethod = true;
      options.printVariables = true;
      options.printStack = true;
      return options;
    }
  }

  private static class StreamState {
    static final int ENTRY_LINE = -1;
    static final int PLACEHOLDER_LINE = -2;

    final Iterator<DebuggeeState> iterator;
    final Deque<Integer> frameEntryLines = new ArrayDeque<>();
    int currentLine = ENTRY_LINE;

    StreamState(Stream<DebuggeeState> stream) {
      iterator = stream.iterator();
    }

    DebuggeeState next() {
      while (true) {
        DebuggeeState state = iterator.next();
        if (state == null) {
          return null;
        }
        int nextDepth = state.getFrameDepth();
        int nextLine = state.getLineNumber();
        if (nextDepth == frameEntryLines.size()) {
          currentLine = nextLine;
          return state;
        }
        if (nextDepth > frameEntryLines.size()) {
          frameEntryLines.push(currentLine);
          while (nextDepth > frameEntryLines.size()) {
            // If the depth grows by more than one we have entered into filtered out frames, eg,
            // java/android internals. In this case push placeholder lines on the stack.
            frameEntryLines.push(PLACEHOLDER_LINE);
          }
          currentLine = nextLine;
          assert nextDepth == frameEntryLines.size();
          return state;
        }
        currentLine = nextLine;
        while (frameEntryLines.size() > nextDepth + 1) {
          // If the depth decreases by more than one we have popped the filtered frames or an
          // exception has unwinded the stack.
          frameEntryLines.pop();
        }
        int lineOnEntry = frameEntryLines.pop();
        assert nextDepth == frameEntryLines.size();
        if (lineOnEntry != nextLine) {
          return state;
        }
        // A frame was popped and the current line is the same as when the frame was entered.
        // In this case we advance again to avoid comparing that a function call returns to the
        // same line (which may not be the case if no stores are needed after the call).
      }
    }
  }

  private boolean verifyLines = true;
  private boolean verifyFiles = true;
  private boolean verifyMethods = true;
  private boolean verifyClasses = true;
  private boolean verifyVariables = true;
  private boolean verifyStack = false;

  private Predicate<DebuggeeState> filter = s -> true;

  private final List<String> names = new ArrayList<>();
  private final List<Stream<DebuggeeState>> streams = new ArrayList<>();

  private final PrintOptions printOptions = new PrintOptions();
  private final PrintOptions errorPrintOptions = PrintOptions.printAll();

  public DebugStreamComparator add(String name, Stream<DebuggeeState> stream) {
    names.add(name);
    streams.add(stream);
    return this;
  }

  public DebugStreamComparator setFilter(Predicate<DebuggeeState> filter) {
    this.filter = filter;
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

  public DebugStreamComparator setVerifyStack(boolean verifyStack) {
    this.verifyStack = verifyStack;
    return this;
  }

  public DebugStreamComparator setPrintStates(boolean printStates) {
    printOptions.printStates = printStates;
    return this;
  }

  public DebugStreamComparator setPrintClass(boolean printClass) {
    printOptions.printClass = printClass;
    return this;
  }

  public DebugStreamComparator setPrintMethod(boolean printMethod) {
    printOptions.printMethod = printMethod;
    return this;
  }

  public DebugStreamComparator setPrintStack(boolean printStack) {
    printOptions.printStack = printStack;
    return this;
  }

  public DebugStreamComparator setPrintVariables(boolean printVariables) {
    printOptions.printVariables = printVariables;
    return this;
  }

  public void run() {
    if (streams.size() != 1) {
      throw new RuntimeException("Expected single stream to run");
    }
    internal();
  }

  public void compare() {
    if (streams.size() < 2) {
      throw new RuntimeException("Expected multiple streams to compare");
    }
    internal();
  }

  private void internal() {
    List<StreamState> streamStates =
        streams.stream().map(StreamState::new).collect(Collectors.toList());
    while (true) {
      List<DebuggeeState> states = new ArrayList<>(streamStates.size());
      boolean done = false;
      for (StreamState streamState : streamStates) {
        DebuggeeState state;
        do {
          state = streamState.next();
        } while (state != null && !filter.test(state));
        states.add(state);
        if (state == null) {
          done = true;
        }
      }
      try {
        if (done) {
          assertTrue(
              "Not all streams completed at the same time. "
                  + "Set 'DebugTestBase.DEBUG_TEST = true' to aid in diagnosing the issue.",
              states.stream().allMatch(Objects::isNull));
          return;
        } else {
          verifyStatesEqual(states);
          if (printOptions.printStates) {
            System.out.println(prettyPrintState(states.get(0), printOptions));
          }
        }
      } catch (AssertionError e) {
        for (int i = 0; i < names.size(); i++) {
          System.err.println(
              names.get(i) + ":\n" + prettyPrintState(states.get(i), errorPrintOptions));
        }
        throw e;
      }
    }
  }

  public static String prettyPrintState(DebuggeeState state, PrintOptions options) {
    StringBuilder builder = new StringBuilder();
    if (!options.printStack) {
      builder.append(prettyPrintFrame(state, options));
    } else {
      for (int i = 0; i < state.getFrameDepth(); i++) {
        builder.append("f").append(i).append(": ");
        builder.append(prettyPrintFrame(state.getFrame(i), options));
        builder.append('\n');
      }
    }
    return builder.toString();
  }

  public static String prettyPrintFrame(FrameInspector frame, PrintOptions options) {
    StringBuilder builder =
        new StringBuilder()
            .append(frame.getSourceFile())
            .append(':')
            .append(frame.getLineNumber())
            .append(' ');
    if (options.printClass) {
      builder.append("\n    class:  ").append(frame.getClassName());
    }
    if (options.printMethod) {
      builder
          .append("\n    method: ")
          .append(frame.getMethodName())
          .append(frame.getMethodSignature());
    }
    if (options.printVariables) {
      builder.append(prettyPrintVariables(frame.getVisibleVariables(), options));
    }
    return builder.toString();
  }

  public static String prettyPrintVariables(List<Variable> variables, PrintOptions options) {
    StringBuilder builder = new StringBuilder("\n    locals: ");
    StringUtils.append(
        builder,
        ListUtils.map(variables, v -> v.getName() + ':' + v.getSignature()),
        ", ",
        BraceType.NONE);
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
    int frameDepth = reference.getFrameDepth();
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
      if (verifyStack) {
        assertEquals(frameDepth, state.getFrameDepth());
        for (int j = 0; j < frameDepth; j++) {
          FrameInspector referenceInspector = reference.getFrame(j);
          FrameInspector stateInspector = state.getFrame(j);
          verifyVariablesEqual(
              referenceInspector.getVisibleVariables(), stateInspector.getVisibleVariables());
        }
      }
    }
  }

  private static void verifyVariablesEqual(List<Variable> xs, List<Variable> ys) {
    Map<String, Variable> map = new HashMap<>(xs.size());
    for (Variable x : xs) {
      map.put(x.getName(), x);
    }
    List<Variable> unexpected = new ArrayList<>(ys.size());
    List<Pair<Variable, Variable>> different = new ArrayList<>(Math.min(xs.size(), ys.size()));
    for (Variable y : ys) {
      Variable x = map.remove(y.getName());
      if (x == null) {
        unexpected.add(y);
      } else if (!isVariableEqual(x, y)) {
        different.add(new Pair<>(x, y));
      }
    }
    StringBuilder builder = null;
    if (!map.isEmpty() || !unexpected.isEmpty()) {
      builder = new StringBuilder();
      if (!map.isEmpty()) {
        builder.append("Missing variables: ");
        for (Variable variable : map.values()) {
          builder.append(variable.getName()).append(", ");
        }
      }
      if (!unexpected.isEmpty()) {
        builder.append("Unexpected variables: ");
        for (Variable variable : unexpected) {
          builder.append(variable.getName()).append(", ");
        }
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
