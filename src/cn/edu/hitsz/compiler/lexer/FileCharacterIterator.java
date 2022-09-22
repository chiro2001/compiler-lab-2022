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
    private final FileCharacterIteratorData dataIterator;

    private final List<Character> buffer;
    private int bufferOffset = 0;
    private final int bufferSize;
    private final static int BUFFER_PRELOAD_BATCH = 2;

    private FileCharacterIterator(FileCharacterIteratorData dataIterator) {
        this.dataIterator = dataIterator;
        bufferSize = 0;
        buffer = new LinkedList<>();
    }

    private FileCharacterIterator(FileCharacterIteratorData dataIterator, int bufferSize) {
        this.dataIterator = dataIterator;
        buffer = new LinkedList<>();
        this.bufferSize = bufferSize;
        for (int i = 0; i < bufferSize; i++) {
            buffer.add(FileCharacterIteratorData.DONE);
        }
        maintenance();
    }

    public FileCharacterIterator(FileCharacterIteratorData dataIterator, List<Character> buffer, int bufferSize) {
        this.dataIterator = dataIterator;
        this.buffer = buffer;
        this.bufferSize = bufferSize;
    }

    @Override
    public Iterator<Character> iterator() {
        return new FileCharacterIterator(dataIterator, buffer, bufferSize);
    }

    static public FileCharacterIterator build(String path) throws IOException {
        return FileCharacterIterator.build(path, 0);
    }

    static public FileCharacterIterator build(String path, int bufferSize) throws IOException {
        return new FileCharacterIterator(new FileCharacterIteratorData(Files.newBufferedReader(Paths.get(path))), bufferSize);
    }

    private void updateBuffer() {
        if (bufferSize == 0) {
            return;
        }
        for (int i = 0; i < bufferSize; i++) {
            buffer.add(dataIterator.current());
            dataIterator.next();
        }
    }

    private void maintenance() {
        while (buffer.size() < (BUFFER_PRELOAD_BATCH + 1) * bufferSize) {
            updateBuffer();
        }
    }

    @Override
    public boolean hasNext() {
        if (bufferSize == 0) {
            return dataIterator.current() != FileCharacterIteratorData.DONE;
        } else {
            return buffer.get(bufferOffset + bufferSize) != FileCharacterIteratorData.DONE;
        }
    }

    @Override
    public Character next() {
        if (bufferSize == 0) {
            Character c = dataIterator.current();
            dataIterator.next();
            return c;
        } else {
            bufferOffset += 1;
            if (bufferOffset >= bufferSize) {
                buffer.subList(0, bufferSize).clear();
                bufferOffset %= bufferSize;
            }
            maintenance();
            return buffer.get(bufferOffset + bufferSize);
        }
    }

    public Character current() {
        if (bufferSize == 0) {
            return dataIterator.current();
        } else {
            return buffer.get(bufferOffset + bufferSize);
        }
    }

    public Character current(int index) {
        if (bufferSize == 0) {
            return current();
        }
        while (index + bufferSize + bufferOffset >= buffer.size()) {
            updateBuffer();
        }
        return buffer.get(index + bufferOffset + bufferSize);
    }

    public Character last(int index) {
        assert bufferSize != 0;
        return buffer.get(bufferOffset + bufferSize - index);
    }

    public List<Character> getBuffer() {
        return buffer;
    }
}
