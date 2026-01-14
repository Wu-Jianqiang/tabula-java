package technology.tabula;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.awt.geom.PathIterator.*;

class ObjectExtractorStreamEngine extends PDFGraphicsStreamEngine {

    protected List<Ruling> rulings;
    private AffineTransform pageTransform;
    private boolean extractRulingLines = true;
    private Logger logger;
    private int clipWindingRule = -1;
    private GeneralPath currentPath = new GeneralPath();

    private static final float RULING_MINIMUM_LENGTH = 0.01f;

    protected ObjectExtractorStreamEngine(PDPage page) {
        super(page);
        logger = LoggerFactory.getLogger(ObjectExtractorStreamEngine.class);
        rulings = new ArrayList<>();

        // Calculate page transform:
        pageTransform = new AffineTransform();
        PDRectangle pageCropBox = getPage().getCropBox();
        int rotationAngleInDegrees = getPage().getRotation();

        if (Math.abs(rotationAngleInDegrees) == 90 || Math.abs(rotationAngleInDegrees) == 270) {
            double rotationAngleInRadians = rotationAngleInDegrees * (Math.PI / 180.0);
            pageTransform = AffineTransform.getRotateInstance(rotationAngleInRadians, 0, 0);
        } else {
            double deltaX = 0;
            double deltaY = pageCropBox.getHeight();
            pageTransform.concatenate(AffineTransform.getTranslateInstance(deltaX, deltaY));
        }

        pageTransform.concatenate(AffineTransform.getScaleInstance(1, -1));
        pageTransform.translate(-pageCropBox.getLowerLeftX(), -pageCropBox.getLowerLeftY());
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        currentPath.moveTo((float) p0.getX(), (float) p0.getY());
        currentPath.lineTo((float) p1.getX(), (float) p1.getY());
        currentPath.lineTo((float) p2.getX(), (float) p2.getY());
        currentPath.lineTo((float) p3.getX(), (float) p3.getY());
        currentPath.closePath();
    }

    @Override
    public void clip(int windingRule) {
        // The clipping path will not be updated until the succeeding painting
        // operator is called.
        clipWindingRule = windingRule;
    }

    @Override
    public void closePath() {
        currentPath.closePath();
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        currentPath.curveTo(x1, y1, x2, y2, x3, y3);
    }

    @Override
    public void drawImage(PDImage arg0) {}

    @Override
    public void endPath() {
        if (clipWindingRule != -1) {
            currentPath.setWindingRule(clipWindingRule);
            getGraphicsState().intersectClippingPath(currentPath);
            clipWindingRule = -1;
        }
        currentPath.reset();
    }

    @Override
    public void fillAndStrokePath(int arg0) {
        strokeOrFillPath(true, true);
    }

    @Override
    public void fillPath(int arg0) {
        strokeOrFillPath(false, true);
    }

    @Override
    public Point2D getCurrentPoint() {
        return currentPath.getCurrentPoint();
    }

    @Override
    public void lineTo(float x, float y) {
        currentPath.lineTo(x, y);
    }

    @Override
    public void moveTo(float x, float y) {
        currentPath.moveTo(x, y);
    }

    @Override
    public void shadingFill(COSName arg0) {}

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    @Override
    public void strokePath()  {
        strokeOrFillPath(true, false);
    }

    private void strokeOrFillPath(boolean isStroke, boolean isFill) {
        if (!extractRulingLines) {
            currentPath.reset();
            return;
        }

        boolean didNotPassedTheFilter = filterPathBySegmentType(isStroke, isFill);
        if (didNotPassedTheFilter) return;

        // TODO: how to implement color filter?

        // Skip the first path operation and save it as the starting point.
        PathIterator pathIterator = currentPath.getPathIterator(getPageTransform());

        float[] coordinates = new float[6];
        int currentSegment;

        Point2D.Float startPoint = getStartPoint(pathIterator);
        Point2D.Float last_move = startPoint;
        Point2D.Float endPoint = null;
        Line2D.Float line;
        PointComparator pointComparator = new PointComparator();

        // next() moves the iterator to the next segment after processing the current one,
        // without affecting the current segment processing.
        // next() 在处理完当前段后调用：移动指针到下一个段，不会影响当前段的处理。
        pathIterator.next();

        // If there are no more segments after calling next(),
        // isDone() will return true in the next iteration, exiting the loop.
        // 如果 next() 被调用后没有更多段，下次循环时 isDone() 会返回 true，退出循环。
        while (!pathIterator.isDone()) {
            // This can be the last segment, when pathIterator.isDone, but we need to
            // process it otherwise us-017.pdf fails the last value.
            try {
                currentSegment = pathIterator.currentSegment(coordinates);
            } catch (IndexOutOfBoundsException ex) {
                pathIterator.next();
                continue;
            }
            switch (currentSegment) {
                case SEG_LINETO:
                    endPoint = new Point2D.Float(coordinates[0], coordinates[1]);
                    if (startPoint == null || endPoint == null) {
                        break;
                    }
                    line = getLineBetween(startPoint, endPoint, pointComparator);
                    verifyLineIntersectsClipping(line);
                    break;
                case SEG_MOVETO:
                    last_move = new Point2D.Float(coordinates[0], coordinates[1]);
                    endPoint = last_move;
                    break;
                case SEG_CLOSE:
                    // According to PathIterator docs:
                    // "The preceding sub-path should be closed by appending a line
                    // segment back to the point corresponding to the most recent
                    // SEG_MOVETO."
                    if (startPoint == null || endPoint == null) {
                        break;
                    }
                    line = getLineBetween(endPoint, last_move, pointComparator);
                    verifyLineIntersectsClipping(line);
                    break;
            }
            startPoint = endPoint;
            pathIterator.next();
        }
        currentPath.reset();
    }

