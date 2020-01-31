package backport;

public final class CharSequenceBackportJava11Main {
  public static void main(String[] args) {
    testCompare();
  }

  private static void testCompare() {
    assertTrue(CharSequence.compare("Hello", "Hello") == 0);

    assertTrue(CharSequence.compare("Hey", "Hello") > 0);
    assertTrue(CharSequence.compare("Hello", "Hey") < 0);

    assertTrue(CharSequence.compare("Hel", "Hello") < 0);
    assertTrue(CharSequence.compare("Hello", "Hel") > 0);

    assertTrue(CharSequence.compare("", "") == 0);
    assertTrue(CharSequence.compare("", "Hello") < 0);
    assertTrue(CharSequence.compare("Hello", "") > 0);

    // Different CharSequence types:
    assertTrue(CharSequence.compare("Hello", new StringBuilder("Hello")) == 0);
    assertTrue(CharSequence.compare(new StringBuffer("hey"), "Hello") > 0);
    assertTrue(CharSequence.compare(new StringBuffer("Hello"), new StringBuilder("Hey")) < 0);

    try {
      throw new AssertionError(CharSequence.compare(null, "Hello"));
    } catch (NullPointerException expected) {
    }
    try {
      throw new AssertionError(CharSequence.compare("Hello", null));
    } catch (NullPointerException expected) {
    }
    try {
      // Ensure a == b fast path does not happen before null checks.
      throw new AssertionError(CharSequence.compare(null, null));
    } catch (NullPointerException expected) {
    }
  }

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new AssertionError("Expected <true> but was <false>");
    }
  }
}
