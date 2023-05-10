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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class Timing {

  private static final int MINIMUM_REPORT_PERCENTAGE = 2;

  private static final Timing EMPTY =
      new Timing("<empty>", false) {
        @Override
        public TimingMerger beginMerger(String title, int numberOfThreads) {
          return new TimingMerger(null, -1, this) {
            @Override
            public void add(Collection<Timing> timings) {
              // Ignore.
            }

            @Override
            public void end() {
              // Ignore.
            }
          };
        }

        @Override
        public void begin(String title) {
          // Ignore.
        }

        @Override
        public void end() {
          // Ignore.
        }

        @Override
        public void report() {
          // Ignore.
        }
      };

  public static Timing empty() {
    return Timing.EMPTY;
  }

  private abstract static class TimingDelegateBase extends Timing {
    private final Timing timing;

    public TimingDelegateBase(String title, Timing timing) {
      super(title);
      this.timing = timing;
    }

    @Override
    public TimingMerger beginMerger(String title, int numberOfThreads) {
      return timing.beginMerger(title, numberOfThreads);
    }

    @Override
    public void begin(String title) {
      timing.begin(title);
    }

    @Override
    public <E extends Exception> void time(String title, ThrowingAction<E> action) throws E {
      timing.time(title, action);
    }

    @Override
    public <T, E extends Exception> T time(String title, ThrowingSupplier<T, E> supplier) throws E {
      return timing.time(title, supplier);
    }

    @Override
    public void end() {
      timing.end();
    }

    @Override
    public void report() {
      timing.report();
    }
  }

  private static class TimingWithCancellation extends TimingDelegateBase {
    private final InternalOptions options;

    TimingWithCancellation(InternalOptions options, Timing timing) {
      super("<cancel>", timing);
      this.options = options;
    }

    @Override
    public void begin(String title) {
      if (options.checkIfCancelled()) {
        throw new CancelCompilationException();
      }
      super.begin(title);
    }
  }

  public static Timing create(String title, InternalOptions options) {
    // We also create a timer when running assertions to validate wellformedness of the node stack.
    Timing timing =
        options.printTimes || InternalOptions.assertionsEnabled()
            ? new Timing(title, options.printMemory)
            : Timing.empty();
    if (options.cancelCompilationChecker != null) {
      return new TimingWithCancellation(options, timing);
    }
    return timing;
  }

  public static Timing create(String title, boolean printMemory) {
    return new Timing(title, printMemory);
  }

  private final Node top;
  private final Deque<Node> stack;
  private final boolean trackMemory;

  @Deprecated
  public Timing(String title) {
    this(title, false);
  }

  private Timing(String title, boolean trackMemory) {
    this.trackMemory = trackMemory;
    stack = new ArrayDeque<>();
    top = new Node(title, trackMemory);
    stack.push(top);
  }

  private static class MemInfo {
    final long used;

    MemInfo(long used) {
      this.used = used;
    }

    public static MemInfo fromTotalAndFree(long total, long free) {
      return new MemInfo(total - free);
    }

    long usedDelta(MemInfo previous) {
      return used - previous.used;
    }
  }

  static class Node {
    final String title;
    final boolean trackMemory;

    final Map<String, Node> children = new LinkedHashMap<>();
    long duration = 0;
    long start_time;
    Map<String, MemInfo> startMemory;
    Map<String, MemInfo> endMemory;

    Node(String title, boolean trackMemory) {
      this.title = title;
      this.trackMemory = trackMemory;
      if (trackMemory) {
        startMemory = computeMemoryInformation();
      }
      this.start_time = System.nanoTime();
    }

    void restart() {
      assert start_time == -1;
      if (trackMemory) {
        startMemory = computeMemoryInformation();
      }
      start_time = System.nanoTime();
    }

    void end() {
      duration += System.nanoTime() - start_time;
      start_time = -1;
      assert duration() >= 0;
      if (trackMemory) {
        endMemory = computeMemoryInformation();
      }
    }

    long duration() {
      return duration;
    }

    @Override
    public String toString() {
      return title + ": " + prettyTime(duration());
    }

    public String toString(Node top) {
      if (this == top) return toString();
      return "(" + prettyPercentage(duration(), top.duration()) + ") " + toString();
    }

    public void report(int depth, Node top) {
      assert duration() >= 0;
      if (percentage(duration(), top.duration()) < MINIMUM_REPORT_PERCENTAGE) {
        return;
      }
      printPrefix(depth);
      System.out.println(toString(top));
      if (trackMemory) {
        printMemory(depth);
      }
      if (children.isEmpty()) {
        return;
      }
      Collection<Node> childNodes = children.values();
      long childTime = 0;
      for (Node childNode : childNodes) {
        childTime += childNode.duration();
      }
      if (childTime < duration()) {
        long unaccounted = duration() - childTime;
        if (percentage(unaccounted, top.duration()) >= MINIMUM_REPORT_PERCENTAGE) {
          printPrefix(depth + 1);
          System.out.println(
              "("
                  + prettyPercentage(unaccounted, top.duration())
                  + ") Unaccounted: "
                  + prettyTime(unaccounted));
        }
      }
      childNodes.forEach(p -> p.report(depth + 1, top));

    }

    void printPrefix(int depth) {
      if (depth > 0) {
        System.out.print("  ".repeat(depth));
        System.out.print("- ");
      }
    }

    void printMemory(int depth) {
      for (Entry<String, MemInfo> start : startMemory.entrySet()) {
        if (start.getKey().equals("Memory")) {
          for (int i = 0; i <= depth; i++) {
            System.out.print("  ");
          }
          MemInfo endValue = endMemory.get(start.getKey());
          MemInfo startValue = start.getValue();
          System.out.println(
              start.getKey()
                  + " start: "
                  + prettySize(startValue.used)
                  + ", end: "
                  + prettySize(endValue.used)
                  + ", delta: "
                  + prettySize(endValue.usedDelta(startValue)));
        }
      }
    }
  }

  public static class TimingMerger {
    final Node parent;
    final Node merged;

    private int taskCount = 0;
    private Node slowest = new Node("<zero>", false);

    private TimingMerger(String title, int numberOfThreads, Timing timing) {
      parent = timing.stack.peek();
      merged =
          new Node(title, timing.trackMemory) {
            @Override
            public void report(int depth, Node top) {
              assert duration() >= 0;
              printPrefix(depth);
              System.out.print(toString());
              if (numberOfThreads <= 0) {
                System.out.println(" (unknown thread count)");
              } else {
                long walltime = parent.duration();
                long perThreadTime = duration() / numberOfThreads;
                System.out.println(
                    ", tasks: "
                        + taskCount
                        + ", threads: "
                        + numberOfThreads
                        + ", utilization: "
                        + prettyPercentage(perThreadTime, walltime));
              }
              if (trackMemory) {
                printMemory(depth);
              }
              // Report children with this merge node as "top" so times are relative to the total
              // merge.
              children.forEach((title, node) -> node.report(depth + 1, this));
              // Print the slowest entry if one was found.
              if (slowest.duration > 0) {
                printPrefix(depth);
                System.out.println("SLOWEST " + slowest.toString(this));
                slowest.children.forEach((title, node) -> node.report(depth + 1, this));
              }
            }

            @Override
            public String toString() {
              return "MERGE " + super.toString();
            }
          };
    }

    private static class Item {
      final Node mergeTarget;
      final Node mergeSource;

      public Item(Node mergeTarget, Node mergeSource) {
        this.mergeTarget = mergeTarget;
        this.mergeSource = mergeSource;
      }
    }

    public void add(Collection<Timing> timings) {
      final boolean trackMemory = merged.trackMemory;
      Deque<Item> worklist = new ArrayDeque<>();
      for (Timing timing : timings) {
        if (timing == empty()) {
          continue;
        }
        assert timing.stack.isEmpty() : "Expected sub-timing to have completed prior to merge";
        ++taskCount;
        merged.duration += timing.top.duration;
        if (timing.top.duration > slowest.duration) {
          slowest = timing.top;
        }
        worklist.addLast(new Item(merged, timing.top));
      }
      while (!worklist.isEmpty()) {
        Item item = worklist.pollFirst();
        item.mergeSource.children.forEach(
            (title, child) -> {
              Node mergeTarget =
                  item.mergeTarget.children.computeIfAbsent(title, t -> new Node(t, trackMemory));
              mergeTarget.duration += child.duration;
              mergeTarget.endMemory = child.endMemory;
              if (!child.children.isEmpty()) {
                worklist.addLast(new Item(mergeTarget, child));
              }
            });
      }
    }

    public void end() {
      assert !parent.children.containsKey(merged.title);
      merged.end();
      parent.children.put(merged.title, merged);
    }
  }

  public TimingMerger beginMerger(String title, int numberOfThreads) {
    return new TimingMerger(title, numberOfThreads, this);
  }

  private static long percentage(long part, long total) {
    return part * 100 / total;
  }

  private static String prettyPercentage(long part, long total) {
    return percentage(part, total) + "%";
  }

  private static String prettyTime(long value) {
    return (value / 1000000) + "ms";
  }

  private static String prettySize(long value) {
    return prettyNumber(value / 1024) + "k";
  }

  private static String prettyNumber(long value) {
    String printed = "" + Math.abs(value);
    if (printed.length() < 4) {
      return "" + value;
    }
    StringBuilder builder = new StringBuilder();
    if (value < 0) {
      builder.append('-');
    }
    int prefix = printed.length() % 3;
    builder.append(printed, 0, prefix);
    for (int i = prefix; i < printed.length(); i += 3) {
      if (i > 0) {
        builder.append('.');
      }
      builder.append(printed, i, i + 3);
    }
    return builder.toString();
  }

  public void begin(String title) {
    Node parent = stack.peek();
    Node child;
    if (parent.children.containsKey(title)) {
      child = parent.children.get(title);
      child.restart();
    } else {
      child = new Node(title, trackMemory);
      parent.children.put(title, child);
    }
    stack.push(child);
  }

  public <E extends Exception> void time(String title, ThrowingAction<E> action) throws E {
    begin(title);
    try {
      action.execute();
    } finally {
      end();
    }
  }

  public <T, E extends Exception> T time(String title, ThrowingSupplier<T, E> supplier) throws E {
    begin(title);
    try {
      return supplier.get();
    } finally {
      end();
    }
  }

  public void end() {
    stack.peek().end();  // record time.
    stack.pop();
  }

  public void report() {
    assert stack.size() == 1;
    Node top = stack.peek();
    assert top == this.top;
    top.end();
    System.out.println("Recorded timings:");
    top.report(0, top);
  }

  private static Map<String, MemInfo> computeMemoryInformation() {
    System.gc();
    Map<String, MemInfo> info = new LinkedHashMap<>();
    info.put(
        "Memory",
        MemInfo.fromTotalAndFree(
            Runtime.getRuntime().totalMemory(), Runtime.getRuntime().freeMemory()));
    return info;
  }
}
