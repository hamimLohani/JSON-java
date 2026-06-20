package org.json;

import static java.lang.String.format;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/*
Public Domain.
*/

/**
 * A JSON Pointer is a simple query language defined for JSON documents by
 * <a href="https://tools.ietf.org/html/rfc6901">RFC 6901</a>.
 * 
 * In a nutshell, JSONPointer allows the user to navigate into a JSON document
 * using strings, and retrieve targeted objects, like a simple form of XPATH.
 * Path segments are separated by the '/' char, which signifies the root of
 * the document when it appears as the first char of the string. Array 
 * elements are navigated using ordinals, counting from 0. JSONPointer strings
 * may be extended to any arbitrary number of segments. If the navigation
 * is successful, the matched item is returned. A matched item may be a
 * JSONObject, a JSONArray, or a JSON value. If the JSONPointer string building 
 * fails, an appropriate exception is thrown. If the navigation fails to find
 * a match, a JSONPointerException is thrown. 
 * 
 * @author JSON.org
 * @version 2016-05-14
 */
public class JSONPointer {

    // used for URL encoding and decoding
    private static final String ENCODING = "utf-8";

    /**
     * This class allows the user to build a JSONPointer in steps, using
     * exactly one segment in each step.
     */
    public static class Builder {

        /**
         * Constructs a new Builder object.
         */
        public Builder() {
        }

        // Segments for the eventual JSONPointer string
        private final List<String> refTokens = new ArrayList<String>();

        /**
         * Creates a {@code JSONPointer} instance using the tokens previously set using the
         * {@link #append(String)} method calls.
         * @return a JSONPointer object
         */
        public JSONPointer build() {
            return new JSONPointer(this.refTokens);
        }

        /**
         * Adds an arbitrary token to the list of reference tokens. It can be any non-null value.
         * 
         * Unlike in the case of JSON string or URI fragment representation of JSON pointers, the
         * argument of this method MUST NOT be escaped. If you want to query the property called
         * {@code "a~b"} then you should simply pass the {@code "a~b"} string as-is, there is no
         * need to escape it as {@code "a~0b"}.
         * 
         * @param token the new token to be appended to the list
         * @return {@code this}
         * @throws NullPointerException if {@code token} is null
         */
        public Builder append(String token) {
            this.refTokens.add(Objects.requireNonNull(token, "token cannot be null"));
            return this;
        }

        /**
         * Adds an integer to the reference token list. Although not necessarily, mostly this token will
         * denote an array index. 
         * 
         * @param arrayIndex the array index to be added to the token list
         * @return {@code this}
         */
        public Builder append(int arrayIndex) {
            this.refTokens.add(String.valueOf(arrayIndex));
            return this;
        }
    }

    /**
     * Static factory method for {@link Builder}. Example usage:
     * 
     * <pre><code>
     * JSONPointer pointer = JSONPointer.builder()
     *       .append("obj")
     *       .append("other~key").append("another/key")
     *       .append("\"")
     *       .append(0)
     *       .build();
     * </code></pre>
     * 
     *  @return a builder instance which can be used to construct a {@code JSONPointer} instance by chained
     *  {@link Builder#append(String)} calls.
     */
    public static Builder builder() {
        return new Builder();
    }

    // Segments for the JSONPointer string
    private final List<String> refTokens;

    /**
     * Pre-parses and initializes a new {@code JSONPointer} instance. If you want to
     * evaluate the same JSON Pointer on different JSON documents then it is recommended
     * to keep the {@code JSONPointer} instances due to performance considerations.
     * 
     * @param pointer the JSON String or URI Fragment representation of the JSON pointer.
     * @throws IllegalArgumentException if {@code pointer} is not a valid JSON pointer
     */
    public JSONPointer(final String pointer) {
        Objects.requireNonNull(pointer, "pointer cannot be null");
        if (pointer.isEmpty() || "#".equals(pointer)) {
            this.refTokens = Collections.emptyList();
            return;
        }
        String refs = refsFromPointer(pointer);
        this.refTokens = new ArrayList<String>();
        parseReferenceTokens(refs);
        // using split does not take into account consecutive separators or "ending nulls"
        //for (String token : refs.split("/")) {
        //    this.refTokens.add(unescape(token));
        //}
    }

    private static String refsFromPointer(String pointer) {
        if (pointer.startsWith("#/")) {
            try {
                return URLDecoder.decode(pointer.substring(2), ENCODING);
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(ENCODING + " is not supported", e);
            }
        }
        if (pointer.startsWith("/")) {
            return pointer.substring(1);
        }
        throw new IllegalArgumentException("a JSON pointer should start with '/' or '#/'");
    }

