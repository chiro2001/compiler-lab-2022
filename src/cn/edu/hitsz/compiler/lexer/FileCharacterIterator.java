package cn.edu.hitsz.compiler.lexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.StringCharacterIterator;

public class FileCharacterIterator {
    static char DONE = StringCharacterIterator.DONE;
    private String line = null;
    private StringCharacterIterator iterator = null;
    private final BufferedReader reader;

    FileCharacterIterator(BufferedReader reader) {
        this.reader = reader;
    }

    public char current() {
        try {
            if (iterator != null) {
                if (iterator.current() != StringCharacterIterator.DONE) {
                    return iterator.current();
                } else {
                    line = null;
                }
            }
            if (line == null) {
                line = reader.readLine();
                if (line == null) {
                    return DONE;
                }
            }
            iterator = new StringCharacterIterator(line);
            return iterator.current();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void next() {
        try {
            if (iterator != null) {
                if (iterator.current() != StringCharacterIterator.DONE) {
                    iterator.next();
                } else {
                    line = null;
                }
            }
            if (line == null) {
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
