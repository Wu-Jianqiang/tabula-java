package technology.tabula;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.junit.Test;


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link OperatorExceptionHandler}: the injected callback takes control of
 * operator exceptions (e.g. {@code MissingOperandException} from a corrupt content
 * stream), while the default behaviour (no handler) is kept.
 * <p>
 * {@link OperatorExceptionHandler} 行为测试：注入回调可接管操作符异常
 * （如内容流损坏导致的 {@code MissingOperandException}）；未注入时保持默认行为。
 */
public class TestOperatorExceptionHandler {

    /**
     * Builds a minimal PDF whose content stream is "BT Tm ET" — the Tm operator lacks its
     * 6 operands, which makes PDFBox raise {@code MissingOperandException} during extraction.
     *
     * @return a minimal one-page {@link PDDocument} with the damaged content stream
     */
    private PDDocument buildDamagedDocument() throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage();
        doc.addPage(page);
        page.setContents(new PDStream(doc, new ByteArrayInputStream("BT Tm ET".getBytes(StandardCharsets.UTF_8))));
        return doc;
    }

    @Test
    public void testDefaultBehaviourDoesNotThrow() throws Exception {
        // No handler: MissingOperandException is logged by PDFBox and silently degraded
        // 未注入回调：MissingOperandException 由 PDFBox 记 ERROR 日志后静默降级
        try (PDDocument doc = buildDamagedDocument();
             ObjectExtractor extractor = new ObjectExtractor(doc)) {
            Page page = extractor.extract(1);
            assertNotNull(page);
        }
    }

    @Test
    public void testHandlerReceivesMissingOperandException() throws Exception {
        final int[] count = {0};
        try (PDDocument doc = buildDamagedDocument();
             ObjectExtractor extractor = new ObjectExtractor(doc, (operator, operands, e) -> {
                 count[0]++;
                 // continue with the default PDFBox behaviour
                 // 继续 PDFBox 默认行为
                 return true;
             })) {
            Page page = extractor.extract(1);
            assertNotNull(page);
            assertTrue("handler should be invoked for the missing operand", count[0] > 0);
        }
    }

    @Test
    public void testHandlerCanRethrow() throws Exception {
        try (PDDocument doc = buildDamagedDocument();
             ObjectExtractor extractor = new ObjectExtractor(doc, (operator, operands, e) -> {
                 throw e;
             })) {
            try {
                extractor.extract(1);
                fail("extraction should fail when the handler re-throws");
            } catch (RuntimeException ex) {
                // PageIterator wraps IOException in UncheckedIOException
                // PageIterator 将 IOException 包装为 UncheckedIOException
                Throwable cause = ex.getCause();
                assertNotNull("cause chain should be preserved", cause);
                assertTrue("cause should be the original IOException", cause instanceof IOException);
            }
        }
    }

    /**
     * Anchors the "return false skips the default" branch on both injection points with a
     * directly distinguishable scenario: a plain IOException is re-thrown by the default
     * path (only MissingOperandException is degraded), so handler=false must not escape.
     * <p>
     * 用可区分场景锚定两处注入点的"返回 false 跳过默认"分支：普通 IOException 走默认路径
     * 会被重新抛出（仅 MissingOperandException 降级），返回 false 后异常不得外抛。
     */
    @Test
    public void testHandlerCanSkipDefaultBehaviour() throws Exception {
        try (PDDocument doc = buildDamagedDocument()) {
            IOException ioError = new IOException("plain io failure");
            Operator tm = Operator.getOperator("Tm");

            // stream engine injection point: false must swallow, true must re-throw
            // 流提取引擎注入点：false 必须吞掉，true 必须走默认 rethrow
            ObjectExtractorStreamEngine engine =
                    new ObjectExtractorStreamEngine(doc.getPage(0), (operator, operands, e) -> false);
            engine.operatorException(tm, Collections.emptyList(), ioError);

            ObjectExtractorStreamEngine defaultEngine =
                    new ObjectExtractorStreamEngine(doc.getPage(0), (operator, operands, e) -> true);
            try {
                defaultEngine.operatorException(tm, Collections.emptyList(), ioError);
                fail("default behaviour should re-throw a plain IOException");
            } catch (IOException expected) {
                assertSame("default path should re-throw the original exception", ioError, expected);
            }

            // text stripper injection point: same two-branch anchor
            // 文本提取器注入点：同样双向锚定
            TextStripper stripper = new TextStripper(doc, 1, (operator, operands, e) -> false);
            stripper.operatorException(tm, Collections.emptyList(), ioError);

            TextStripper defaultStripper = new TextStripper(doc, 1, (operator, operands, e) -> true);
            try {
                defaultStripper.operatorException(tm, Collections.emptyList(), ioError);
                fail("default behaviour should re-throw a plain IOException");
            } catch (IOException expected) {
                assertSame("default path should re-throw the original exception", ioError, expected);
            }
        }
    }
}
