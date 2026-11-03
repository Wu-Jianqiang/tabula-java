package technology.tabula;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;

/**
 * Callback for handling operator exceptions thrown while processing a page's content
 * stream. PDFBox's default behaviour ({@code PDFTextStripper#operatorException} /
 * {@code PDFGraphicsStreamEngine#operatorException}) is to log and then continue or
 * re-throw depending on the exception type — e.g. a {@code MissingOperandException}
 * caused by a corrupt content stream is only logged as an ERROR and silently degraded.
 * <p>
 * When a handler is injected via {@link ObjectExtractor}'s constructor, it receives
 * every exception thrown while a known operator is being processed (exceptions for
 * <em>unknown operators</em> are raised separately by PDFBox and are not delivered
 * here) and decides how to proceed: return {@code true} to continue with the default
 * PDFBox behaviour ({@code super.operatorException}), return {@code false} to fully
 * handle the exception and skip the default, or throw an exception to abort processing.
 * Without a handler, the default PDFBox behaviour is kept unchanged.
 * <p>
 * Call-side notes: the {@code operands} come from the untrusted PDF content stream —
 * do not log or send their content (log the operator name instead); when returning
 * {@code false}, distinguish exception types and re-throw genuine IO failures instead
 * of swallowing them.
 * <p>
 * 操作符异常处理回调：PDFBox 默认对操作符执行异常（如内容流损坏导致的
 * {@code MissingOperandException}）打 ERROR 日志后静默降级。调用方通过
 * {@link ObjectExtractor} 构造注入本回调后，已注册操作符执行期间抛出的异常都会先交给
 * 回调决定如何继续（未知操作符的异常由 PDFBox 单独抛出，不经本回调）：返回 {@code true}
 * 继续 PDFBox 默认行为（{@code super.operatorException}）、返回 {@code false} 完全接管
 * 跳过默认、抛出异常则中止处理。未注入时保持 PDFBox 默认行为不变。
 * <p>
 * 调用方注意：{@code operands} 来自不可信的 PDF 内容流——不得将其内容写日志或外发
 * （需要记录时只记操作符名称）；返回 {@code false} 前应甄别异常类型，真实 IO 故障应
 * 重新抛出而非吞掉。
 */
@FunctionalInterface
public interface OperatorExceptionHandler {

    /**
     * Handles an operator exception.
     *
     * @param operator the failing operator
     * @param operands the operands passed to the operator
     * @param exception the exception thrown by the operator
     * @return {@code true} to continue with the default PDFBox behaviour, {@code false} to
     *         fully handle the exception and skip the default
     * @throws IOException may be thrown by the handler to abort processing
     */
    boolean handle(Operator operator, List<COSBase> operands, IOException exception) throws IOException;
}
