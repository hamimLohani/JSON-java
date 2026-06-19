package org.json;

/*
Public Domain.
 */

/**
 * This provides static methods to convert comma (or otherwise) delimited text into a
 * JSONArray, and to convert a JSONArray into comma (or otherwise) delimited text. Comma
 * delimited text is a very popular format for data interchange. It is
 * understood by most database, spreadsheet, and organizer programs.
 * <p>
 * Each row of text represents a row in a table or a data record. Each row
 * ends with a NEWLINE character. Each row contains one or more values.
 * Values are separated by commas. A value can contain any character except
 * for comma, unless it is wrapped in single quotes or double quotes.
 * <p>
 * The first row usually contains the names of the columns.
 * <p>
 * A comma delimited list can be converted into a JSONArray of JSONObjects.
 * The names for the elements in the JSONObjects can be taken from the names
 * in the first row.
 * @author JSON.org
 * @version 2016-05-01
 */
public class CDL {

    /**
     * Constructs a new CDL object.
     * @deprecated (Utility class cannot be instantiated)
     */
    @Deprecated
    public CDL() {
    }

    /**
     * Get the next value. The value can be wrapped in quotes. The value can
     * be empty.
     * @param x A JSONTokener of the source text.
     * @param delimiter used in the file
     * @return The value string, or null if empty.
     * @throws JSONException if the quoted string is badly formed.
     */
    private static String getValue(JSONTokener x, char delimiter) throws JSONException {
        char c = nextNonWhitespace(x);
        if (c == 0) {
            return null;
        }
        if (isQuote(c)) {
            return nextQuotedValue(x, c);
        }
        if (c == delimiter) {
            x.back();
            return "";
        }
        x.back();
        return x.nextTo(delimiter);
    }

    private static char nextNonWhitespace(JSONTokener x) {
        char c;
        do {
            c = x.next();
        } while (c == ' ' || c == '\t');
        return c;
    }

    private static boolean isQuote(char c) {
        return c == '"' || c == '\'';
    }

    private static String nextQuotedValue(JSONTokener x, char quote) {
        StringBuilder sb = new StringBuilder();
        for (;;) {
            char c = x.next();
            if (isClosingQuote(x, c, quote)) {
                return sb.toString();
            }
            if (isLineEnd(c)) {
                throw x.syntaxError("Missing close quote '" + quote + "'.");
            }
            sb.append(c);
        }
    }

    private static boolean isClosingQuote(JSONTokener x, char c, char quote) {
        if (c != quote) {
            return false;
        }

        // Handle escaped double-quote.
        char nextC = x.next();
        if (nextC == '\"') {
            return false;
        }
        // If our quote was the end of the file, don't step back.
        if (nextC > 0) {
            x.back();
        }
        return true;
    }

    private static boolean isLineEnd(char c) {
        return c == 0 || c == '\n' || c == '\r';
    }

    /**
     * Produce a JSONArray of strings from a row of comma delimited values.
     * @param x A JSONTokener of the source text.
     * @return A JSONArray of strings.
     * @throws JSONException if a called function fails
     */
    public static JSONArray rowToJSONArray(JSONTokener x) throws JSONException {
        return rowToJSONArray(x, ',');
    }

    /**
     * Produce a JSONArray of strings from a row of comma delimited values.
     * @param x A JSONTokener of the source text.
     * @param delimiter custom delimiter char
     * @return A JSONArray of strings.
     * @throws JSONException if a called function fails
     */
    public static JSONArray rowToJSONArray(JSONTokener x, char delimiter) throws JSONException {
        JSONArray ja = new JSONArray();
        for (;;) {
            String value = getValue(x,delimiter);
            char c = x.next();
            if (!appendRowValue(ja, value, c, delimiter)) {
                return null;
            }

            if (isEndOfRow(x, c, delimiter)) {
                return ja;
            }
        }
    }

    private static boolean appendRowValue(JSONArray ja, String value, char next, char delimiter) {
        if (value != null) {
            ja.put(value);
            return true;
        }
        if (ja.length() == 0 && next != delimiter) {
            return false;
        }
        // This line accounts for CSV ending with no newline.
        ja.put("");
        return true;
    }

    private static boolean isEndOfRow(JSONTokener x, char c, char delimiter) {
        while (c != delimiter) {
            if (c != ' ') {
                if (isLineEnd(c)) {
                    return true;
                }
                throw x.syntaxError("Bad character '" + c + "' (" +
                        (int)c + ").");
            }
            c = x.next();
        }
        return false;
    }

