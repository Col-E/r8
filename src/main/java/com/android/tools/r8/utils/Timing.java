// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

// Helper for collecting timing information during execution.
// Timing t = new Timing("R8");
// A timing tree is collected by calling the following pair (nesting will create the tree):
//     t.begin("My task);
//     try { ... } finally { t.end(); }
// or alternatively:
//     t.scope("My task", () -> { ... });
// Finally a report is printed by:
//     t.report();

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class Timing {

  private final Stack<Node> stack;
  private final boolean trackMemory;

  public Timing() {
    this("<no title>");
  }

  public Timing(String title) {
    this(title, false);
  }

  public Timing(String title, boolean trackMemory) {
    this.trackMemory = trackMemory;
    stack = new Stack<>();
    stack.push(new Node("Recorded timings for " + title));
  }

  class Node {
    final String title;

    final Map<String, Node> children = new LinkedHashMap<>();
    long duration = 0;
    long start_time;
    List<String> startMemory;
    List<String> endMemory;

    Node(String title) {
      this.title = title;
      this.start_time = System.nanoTime();
      if (trackMemory) {
        startMemory = computeMemoryInformation();
      }
    }

    void restart() {
      assert start_time == -1;
      start_time = System.nanoTime();
      if (trackMemory) {
        startMemory = computeMemoryInformation();
      }
    }

    void end() {
      duration += System.nanoTime() - start_time;
      start_time = -1;
      assert duration() >= 0;
      if (trackMemory) {
        System.gc();
        endMemory = computeMemoryInformation();
      }
    }

    long duration() {
      return duration;
    }

    @Override
    public String toString() {
      return title + ": " + (duration() / 1000000) + "ms.";
    }

    public String toString(Node top) {
      if (this == top) return toString();
      long percentage = duration() * 100 / top.duration();
      return toString() + " (" + percentage + "%)";
    }

    public void report(int depth, Node top) {
      assert duration() >= 0;
      if (depth > 0) {
        for (int i = 0; i < depth; i++) {
          System.out.print("  ");
        }
        System.out.print("- ");
      }
      System.out.println(toString(top));
      System.out.println();
      if (trackMemory) {
        printMemoryStart(depth);
        System.out.println();
      }
      children.values().forEach(p -> p.report(depth + 1, top));
      if (trackMemory) {
        printMemoryEnd(depth);
        System.out.println();
      }
    }

    private void printMemoryStart(int depth) {
      if (startMemory != null) {
        printMemory(depth, title + "(Memory) Start: ", startMemory);
      }
    }

    private void printMemoryEnd(int depth) {
      if (endMemory != null) {
        printMemory(depth, title + "(Memory) End: ", endMemory);
      }
    }

    private void printMemory(int depth, String header, List<String> strings) {
      for (int i = 0; i <= depth; i++) {
        System.out.print("  ");
      }
      System.out.println(header);
      for (String memoryInfo : strings) {
        for (int i = 0; i <= depth; i++) {
          System.out.print("  ");
        }
        System.out.println(memoryInfo);
      }
    }
  }


  public void begin(String title) {
    Node parent = stack.peek();
    Node child;
    if (parent.children.containsKey(title)) {
      child = parent.children.get(title);
      child.restart();
    } else {
      child = new Node(title);
      parent.children.put(title, child);
    }
    stack.push(child);
  }

  public void end() {
    stack.peek().end();  // record time.
    stack.pop();
  }

  public void report() {
    Node top = stack.peek();
    top.end();
    System.out.println();
    top.report(0, top);
  }

  public void scope(String title, TimingScope fn) {
    begin(title);
    try {
      fn.apply();
    } finally {
      end();
    }
  }

  public interface TimingScope {
    void apply();
  }

  private List<String> computeMemoryInformation() {
    List<String> strings = new ArrayList<>();
    strings.add(
        "Free memory: "
            + Runtime.getRuntime().freeMemory()
            + "\tTotal memory: "
            + Runtime.getRuntime().totalMemory()
            + "\tMax memory: "
            + Runtime.getRuntime().maxMemory());
    MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    strings.add("Heap summary: " + memoryMXBean.getHeapMemoryUsage().toString());
    strings.add("Non-heap summary: " + memoryMXBean.getNonHeapMemoryUsage().toString());
    // Print out the memory information for all managed memory pools.
    for (MemoryPoolMXBean memoryPoolMXBean : ManagementFactory.getMemoryPoolMXBeans()) {
      strings.add(memoryPoolMXBean.getName() + ": " + memoryPoolMXBean.getUsage().toString());
    }
    return strings;
  }
}
