package cn.edu.hitsz.compiler.lexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;

/**
 * @author chiro
 */
public class FileCharacterIterator implements Iterable<Character>, Iterator<Character> {
    private final FileCharacterIteratorData data;

    private FileCharacterIterator(FileCharacterIteratorData data) {
        this.data = data;
    }

    @Override
    public Iterator<Character> iterator() {
        return new FileCharacterIterator(data.next());
    }

    static public FileCharacterIterator build(String path) throws IOException {
        return new FileCharacterIterator(new FileCharacterIteratorData(Files.newBufferedReader(Paths.get(path))));
    }

    @Override
    public boolean hasNext() {
        return data.current() != FileCharacterIteratorData.DONE;
    }

    @Override
    public Character next() {
        Character c = data.current();
        data.next();
        return c;
    }

    public Character current() {
        return data.current();
    }
}