    /**
     * Produce a JSONObject from a row of comma delimited text, using a
     * parallel JSONArray of strings to provides the names of the elements.
     * @param names A JSONArray of names. This is commonly obtained from the
     *  first row of a comma delimited text file using the rowToJSONArray
     *  method.
     * @param x A JSONTokener of the source text.
     * @return A JSONObject combining the names and values.
     * @throws JSONException if a called function fails
     */
    public static JSONObject rowToJSONObject(JSONArray names, JSONTokener x) throws JSONException {
        return rowToJSONObject(names, x, ',');
    }

    /**
     * Produce a JSONObject from a row of comma delimited text, using a
     * parallel JSONArray of strings to provides the names of the elements.
     * @param names A JSONArray of names. This is commonly obtained from the
     *  first row of a comma delimited text file using the rowToJSONArray
     *  method.
     * @param x A JSONTokener of the source text.
     * @param delimiter custom delimiter char
     * @return A JSONObject combining the names and values.
     * @throws JSONException if a called function fails
     */
    public static JSONObject rowToJSONObject(JSONArray names, JSONTokener x, char delimiter) throws JSONException {
        JSONArray ja = rowToJSONArray(x, delimiter);
        return ja != null ? ja.toJSONObject(names) :  null;
    }

    /**
     * Produce a comma delimited text row from a JSONArray. Values containing
     * the comma character will be quoted. Troublesome characters may be
     * removed.
     * @param ja A JSONArray of strings.
     * @return A string ending in NEWLINE.
     */
    public static String rowToString(JSONArray ja) {
        return rowToString(ja, ',');
    }

