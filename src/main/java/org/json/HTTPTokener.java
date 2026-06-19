package org.json;

/*
Public Domain.
*/

/**
 * The HTTPTokener extends the JSONTokener to provide additional methods
 * for the parsing of HTTP headers.
 * @author JSON.org
 * @version 2015-12-09
 */
public class HTTPTokener extends JSONTokener {

    /**
     * Construct an HTTPTokener from a string.
     * @param string A source string.
     */
    public HTTPTokener(String string) {
        super(string);
    }


    /**
     * Get the next token or string. This is used in parsing HTTP headers.
     * @return A String.
     * @throws JSONException if a syntax error occurs
     */
    public String nextToken() throws JSONException {
        char c = nextNonWhitespace();
        if (c == '"' || c == '\'') {
            return nextQuotedToken(c);
        }
        return nextUnquotedToken(c);
    }

    private char nextNonWhitespace() {
        char c;
        do {
            c = next();
        } while (Character.isWhitespace(c));
        return c;
    }

    private String nextQuotedToken(char quote) {
        StringBuilder sb = new StringBuilder();
        for (;;) {
            char c = next();
            if (c < ' ') {
                throw syntaxError("Unterminated string.");
            }
            if (c == quote) {
                return sb.toString();
            }
            sb.append(c);
        }
    }

    private String nextUnquotedToken(char c) {
        StringBuilder sb = new StringBuilder();
        while (c != 0 && !Character.isWhitespace(c)) {
            sb.append(c);
            c = next();
        }
        return sb.toString();
    }
}
