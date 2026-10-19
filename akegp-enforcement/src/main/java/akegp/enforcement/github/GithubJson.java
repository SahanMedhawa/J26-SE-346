package akegp.enforcement.github;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON helper so the module needs no JSON library: string quoting for requests and a reader for responses. */
final class GithubJson {
  private GithubJson() {}

  static String quote(String s) {
    if (s == null) return "null";
    StringBuilder sb = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
      }
    }
    return sb.append('"').toString();
  }

  /** @return Map, List, String, Double, Boolean or null. Throws IllegalArgumentException on malformed JSON. */
  static Object parse(String text) {
    Reader r = new Reader(text);
    r.skipSpace();
    Object value = r.value();
    r.skipSpace();
    if (r.pos != text.length()) throw new IllegalArgumentException("Unexpected text after JSON value");
    return value;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> object(Object o) { return o instanceof Map<?, ?> ? (Map<String, Object>) o : Map.of(); }

  @SuppressWarnings("unchecked")
  static List<Object> array(Object o) { return o instanceof List<?> ? (List<Object>) o : List.of(); }

  static String string(Object o) { return o instanceof String s ? s : null; }

  private static final class Reader {
    final String s;
    int pos;

    Reader(String s) { this.s = s; }

    void skipSpace() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }

    Object value() {
      if (pos >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
      char c = s.charAt(pos);
      if (c == '{') return objectValue();
      if (c == '[') return arrayValue();
      if (c == '"') return stringValue();
      if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
      if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
      if (s.startsWith("null", pos)) { pos += 4; return null; }
      return numberValue();
    }

    Map<String, Object> objectValue() {
      Map<String, Object> out = new LinkedHashMap<>();
      pos++; skipSpace();
      if (peek('}')) { pos++; return out; }
      while (true) {
        skipSpace();
        String key = stringValue();
        skipSpace(); expect(':'); skipSpace();
        out.put(key, value());
        skipSpace();
        if (peek(',')) { pos++; continue; }
        expect('}');
        return out;
      }
    }

    List<Object> arrayValue() {
      List<Object> out = new ArrayList<>();
      pos++; skipSpace();
      if (peek(']')) { pos++; return out; }
      while (true) {
        skipSpace();
        out.add(value());
        skipSpace();
        if (peek(',')) { pos++; continue; }
        expect(']');
        return out;
      }
    }

    String stringValue() {
      expect('"');
      StringBuilder sb = new StringBuilder();
      while (pos < s.length()) {
        char c = s.charAt(pos++);
        if (c == '"') return sb.toString();
        if (c != '\\') { sb.append(c); continue; }
        if (pos >= s.length()) break;
        char e = s.charAt(pos++);
        switch (e) {
          case '"', '\\', '/' -> sb.append(e);
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> {
            if (pos + 4 > s.length()) throw new IllegalArgumentException("Bad unicode escape");
            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
            pos += 4;
          }
          default -> throw new IllegalArgumentException("Bad escape \\" + e);
        }
      }
      throw new IllegalArgumentException("Unterminated string");
    }

    Double numberValue() {
      int start = pos;
      while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
      if (start == pos) throw new IllegalArgumentException("Unexpected character '" + s.charAt(pos) + "'");
      return Double.valueOf(s.substring(start, pos));
    }

    boolean peek(char c) { return pos < s.length() && s.charAt(pos) == c; }

    void expect(char c) {
      if (!peek(c)) throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
      pos++;
    }
  }
}
