package technology.tabula;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

public class ObjectExtractor implements java.io.Closeable {

    private final PDDocument pdfDocument;
    private final OperatorExceptionHandler handler;

    public ObjectExtractor(PDDocument pdfDocument) {
        this(pdfDocument, null);
    }

    /**
     * Creates an extractor with an {@link OperatorExceptionHandler} to take control of
     * operator exceptions (e.g. {@code MissingOperandException} from a corrupt content
     * stream). The handler is applied to both the stream engine and the text stripper.
     * <p>
     * 创建提取器并注入 {@link OperatorExceptionHandler}，接管操作符异常处理
     * （如内容流损坏导致的 {@code MissingOperandException}）。回调对提取引擎
     * （ObjectExtractorStreamEngine）与文本提取器（TextStripper）均生效。
     *
     * @param pdfDocument the PDF document to extract from
     * @param handler the operator exception handler, or {@code null} to keep the default behaviour
     */
    public ObjectExtractor(PDDocument pdfDocument, OperatorExceptionHandler handler) {
        this.pdfDocument = pdfDocument;
        this.handler = handler;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    protected Page extractPage(Integer pageNumber) throws IOException {
        if (pageNumber > pdfDocument.getNumberOfPages() || pageNumber < 1) {
            throw new java.lang.IndexOutOfBoundsException("Page number does not exist.");
        }
        PDPage page = pdfDocument.getPage(pageNumber - 1);

        ObjectExtractorStreamEngine streamEngine = new ObjectExtractorStreamEngine(page, handler);
        streamEngine.processPage(page);

        TextStripper textStripper = new TextStripper(pdfDocument, pageNumber, handler);
        textStripper.process();

        Utils.sort(textStripper.getTextElements(), Rectangle.ILL_DEFINED_ORDER);

        float width, height;
        int rotation = page.getRotation();
        if (Math.abs(rotation) == 90 || Math.abs(rotation) == 270) {
            width = page.getCropBox().getHeight();
            height = page.getCropBox().getWidth();
        } else {
            width = page.getCropBox().getWidth();
            height = page.getCropBox().getHeight();
        }

        return Page.Builder.newInstance()
                .withPageDims(PageDims.of(0, 0, width, height))
                .withRotation(rotation)
                .withNumber(pageNumber)
                .withPdPage(page)
                .withPdDocument(pdfDocument)
                .withRulings(streamEngine.rulings)
                .withTextElements(textStripper.getTextElements())
                .withMinCharWidth(textStripper.getMinCharWidth())
                .withMinCharHeight(textStripper.getMinCharHeight())
                .withIndex(textStripper.getSpatialIndex())
                .build();
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    public PageIterator extract(Iterable<Integer> pages) {
        return new PageIterator(this, pages);
    }

    public PageIterator extract() {
        return extract(Utils.range(1, pdfDocument.getNumberOfPages() + 1));
    }

    public Page extract(int pageNumber) {
        return extract(Utils.range(pageNumber, pageNumber + 1)).next();
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    public void close() throws IOException {
        pdfDocument.close();
    }
    
}