    /**
     * Produce a comma delimited text row from a JSONArray. Values containing
     * the comma character will be quoted. Troublesome characters may be
     * removed.
     * @param ja A JSONArray of strings.
     * @param delimiter custom delimiter char
     * @return A string ending in NEWLINE.
     */
    public static String rowToString(JSONArray ja, char delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ja.length(); i += 1) {
            if (i > 0) {
                sb.append(delimiter);
            }
            Object object = ja.opt(i);
            if (object != null) {
                appendValue(sb, object.toString(), delimiter);
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    private static void appendValue(StringBuilder sb, String string, char delimiter) {
        if (requiresQuote(string, delimiter)) {
            appendQuotedValue(sb, string);
            return;
        }
        sb.append(string);
    }

    private static boolean requiresQuote(String string, char delimiter) {
        return !string.isEmpty() && (string.indexOf(delimiter) >= 0 ||
                string.indexOf('\n') >= 0 || string.indexOf('\r') >= 0 ||
                string.indexOf(0) >= 0 || string.charAt(0) == '"');
    }

    private static void appendQuotedValue(StringBuilder sb, String string) {
        sb.append('"');
        int length = string.length();
        for (int j = 0; j < length; j += 1) {
            char c = string.charAt(j);
            if (c >= ' ' && c != '"') {
                sb.append(c);
            }
        }
        sb.append('"');
    }

    /**
     * Produce a JSONArray of JSONObjects from a comma delimited text string,
     * using the first row as a source of names.
     * @param string The comma delimited text.
     * @return A JSONArray of JSONObjects.
     * @throws JSONException if a called function fails
     */
    public static JSONArray toJSONArray(String string) throws JSONException {
        return toJSONArray(string, ',');
    }

    /**
     * Produce a JSONArray of JSONObjects from a comma delimited text string,
     * using the first row as a source of names.
     * @param string The comma delimited text.
     * @param delimiter custom delimiter char
     * @return A JSONArray of JSONObjects.
     * @throws JSONException if a called function fails
     */
    public static JSONArray toJSONArray(String string, char delimiter) throws JSONException {
        return toJSONArray(new JSONTokener(string), delimiter);
    }

    /**
     * Produce a JSONArray of JSONObjects from a comma delimited text string,
     * using the first row as a source of names.
     * @param x The JSONTokener containing the comma delimited text.
     * @return A JSONArray of JSONObjects.
     * @throws JSONException if a called function fails
     */
    public static JSONArray toJSONArray(JSONTokener x) throws JSONException {
        return toJSONArray(x, ',');
    }

    /**
     * Produce a JSONArray of JSONObjects from a comma delimited text string,
     * using the first row as a source of names.
     * @param x The JSONTokener containing the comma delimited text.
     * @param delimiter custom delimiter char
     * @return A JSONArray of JSONObjects.
     * @throws JSONException if a called function fails
     */
    public static JSONArray toJSONArray(JSONTokener x, char delimiter) throws JSONException {
        return toJSONArray(rowToJSONArray(x, delimiter), x, delimiter);
    }

    /**
     * Produce a JSONArray of JSONObjects from a comma delimited text string
     * using a supplied JSONArray as the source of element names.
     * @param names A JSONArray of strings.
     * @param string The comma delimited text.
     * @return A JSONArray of JSONObjects.
     * @throws JSONException if a called function fails
     */
    public static JSONArray toJSONArray(JSONArray names, String string) throws JSONException {
        return toJSONArray(names, string, ',');
    }

    /**
     * Produce a JSONArray of JSONObjects from a comma delimited text string
     * using a supplied JSONArray as the source of element names.
     * @param names A JSONArray of strings.
     * @param string The comma delimited text.
     * @param delimiter custom delimiter char
     * @return A JSONArray of JSONObjects.
     * @throws JSONException if a called function fails
     */
    public static JSONArray toJSONArray(JSONArray names, String string, char delimiter) throws JSONException {
        return toJSONArray(names, new JSONTokener(string), delimiter);
    }

    /**
     * Produce a JSONArray of JSONObjects from a comma delimited text string
     * using a supplied JSONArray as the source of element names.
     * @param names A JSONArray of strings.
     * @param x A JSONTokener of the source text.
     * @return A JSONArray of JSONObjects.
     * @throws JSONException if a called function fails
     */
    public static JSONArray toJSONArray(JSONArray names, JSONTokener x) throws JSONException {
        return toJSONArray(names, x, ',');
    }

    /**
     * Produce a JSONArray of JSONObjects from a comma delimited text string
     * using a supplied JSONArray as the source of element names.
     * @param names A JSONArray of strings.
     * @param x A JSONTokener of the source text.
     * @param delimiter custom delimiter char
     * @return A JSONArray of JSONObjects.
     * @throws JSONException if a called function fails
     */
    public static JSONArray toJSONArray(JSONArray names, JSONTokener x, char delimiter) throws JSONException {
        if (names == null || names.length() == 0) {
            return null;
        }
        JSONArray ja = new JSONArray();
        for (;;) {
            JSONObject jo = rowToJSONObject(names, x, delimiter);
            if (jo == null) {
                break;
            }
            ja.put(jo);
        }
        if (ja.length() == 0) {
            return null;
        }

        if (isEmptyDataset(ja)) {
            return null;
        }
        return ja;
    }

    private static boolean isEmptyDataset(JSONArray ja) {
        if (ja.length() != 1) {
            return false;
        }
        JSONObject jo = ja.getJSONObject(0);
        if (jo.length() != 1) {
            return false;
        }
        String key = jo.keys().next();
        return "".equals(key) && "".equals(jo.get(key));
    }


    /**
     * Produce a comma delimited text from a JSONArray of JSONObjects. The
     * first row will be a list of names obtained by inspecting the first
     * JSONObject.
     * @param ja A JSONArray of JSONObjects.
     * @return A comma delimited text.
     * @throws JSONException if a called function fails
     */
    public static String toString(JSONArray ja) throws JSONException {
        return toString(ja, ',');
    }

    /**
     * Produce a comma delimited text from a JSONArray of JSONObjects. The
     * first row will be a list of names obtained by inspecting the first
     * JSONObject.
     * @param ja A JSONArray of JSONObjects.
     * @param delimiter custom delimiter char
     * @return A comma delimited text.
     * @throws JSONException if a called function fails
     */
    public static String toString(JSONArray ja, char delimiter) throws JSONException {
        JSONObject jo = ja.optJSONObject(0);
        if (jo != null) {
            JSONArray names = jo.names();
            if (names != null) {
                return rowToString(names, delimiter) + toString(names, ja, delimiter);
            }
        }
        return null;
    }

    /**
     * Produce a comma delimited text from a JSONArray of JSONObjects using
     * a provided list of names. The list of names is not included in the
     * output.
     * @param names A JSONArray of strings.
     * @param ja A JSONArray of JSONObjects.
     * @return A comma delimited text.
     * @throws JSONException if a called function fails
     */
    public static String toString(JSONArray names, JSONArray ja) throws JSONException {
        return toString(names, ja, ',');
    }

    /**
     * Produce a comma delimited text from a JSONArray of JSONObjects using
     * a provided list of names. The list of names is not included in the
     * output.
     * @param names A JSONArray of strings.
     * @param ja A JSONArray of JSONObjects.
     * @param delimiter custom delimiter char
     * @return A comma delimited text.
     * @throws JSONException if a called function fails
     */
    public static String toString(JSONArray names, JSONArray ja, char delimiter) throws JSONException {
        if (names == null || names.length() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ja.length(); i += 1) {
            JSONObject jo = ja.optJSONObject(i);
            if (jo != null) {
                sb.append(rowToString(jo.toJSONArray(names), delimiter));
            }
        }
        return sb.toString();
    }
}