    /**
     * Filter path drawing operations, only allowing operations related to line drawing
     * 过滤路径划线动作，只允许与直线绘制相关的动作
     * <p>
     * ⚠️Note: In practice (e.g., unit tests), there exist tables that are filled without stroking operations,
     * so both operations need to be considered together by default.
     * ⚠️注意：事实（如单元测试）存在只填充无描边动作的表格存在，因此默认两者都需要一起统计。
     * </p>
     *
     * @param isStroke Whether this is a stroke operation
     *                 是否为描边操作
     * @param isFill   Whether this is a fill operation
     *                 是否为填充操作
     * @return Return true if the path doesn't meet filtering criteria, otherwise return false
     *         如果路径不满足过滤条件则返回true，否则返回false
     */
    private boolean filterPathBySegmentType(boolean isStroke, boolean isFill) {
        PathIterator pathIterator = currentPath.getPathIterator(pageTransform);
        float[] coordinates = new float[6];
        int currentSegmentType = pathIterator.currentSegment(coordinates);

        // Check if the first drawing operation is a move-to command
        // 检查第一个划线动作是否为移动到某点命令
        if (currentSegmentType != SEG_MOVETO) {
            currentPath.reset();
            return true;
        }

        pathIterator.next();

        // Iterate through remaining drawing operations, check if they are all allowed line types
        // 遍历剩余的划线动作，检查是否都是允许的动作类型
        while (!pathIterator.isDone()) {
            currentSegmentType = pathIterator.currentSegment(coordinates);

            // Only allow drawing operations related to line drawing: line-to, close and move-to
            // 只允许直线绘制相关的划线动作：划到某点、闭合到移动点和移动到某点
            if (currentSegmentType != SEG_LINETO && currentSegmentType != SEG_CLOSE &&
                currentSegmentType != SEG_MOVETO) {

                // Does not meet requirements, return true to indicate filtering out this path
                // 不符合要求，返回true表示过滤掉此路径
                currentPath.reset();
                return true;

            }

            pathIterator.next();
        }

        // Meets requirements, return false to indicate keeping this path
        // 符合要求，返回false表示保留此路径
        return false;
    }

    private Point2D.Float getStartPoint(PathIterator pathIterator) {
        float[] startPointCoordinates = new float[6];
        pathIterator.currentSegment(startPointCoordinates);
        float x = Utils.round(startPointCoordinates[0], 2);
        float y = Utils.round(startPointCoordinates[1], 2);
        return new Point2D.Float(x, y);
    }

    private Line2D.Float getLineBetween(Point2D.Float pointA, Point2D.Float pointB, PointComparator pointComparator) {
        if (pointComparator.compare(pointA, pointB) == -1) {
            return new Line2D.Float(pointA, pointB);
        }
        return new Line2D.Float(pointB, pointA);
    }

    private void verifyLineIntersectsClipping(Line2D.Float line) {
        Rectangle2D currentClippingPath = currentClippingPath();
        if (line.intersects(currentClippingPath)) {
            Ruling ruling = new Ruling(line.getP1(), line.getP2()).intersect(currentClippingPath);
            if (ruling.length() > RULING_MINIMUM_LENGTH) {
                rulings.add(ruling);
            }
        }
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    public AffineTransform getPageTransform() {
        return pageTransform;
    }

    public Rectangle2D currentClippingPath() {
        Shape currentClippingPath = getGraphicsState().getCurrentClippingPath();
        Shape transformedClippingPath = getPageTransform().createTransformedShape(currentClippingPath);
        return transformedClippingPath.getBounds2D();
    }

    // TODO: repeated in SpreadsheetExtractionAlgorithm.
    class PointComparator implements Comparator<Point2D> {
        @Override
        public int compare(Point2D p1, Point2D p2) {
            float p1X = Utils.round(p1.getX(), 2);
            float p1Y = Utils.round(p1.getY(), 2);
            float p2X = Utils.round(p2.getX(), 2);
            float p2Y = Utils.round(p2.getY(), 2);

            if (p1Y > p2Y)
                return 1;
            if (p1Y < p2Y)
                return -1;
            if (p1X > p2X)
                return 1;
            if (p1X < p2X)
                return -1;
            return 0;
        }
    }

}