    private void parseReferenceTokens(String refs) {
        int slashIdx = -1;
        int prevSlashIdx;
        do {
            prevSlashIdx = slashIdx + 1;
            slashIdx = refs.indexOf('/', prevSlashIdx);
            if (prevSlashIdx == slashIdx || prevSlashIdx == refs.length()) {
                // Found 2 slashes in a row ( obj//next ) or a slash at the end ( obj/test/ ).
                this.refTokens.add("");
            } else {
                int end = slashIdx >= 0 ? slashIdx : refs.length();
                this.refTokens.add(unescape(refs.substring(prevSlashIdx, end)));
            }
        } while (slashIdx >= 0);
    }

    /**
     * Constructs a new JSONPointer instance with the provided list of reference tokens.
     *
     * @param refTokens A list of strings representing the reference tokens for the JSON Pointer.
     *                  Each token identifies a step in the path to the targeted value.
     */
    public JSONPointer(List<String> refTokens) {
        this.refTokens = new ArrayList<String>(refTokens);
    }

    /**
     * @see <a href="https://tools.ietf.org/html/rfc6901#section-3">rfc6901 section 3</a>
     */
    private static String unescape(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    /**
     * Evaluates this JSON Pointer on the given {@code document}. The {@code document}
     * is usually a {@link JSONObject} or a {@link JSONArray} instance, but the empty
     * JSON Pointer ({@code ""}) can be evaluated on any JSON values and in such case the
     * returned value will be {@code document} itself. 
     * 
     * @param document the JSON document which should be the subject of querying.
     * @return the result of the evaluation
     * @throws JSONPointerException if an error occurs during evaluation
     */
    public Object queryFrom(Object document) throws JSONPointerException {
        if (this.refTokens.isEmpty()) {
            return document;
        }
        Object current = document;
        for (String token : this.refTokens) {
            if (current instanceof JSONObject) {
                current = ((JSONObject) current).opt(unescape(token));
            } else if (current instanceof JSONArray) {
                current = readByIndexToken(current, token);
            } else {
                throw new JSONPointerException(format(
                        "value [%s] is not an array or object therefore its key %s cannot be resolved", current,
                        token));
            }
        }
        return current;
    }

    /**
     * Matches a JSONArray element by ordinal position
     * @param current the JSONArray to be evaluated
     * @param indexToken the array index in string form
     * @return the matched object. If no matching item is found a
     * @throws JSONPointerException is thrown if the index is out of bounds
     */
    private static Object readByIndexToken(Object current, String indexToken) throws JSONPointerException {
        Integer index = parseArrayIndex(indexToken);
        if (index == null) {
            throw new JSONPointerException(format("%s is not an array index", indexToken));
        }
        JSONArray currentArr = (JSONArray) current;
        if (index.intValue() < 0) {
            throw new JSONPointerException("Error reading value at index position " + index);
        }
        if (index.intValue() >= currentArr.length()) {
            throw new JSONPointerException(format("index %s is out of bounds - the array has %d elements", indexToken,
                    Integer.valueOf(currentArr.length())));
        }
        return currentArr.get(index.intValue());
    }

    private static Integer parseArrayIndex(String indexToken) {
        if (!indexToken.matches("-?\\d+")) {
            return null;
        }
        int start = indexToken.charAt(0) == '-' ? 1 : 0;
        long maxIndex = start == 0 ? Integer.MAX_VALUE : -(long) Integer.MIN_VALUE;
        long index = 0;
        for (int i = start; i < indexToken.length(); i += 1) {
            index = index * 10 + indexToken.charAt(i) - '0';
            if (index > maxIndex) {
                return null;
            }
        }
        return Integer.valueOf(start == 0 ? (int) index : (int) -index);
    }

    /**
     * Returns a string representing the JSONPointer path value using string
     * representation
     */
    @Override
    public String toString() {
        StringBuilder rval = new StringBuilder();
        for (String token: this.refTokens) {
            rval.append('/').append(escape(token));
        }
        return rval.toString();
    }

    /**
     * Escapes path segment values to an unambiguous form.
     * The escape char to be inserted is '~'. The chars to be escaped 
     * are ~, which maps to ~0, and /, which maps to ~1.
     * @param token the JSONPointer segment value to be escaped
     * @return the escaped value for the token
     * 
     * @see <a href="https://tools.ietf.org/html/rfc6901#section-3">rfc6901 section 3</a>
     */
    private static String escape(String token) {
        return token.replace("~", "~0")
                .replace("/", "~1");
    }

    /**
     * Returns a string representing the JSONPointer path value using URI
     * fragment identifier representation
     * @return a uri fragment string
     */
    public String toURIFragment() {
        try {
            StringBuilder rval = new StringBuilder("#");
            for (String token : this.refTokens) {
                rval.append('/').append(URLEncoder.encode(token, ENCODING));
            }
            return rval.toString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(ENCODING + " is not supported", e);
        }
    }
    
}
