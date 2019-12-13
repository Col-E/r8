package backport;

public final class StringBackportJava11Main {
  public static void main(String[] args) {
    testRepeat();
  }

  private static void testRepeat() {
    try {
      throw new AssertionError("hey".repeat(-1));
    } catch (IllegalArgumentException e) {
      assertEquals("count is negative: -1", e.getMessage());
    }

    assertEquals("", "".repeat(0));
    assertEquals("", "".repeat(1));
    assertEquals("", "".repeat(2));

    assertEquals("", "hey".repeat(0));
    assertEquals("hey", "hey".repeat(1));
    assertEquals("heyhey", "hey".repeat(2));
    assertEquals("heyheyhey", "hey".repeat(3));
    assertEquals("heyheyheyhey", "hey".repeat(4));
  }

  private static void assertEquals(Object expected, Object actual) {
    if (expected != actual && (expected == null || !expected.equals(actual))) {
      throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
    }
  }
}
