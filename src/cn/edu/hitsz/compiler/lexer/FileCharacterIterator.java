package cn.edu.hitsz.compiler.lexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author chiro
 */
public class FileCharacterIterator implements Iterable<Character>, Iterator<Character> {
    private final FileCharacterIteratorData data;

    private final List<Character> buffer;
    private final int bufferSize;

    private FileCharacterIterator(FileCharacterIteratorData data) {
        this.data = data;
        bufferSize = 0;
        buffer = new LinkedList<>();
    }

    private FileCharacterIterator(FileCharacterIteratorData data, int bufferSize) {
        this.data = data;
        buffer = new LinkedList<>();
        this.bufferSize = bufferSize;
        for (int i = 0; i < bufferSize; i++) {
            buffer.add(data.current());
            data.next();
        }
    }

    public FileCharacterIterator(FileCharacterIteratorData data, List<Character> buffer, int bufferSize) {
        this.data = data;
        this.buffer = buffer;
        this.bufferSize = bufferSize;
    }

    @Override
    public Iterator<Character> iterator() {
        return new FileCharacterIterator(data, buffer, bufferSize);
    }

    static public FileCharacterIterator build(String path) throws IOException {
        return FileCharacterIterator.build(path, 0);
    }

    static public FileCharacterIterator build(String path, int bufferSize) throws IOException {
        return new FileCharacterIterator(new FileCharacterIteratorData(Files.newBufferedReader(Paths.get(path))), bufferSize);
    }

    @Override
    public boolean hasNext() {
        if (bufferSize == 0) {
            return data.current() != FileCharacterIteratorData.DONE;
        } else {
            return buffer.get(0) != FileCharacterIteratorData.DONE;
        }
    }

    @Override
    public Character next() {
        if (bufferSize == 0) {
            Character c = data.current();
            data.next();
            return c;
        } else {
            Character c = data.current();
            data.next();
            buffer.remove(0);
            buffer.add(c);
            return buffer.get(0);
        }
    }

    public Character current() {
        if (bufferSize == 0) {
            return data.current();
        } else {
            return buffer.get(0);
        }
    }

    public Character current(int index) {
        if (bufferSize == 0) {
            return current();
        }
        return buffer.get(index);
    }
}
