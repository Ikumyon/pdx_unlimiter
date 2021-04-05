package com.crschnick.pdx_unlimiter.core.parser;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.SentryLevel;

import java.util.Stack;

public class TextFormatTokenizer {

    public static final byte STRING_UNQUOTED = 1;
    public static final byte STRING_QUOTED = 2;
    public static final byte OPEN_GROUP = 3;
    public static final byte CLOSE_GROUP = 4;
    public static final byte EQUALS = 5;

    private static final byte DOUBLE_QUOTE_CHAR = 34;

    private final byte[] bytes;
    private final byte[] tokenTypes;
    private final int[] scalarsStart;
    private final short[] scalarsLength;
    private final Stack<Integer> arraySizeStack;
    private final int[] arraySizes;
    private boolean isInQuotes;
    private boolean isInComment;
    private int nextScalarStart;
    private int i;
    private int tokenCounter;
    private int scalarCounter;
    private int arraySizesCounter;
    private boolean escapeChar;

    public TextFormatTokenizer(byte[] bytes) {
        this.bytes = bytes;
        this.nextScalarStart = 0;
        this.tokenCounter = 0;

        int maxTokenCount;
        int maxNodeCount;
        if (bytes.length < 300) {
            // Special case for small files

            // Add 2 to include open and close group tokens that are always added
            maxTokenCount = bytes.length + 2;

            // Add 1 in case bytes.length is 0. We then still have one empty array node
            maxNodeCount = bytes.length + 1;
        } else {
            // Pessimistic assumptions, should always hold!

            maxTokenCount = bytes.length / 2;
            maxNodeCount = bytes.length / 5;
        }

        this.tokenTypes = new byte[maxTokenCount];
        this.scalarsStart = new int[maxNodeCount];
        this.scalarsLength = new short[maxNodeCount];
        this.arraySizes = new int[maxNodeCount];

        this.arraySizeStack = new Stack<>();
        this.arraySizesCounter = 0;
    }

    public void tokenize() {
        try {
            tokenTypes[0] = OPEN_GROUP;
            arraySizes[0] = 0;
            arraySizeStack.add(0);
            arraySizesCounter++;
            tokenCounter = 1;
            for (i = 0; i <= bytes.length; i++) {
                tokenizeIteration();
            }
            tokenTypes[tokenCounter] = CLOSE_GROUP;
        } catch (Throwable t) {
            var bc = new Breadcrumb(t.toString());
            bc.setLevel(SentryLevel.ERROR);
            bc.setData("offset", i);
            bc.setData("tokens", tokenCounter);
            bc.setData("scalars", scalarCounter);
            Sentry.addBreadcrumb(bc);
            throw t;
        }
    }

    private void moveScalarStartToNext() {
        nextScalarStart = i + 1;
    }

    private boolean checkCommentCase(char c) {
        if (isInQuotes) {
            return false;
        }

        if (!isInComment) {
            if (c == '#') {
                isInComment = true;
                finishCurrentToken();
                return true;
            } else {
                return false;
            }
        }

        if (c == '\n') {
            isInComment = false;
            moveScalarStartToNext();
        }
        return true;
    }

    private boolean checkQuoteCase(char c) {
        if (!isInQuotes) {
            if (c == '"') {
                isInQuotes = true;
                finishCurrentToken();
                return true;
            }

            return false;
        }

        boolean hasSeenEscapeChar = escapeChar;
        if (hasSeenEscapeChar) {
            escapeChar = false;
            if (c == '"' || c == '\\') {
                return true;
            }
        } else {
            escapeChar = c == '\\';
            if (c == '"') {
                isInQuotes = false;
            }
        }

        return true;
    }

    private void finishCurrentToken() {
        boolean isCurrentToken = nextScalarStart < i;
        if (!isCurrentToken) {
            return;
        }

        short length = (short) ((i - 1) - nextScalarStart + 1);

        // Check for length overflow
        if (length < 0) {
            throw new IndexOutOfBoundsException(
                    "Encountered scalar with length " + ((i - 1) - nextScalarStart + 1) + ", which is too big");
        }

        assert length > 0: "Scalar must be of length at least 1";

        if (bytes[nextScalarStart] == DOUBLE_QUOTE_CHAR && bytes[i - 1] == DOUBLE_QUOTE_CHAR) {
            tokenTypes[tokenCounter++] = STRING_QUOTED;
        } else {
            tokenTypes[tokenCounter++] = STRING_UNQUOTED;
        }
        scalarsStart[scalarCounter] = nextScalarStart;
        scalarsLength[scalarCounter] = length;
        scalarCounter++;

        assert arraySizeStack.size() > 0: "Encountered unexpectedly large array at index " + i;
        arraySizes[arraySizeStack.peek()]++;

        nextScalarStart = i;
    }

    private void checkForNewControlToken(byte controlToken) {
        if (controlToken == 0) {
            return;
        }

        finishCurrentToken();
        moveScalarStartToNext();

        if (controlToken == CLOSE_GROUP) {
            assert arraySizeStack.size() > 0 : "Encountered an additional close group token at " + i;
            arraySizeStack.pop();
        } else if (controlToken == EQUALS) {
            arraySizes[arraySizeStack.peek()]--;
        } else if (controlToken == OPEN_GROUP) {
            arraySizes[arraySizeStack.peek()]++;
            arraySizeStack.add(arraySizesCounter++);
        }

        tokenTypes[tokenCounter++] = controlToken;
    }

    private void checkWhitespace(char c) {
        boolean isWhitespace = (c == '\n' || c == '\r' || c == ' ' || c == '\t');
        if (isWhitespace) {
            finishCurrentToken();
            moveScalarStartToNext();
        }
    }

    private void tokenizeIteration() {
        // Add extra new line at the end to simulate end of token
        char c = i == bytes.length ? '\n' : (char) bytes[i];

        if (checkCommentCase(c)) return;
        if (checkQuoteCase(c)) return;

        byte controlToken = 0;
        if (c == '{') {
            controlToken = OPEN_GROUP;
        } else if (c == '}') {
            controlToken = CLOSE_GROUP;
        } else if (c == '=') {
            controlToken = EQUALS;
        }

        checkForNewControlToken(controlToken);
        checkWhitespace(c);
    }

    public byte[] getTokenTypes() {
        return tokenTypes;
    }

    public int[] getArraySizes() {
        return arraySizes;
    }

    public int[] getScalarsStart() {
        return scalarsStart;
    }

    public short[] getScalarsLength() {
        return scalarsLength;
    }

    public int getScalarCount() {
        return scalarCounter;
    }
}
