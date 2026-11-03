package technology.tabula;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

public class PageIterator implements Iterator<Page> {

    private ObjectExtractor objectExtractor;
    private Iterator<Integer> pageIndexIterator;

    public PageIterator(ObjectExtractor objectExtractor, Iterable<Integer> pages) {
        super();
        this.objectExtractor = objectExtractor;
        this.pageIndexIterator = pages.iterator();
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    @Override
    public boolean hasNext() {
        return pageIndexIterator.hasNext();
    }

    /**
     * Extracts the next page; an {@link IOException} is wrapped in
     * {@link UncheckedIOException}.
     * <p>
     * 提取下一页：{@link IOException} 包装为 {@link UncheckedIOException} 抛出。
     */
    @Override
    public Page next() {
        if (!this.hasNext()) {
            throw new IllegalStateException();
        }
        int pageNumber = pageIndexIterator.next();
        try {
            return objectExtractor.extractPage(pageNumber);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract PDF page " + pageNumber, e);
        }
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

}
